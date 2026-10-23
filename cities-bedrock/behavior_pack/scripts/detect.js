// Finding villages: wherever a player meets a few villagers far from any known town, a settlement is founded
// around the village bell (or the villagers' gathering place). Its culture comes from what the village is
// built of: sandstone means Arabs, spruce and snow Norse, acacia the Sahel and so on.

import { world } from "@minecraft/server";
import { census, day } from "./census.js";
import { t } from "./i18n.js";
import { create, MIN_DISTANCE, nearest } from "./settlement.js";
import { ground, typeAt } from "./terrain.js";

const MIN_VILLAGERS = 3;
const SEARCH = 64;

/** Looks around one player for an unregistered village. */
export function detectAround(player) {
    const dim = player.dimension;
    if (dim.id !== "minecraft:overworld") {
        return;
    }
    const villagers = dim.getEntities({ type: "minecraft:villager_v2", location: player.location, maxDistance: SEARCH });
    if (villagers.length < MIN_VILLAGERS) {
        return;
    }
    const cx = villagers.reduce((sum, v) => sum + v.location.x, 0) / villagers.length;
    const cy = villagers.reduce((sum, v) => sum + v.location.y, 0) / villagers.length;
    const cz = villagers.reduce((sum, v) => sum + v.location.z, 0) / villagers.length;
    if (nearest("overworld", cx, cz, MIN_DISTANCE)) {
        return;
    }
    const center = findBell(dim, Math.floor(cx), Math.floor(cy), Math.floor(cz)) ?? groundAt(dim, cx, cy, cz);
    if (!center) {
        return;
    }
    found(dim, center, villagers);
}

/** Registers a settlement at {@code center}; {@code villagers} sets how far the old village reaches. */
export function found(dim, center, villagers, name) {
    const culture = cultureAround(dim, center);
    const s = create("overworld", center, culture, day(), name);
    let farthest = 0;
    for (const v of villagers) {
        farthest = Math.max(farthest, Math.hypot(v.location.x - center.x, v.location.z - center.z));
    }
    s.core = Math.min(6, Math.ceil((farthest + 16) / 16));
    s.bedsBase = villagers.length;
    world.sendMessage(t("cities.announce.founded", s.name, t("cities.culture." + culture)));
    census(s);
    return s;
}

function groundAt(dim, x, y, z) {
    const g = ground(dim, Math.floor(x), Math.floor(z), Math.floor(y));
    return g ? { x: Math.floor(x), y: g.y + 1, z: Math.floor(z) } : null;
}

/** A bell within a few blocks of where the villagers gather. */
function findBell(dim, cx, cy, cz) {
    for (let r = 0; r <= 16; r++) {
        for (let dx = -r; dx <= r; dx++) {
            for (let dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) !== r) {
                    continue;
                }
                for (let dy = -6; dy <= 6; dy++) {
                    if (typeAt(dim, cx + dx, cy + dy, cz + dz) === "minecraft:bell") {
                        return { x: cx + dx, y: cy + dy, z: cz + dz };
                    }
                }
            }
        }
    }
    return null;
}

const SIGNS = [
    ["desert", ["sandstone", "sand"]],
    ["savanna", ["acacia", "red_sand", "terracotta", "hardened_clay"]],
    ["snowy", ["snow", "ice"]],
    ["taiga", ["spruce", "podzol"]],
    ["cherry", ["cherry", "pink_petals"]],
    ["jungle", ["jungle", "bamboo", "cocoa"]],
    ["plains", ["oak", "birch", "grass", "dandelion", "poppy"]],
];

/** Guesses the culture from the blocks around the center. */
export function cultureAround(dim, center) {
    const score = {};
    for (let i = 0; i < 240; i++) {
        const x = Math.floor(center.x + (Math.random() - 0.5) * 48);
        const y = Math.floor(center.y + (Math.random() - 0.5) * 14);
        const z = Math.floor(center.z + (Math.random() - 0.5) * 48);
        const id = typeAt(dim, x, y, z);
        if (!id || id === "minecraft:air") {
            continue;
        }
        for (const [culture, words] of SIGNS) {
            if (words.some((word) => id.includes(word))) {
                score[culture] = (score[culture] ?? 0) + (culture === "plains" ? 1 : 2);
                break;
            }
        }
    }
    // Snowy villages are built of spruce too: snow decides.
    if ((score.snowy ?? 0) >= 6 && (score.taiga ?? 0) > 0) {
        score.snowy += score.taiga;
    }
    let best = "other";
    let bestScore = 4;
    for (const [culture, value] of Object.entries(score)) {
        if (value > bestScore) {
            best = culture;
            bestScore = value;
        }
    }
    return best;
}
