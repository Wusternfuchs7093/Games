// The city book: tapping it opens the town's menu — overview, donations, the job board, buildings, chronicle.

import { world } from "@minecraft/server";
import { ActionFormData, MessageFormData } from "@minecraft/server-ui";
import { BUILDING_TYPES } from "./architecture.js";
import { addStock, census, changeTier, day, moodFactors, requirements } from "./census.js";
import { forget, progress } from "./construction.js";
import { found } from "./detect.js";
import { lines, t, text } from "./i18n.js";
import { ERAS } from "./palette.js";
import { beds, start } from "./planner.js";
import * as quests from "./quests.js";
import { at, changeReputation, count, ERA_POINTS, MIN_DISTANCE, nearest, radiusChunks, record, TIERS } from "./settlement.js";
import * as store from "./store.js";
import { ground } from "./terrain.js";

const NEARBY = 160;

function here(player) {
    const { x, z } = player.location;
    return at("overworld", x, z) ?? nearest("overworld", x, z, NEARBY);
}

function isAdmin(player) {
    return player.hasTag("cities_admin") || world.getAllPlayers().length === 1;
}

export async function openMenu(player) {
    if (player.dimension.id !== "minecraft:overworld") {
        player.sendMessage(t("cities.not_in_settlement"));
        return;
    }
    const s = here(player);
    if (!s) {
        return noTownMenu(player);
    }
    const form = new ActionFormData().title(s.name).body(overview(s));
    const actions = [];
    const add = (key, action) => {
        form.button(t(key));
        actions.push(action);
    };
    add("cities.menu.donate", () => donate(player, s, false));
    add("cities.menu.donate_all", () => donate(player, s, true));
    add("cities.menu.board", () => board(player, s));
    add("cities.menu.mood", () => mood(player, s));
    add("cities.menu.buildings", () => buildings(player, s));
    add("cities.menu.chronicle", () => chronicle(player, s));
    if (isAdmin(player)) {
        add("cities.menu.admin", () => admin(player, s));
    }
    const response = await form.show(player);
    if (!response.canceled && actions[response.selection] !== undefined) {
        await actions[response.selection]();
    }
}

function overview(s) {
    const pop = s.adults + s.children;
    const tier = TIERS[s.tier];
    const parts = [
        t("cities.info.tier", t("cities.tier." + tier.id), t("cities.culture." + s.culture)),
        t("cities.info.population", pop, s.adults, s.children, beds(s)),
        t("cities.info.economy", s.food, s.materials, s.wealth, s.happiness),
        t("cities.info.era", t("cities.era." + s.era), s.research, ERA_POINTS[ERAS[ERAS.indexOf(s.era) + 1]] ?? "-"),
    ];
    if (s.project) {
        parts.push(t(s.stalled ? "cities.info.project_stalled" : "cities.info.project",
            t("cities.building." + s.project.t), progress(s)));
    }
    if (s.abandoned) {
        parts.push(t("cities.info.abandoned"));
    }
    const next = TIERS[s.tier + 1];
    if (next) {
        parts.push(t("cities.info.next", t("cities.tier." + next.id)));
        for (const [key, have, need] of requirements(s, next)) {
            const mark = have >= need ? "§a✔ " : "§c✘ ";
            parts.push(lines(text(mark), t(key.includes(".") ? "cities." + key : "cities.requirement." + key), text("§r: " + have + " / " + need)));
        }
    } else {
        parts.push(t("cities.info.max_tier"));
    }
    return lines(...parts);
}

async function noTownMenu(player) {
    const form = new MessageFormData().title(t("cities.menu.title")).body(t("cities.menu.no_town"))
        .button1(t("cities.menu.found")).button2(t("cities.menu.cancel"));
    const response = await form.show(player);
    if (response.canceled || response.selection !== 0) {
        return;
    }
    const { x, y, z } = player.location;
    if (nearest("overworld", x, z, MIN_DISTANCE)) {
        player.sendMessage(t("cities.menu.too_close"));
        return;
    }
    const g = ground(player.dimension, Math.floor(x), Math.floor(z), Math.floor(y));
    const center = { x: Math.floor(x), y: g ? g.y + 1 : Math.floor(y), z: Math.floor(z) };
    const villagers = player.dimension.getEntities({ type: "minecraft:villager_v2", location: player.location, maxDistance: 48 });
    const s = found(player.dimension, center, villagers);
    s.mayor = player.name;
    store.markDirty(s);
}

// ------------------------------------------------------------------ actions

