// The census: once a minute every loaded settlement counts its people, works, eats, grows or declines.

import { ItemStack, world } from "@minecraft/server";
import { blueprintOf } from "./construction.js";
import { t } from "./i18n.js";
import { ERAS } from "./palette.js";
import { beds, plan } from "./planner.js";
import * as quests from "./quests.js";
import { count, ERA_POINTS, eraIndex, maxTier, radiusChunks, record, TIERS } from "./settlement.js";
import * as store from "./store.js";
import { getBlock, toWorld } from "./terrain.js";

const DEMOTE_AFTER = 3;
const ABANDON_AFTER = 10;

export function day() {
    return Math.floor(world.getAbsoluteTime() / 24000);
}

/** Runs one census for {@code s}; does nothing if the town's center is not loaded. */
export function census(s) {
    const dim = world.getDimension(s.dim);
    if (!getBlock(dim, s.x, s.y, s.z)) {
        return;
    }
    s.day = day();
    countPeople(dim, s);
    collectStorage(dim, s);
    economy(s);
    happiness(s);
    migrate(dim, s);
    research(dim, s);
    updateTier(dim, s);
    quests.update(s);
    plan(dim, s, s.day);
    store.markDirty(s);
}

function villagersOf(dim, s) {
    const radius = radiusChunks(s) * 16 + 16;
    return dim.getEntities({ type: "minecraft:villager_v2", location: { x: s.x, y: s.y, z: s.z }, maxDistance: radius });
}

function countPeople(dim, s) {
    let adults = 0;
    let children = 0;
    for (const villager of villagersOf(dim, s)) {
        if (villager.getComponent("minecraft:is_baby")) {
            children++;
        } else {
            adults++;
        }
    }
    s.adults = adults;
    s.children = children;
}

/** Empties the warehouse chests into the town's stocks. */
function collectStorage(dim, s) {
    for (const b of s.buildings) {
        if (b.t !== "warehouse") {
            continue;
        }
        const bp = blueprintOf(s, b);
        for (const [lx, ly, lz] of bp.storage) {
            const pos = toWorld(b, lx, ly, lz);
            const container = getBlock(dim, pos.x, pos.y, pos.z)?.getComponent("minecraft:inventory")?.container;
            if (!container) {
                continue;
            }
            for (let slot = 0; slot < container.size; slot++) {
                const item = container.getItem(slot);
                if (item) {
                    addStock(s, item.typeId, item.amount);
                    container.setItem(slot);
                }
            }
        }
    }
}

const FOOD = { bread: 5, baked_potato: 5, potato: 1, carrot: 3, beetroot: 1, apple: 4, cooked_beef: 8, cooked_porkchop: 8,
    cooked_chicken: 6, cooked_mutton: 6, cooked_cod: 5, cooked_salmon: 6, beef: 3, porkchop: 3, chicken: 2, mutton: 2,
    cod: 2, salmon: 2, wheat: 2, melon_slice: 2, pumpkin_pie: 8, cookie: 2, cake: 14, sweet_berries: 2, golden_carrot: 6,
    mushroom_stew: 6, rabbit_stew: 10, beetroot_soup: 6, hay_block: 18, dried_kelp: 1 };
const WEALTH = { emerald: 9, emerald_block: 81, diamond: 20, gold_ingot: 6, gold_nugget: 1, iron_ingot: 3, lapis_lazuli: 2,
    amethyst_shard: 2, book: 3, paper: 1 };

/** Classifies an item: food feeds, valuables enrich, everything else becomes building materials. */
export function stockValue(typeId, amount) {
    const name = typeId.replace("minecraft:", "");
    if (FOOD[name] !== undefined) {
        return { stock: "food", points: FOOD[name] * amount };
    }
    if (WEALTH[name] !== undefined) {
        return { stock: "wealth", points: WEALTH[name] * amount };
    }
    const perItem = name.endsWith("_log") || name.endsWith("_wood") || name === "log" || name === "log2" ? 4 : 1;
    return { stock: "materials", points: perItem * amount };
}

export function addStock(s, typeId, amount) {
    const value = stockValue(typeId, amount);
    s[value.stock] = (s[value.stock] ?? 0) + value.points;
    return value;
}

function economy(s) {
    const pop = s.adults + s.children;
    const produced = count(s, "farm") * 18 + s.adults * 2 + (s.culture === "plains" ? 4 : 0);
    const eaten = pop * (s.culture === "snowy" ? 2 : 3);
    s.food = Math.max(0, s.food + produced - eaten);
    s.materials += 20 + s.adults * 5 + (s.culture === "taiga" ? s.adults * 2 : 0);
    s.wealth += s.adults + count(s, "market") * 6 + count(s, "workshop") * 2;
}

/** Named reasons for the town's mood; happiness moves 40% of the way to their sum each census. */
export function moodFactors(s) {
    const pop = s.adults + s.children;
    const free = beds(s) - pop;
    const factors = [];
    const add = (key, value) => value !== 0 && factors.push([key, value]);
    add("housing", free >= 0 ? Math.min(15, free * 3) : Math.max(-30, free * 5));
    add(s.food <= 0 ? "hunger" : "plenty", s.food <= 0 ? -25 : s.food >= pop * 20 ? 10 : 0);
    add("well", count(s, "well") > 0 ? 5 : 0);
    add("markets", Math.min(10, count(s, "market") * 5));
    add("temple", count(s, "temple") > 0 ? 5 : 0);
    add("town_hall", count(s, "town_hall") > 0 ? 3 : 0);
    add("watchtowers", Math.min(9, count(s, "watchtower") * 3));
    add("work", s.adults > 0 ? Math.min(10, count(s, "workshop") * 4) - 5 : 0);
    return factors;
}