function donate(player, s, all) {
    const inventory = player.getComponent("minecraft:inventory")?.container;
    const slot = player.selectedSlotIndex ?? player.selectedSlot ?? 0;
    const held = inventory?.getItem(slot);
    if (!held) {
        player.sendMessage(t("cities.donate.empty_hand"));
        return;
    }
    let amount = held.amount;
    if (all) {
        amount = quests.take(player, held.typeId, 100000);
    } else {
        inventory.setItem(slot);
    }
    const value = addStock(s, held.typeId, amount);
    changeReputation(s, player.id, Math.max(1, Math.floor(value.points / 16)));
    record(s, day(), "donation", player.name);
    store.markDirty(s);
    player.sendMessage(t("cities.donate.done", amount, held.typeId.replace("minecraft:", ""), s.name, value.points,
        t("cities.stock." + value.stock)));
}

async function board(player, s) {
    const form = new ActionFormData().title(t("cities.board.title", s.name));
    const contracts = [...s.contracts];
    if (contracts.length === 0) {
        form.body(t("cities.board.empty"));
    } else {
        form.body(t("cities.board.hint"));
    }
    for (const c of contracts) {
        const name = c.item.replace("minecraft:", "");
        form.button(t("cities.board.entry", c.count - c.progress, name, c.reward, quests.carried(player, c.item)));
    }
    form.button(t("cities.menu.back"));
    const response = await form.show(player);
    if (response.canceled) {
        return;
    }
    const contract = contracts[response.selection];
    if (!contract) {
        return openMenu(player);
    }
    player.sendMessage(quests.deliver(s, player, contract));
    return board(player, s);
}

async function mood(player, s) {
    const parts = [t("cities.mood.header", s.happiness)];
    for (const [key, value] of moodFactors(s)) {
        parts.push(lines(text((value > 0 ? "§a+" : "§c") + value + " §7"), t("cities.mood." + key)));
    }
    await info(player, s.name, lines(...parts));
}

async function buildings(player, s) {
    const parts = [t("cities.buildings.header", s.buildings.length, radiusChunks(s))];
    for (const type of BUILDING_TYPES) {
        const n = count(s, type);
        if (n > 0) {
            parts.push(lines(t("cities.building." + type), text(": " + n)));
        }
    }
    await info(player, s.name, lines(...parts));
}

async function chronicle(player, s) {
    const parts = s.history.slice(-15).map((h) => {
        const arg = ["built", "construction", "upgrade"].includes(h.k) ? t("cities.building." + h.a)
            : ["promoted", "demoted"].includes(h.k) ? t("cities.tier." + h.a)
                : h.k === "era" ? t("cities.era." + h.a) : text(h.a);
        return t("cities.history.line", h.d, t("cities.history." + h.k, arg));
    });
    await info(player, s.name, lines(...parts));
}

async function info(player, title, body) {
    const form = new ActionFormData().title(title).body(body).button(t("cities.menu.back"));
    const response = await form.show(player);
    if (!response.canceled && response.selection === 0) {
        await openMenu(player);
    }
}

// ------------------------------------------------------------------ testing

async function admin(player, s) {
    const form = new ActionFormData().title(t("cities.menu.admin")).body(t("cities.admin.body"));
    const actions = [];
    const add = (message, action) => {
        form.button(message);
        actions.push(action);
    };
    add(t("cities.admin.census"), () => census(s));
    add(t("cities.admin.materials"), () => {
        s.materials += 1000;
        s.food += 500;
        s.wealth += 500;
    });
    add(t("cities.admin.tier_up"), () => TIERS[s.tier + 1] && changeTier(player.dimension, s, s.tier + 1));
    add(t("cities.admin.era_next"), () => {
        const next = ERAS[ERAS.indexOf(s.era) + 1];
        if (next) {
            s.era = next;
            s.research = Math.max(s.research, ERA_POINTS[next]);
        }
    });
    for (const type of BUILDING_TYPES) {
        add(lines(t("cities.admin.build"), t("cities.building." + type)), () => {
            s.project = null;
            forget(s);
            if (!start(player.dimension, s, type, day())) {
                player.sendMessage(t("cities.admin.no_site"));
            }
        });
    }
    add(t("cities.admin.remove"), () => {
        store.remove(s);
        player.sendMessage(t("cities.admin.removed", s.name));
    });
    const response = await form.show(player);
    if (!response.canceled && actions[response.selection]) {
        actions[response.selection]();
        store.markDirty(s);
        store.flush();
    }
}