function happiness(s) {
    const target = Math.max(0, Math.min(100, 50 + moodFactors(s).reduce((sum, [, v]) => sum + v, 0)));
    s.happiness = Math.round(s.happiness * 0.6 + target * 0.4);
}

/** Prosperous towns attract newcomers. */
function migrate(dim, s) {
    const pop = s.adults + s.children;
    const prosperous = s.happiness >= 60 && beds(s) > pop && s.food >= pop * 5;
    if (!prosperous || Math.random() > 0.3) {
        return;
    }
    try {
        const near = { x: s.x + Math.floor(Math.random() * 7) - 3, y: s.y + 1, z: s.z + Math.floor(Math.random() * 7) - 3 };
        dim.spawnEntity("minecraft:villager_v2", near);
        record(s, s.day, "immigrant");
    } catch (error) {
        console.warn("[cities] immigrant: " + error);
    }
}

function research(dim, s) {
    const points = 1 + count(s, "library") * 4 + (count(s, "town_hall") > 0 ? 2 : 0) + Math.floor((s.adults + s.children) / 10)
        + (s.culture === "other" ? 1 : 0);
    s.research += points;
    const next = ERAS[eraIndex(s.era) + 1];
    if (next && s.research >= ERA_POINTS[next]) {
        s.era = next;
        record(s, s.day, "era", next);
        world.sendMessage(t("cities.research.era", s.name, t("cities.era." + next)));
        for (const player of dim.getPlayers({ location: { x: s.x, y: s.y, z: s.z }, maxDistance: radiusChunks(s) * 16 })) {
            player.onScreenDisplay.setTitle(t("cities.era." + next), { subtitle: s.name, fadeInDuration: 10, stayDuration: 60, fadeOutDuration: 20 });
        }
    }
}

/** Requirements for the next tier as [key, have, need] triples. */
export function requirements(s, tier) {
    const pop = s.adults + s.children;
    const list = [["population", pop, tier.pop], ["beds", beds(s), tier.beds], ["wealth", s.wealth, tier.wealth],
        ["happiness", s.happiness, tier.happy], ["food", s.food, 1]];
    for (const [type, wanted] of Object.entries(tier.needs)) {
        list.push(["building." + type, count(s, type), wanted]);
    }
    if (tier.era) {
        list.push(["era." + tier.era, eraIndex(s.era) >= eraIndex(tier.era) ? 1 : 0, 1]);
    }
    return list;
}

function canReach(s, index) {
    return index <= maxTier(s) && requirements(s, TIERS[index]).every(([, have, need]) => have >= need);
}

function updateTier(dim, s) {
    const pop = s.adults + s.children;
    if (pop === 0) {
        s.empty = (s.empty ?? 0) + 1;
        if (!s.abandoned && s.empty >= ABANDON_AFTER) {
            s.abandoned = true;
            record(s, s.day, "abandoned");
            world.sendMessage(t("cities.announce.abandoned", s.name));
        }
        return;
    }
    s.empty = 0;
    if (s.abandoned) {
        s.abandoned = false;
        record(s, s.day, "resettled");
    }
    if (TIERS[s.tier + 1] && canReach(s, s.tier + 1)) {
        changeTier(dim, s, s.tier + 1);
        return;
    }
    if (s.tier > 0 && pop < TIERS[s.tier].pop / 2) {
        s.decline = (s.decline ?? 0) + 1;
        if (s.decline >= DEMOTE_AFTER) {
            changeTier(dim, s, s.tier - 1);
        }
    } else {
        s.decline = 0;
    }
}

export function changeTier(dim, s, tier) {
    const promotion = tier > s.tier;
    s.tier = tier;
    s.decline = 0;
    const name = t("cities.tier." + TIERS[tier].id);
    record(s, s.day ?? day(), promotion ? "promoted" : "demoted", TIERS[tier].id);
    world.sendMessage(t(promotion ? "cities.announce.promoted" : "cities.announce.demoted", s.name, name));
    if (promotion) {
        for (const player of dim.getPlayers({ location: { x: s.x, y: s.y, z: s.z }, maxDistance: radiusChunks(s) * 16 })) {
            player.onScreenDisplay.setTitle(s.name, { subtitle: name, fadeInDuration: 10, stayDuration: 60, fadeOutDuration: 20 });
            try {
                player.playSound("random.levelup");
            } catch {
                // sound is optional
            }
        }
    }
    store.markDirty(s);
}

/** Pays a player in emeralds from the treasury, as far as it goes. */
export function pay(s, player, emeralds) {
    const paid = Math.min(emeralds, Math.floor(s.wealth / 9));
    if (paid > 0) {
        s.wealth -= paid * 9;
        const inventory = player.getComponent("minecraft:inventory")?.container;
        const rest = inventory?.addItem(new ItemStack("minecraft:emerald", paid));
        if (rest) {
            player.dimension.spawnItem(rest, player.location);
        }
    }
    return paid;
}
