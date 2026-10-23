// The settlement model: tiers, territory, names and the chronicle. Settlements are plain JSON objects.

import { ERAS } from "./palette.js";
import * as store from "./store.js";

export const TIERS = [
    { id: "hamlet", pop: 1, beds: 1, wealth: 0, happy: 0, radius: 2, needs: {} },
    { id: "village", pop: 5, beds: 5, wealth: 0, happy: 30, radius: 2, needs: {} },
    { id: "township", pop: 16, beds: 16, wealth: 256, happy: 45, radius: 3, needs: { warehouse: 1, well: 1 } },
    { id: "small_town", pop: 36, beds: 36, wealth: 1024, happy: 55, radius: 4, needs: { town_hall: 1, market: 1, library: 1 } },
    { id: "city", pop: 71, beds: 71, wealth: 4096, happy: 60, radius: 5, era: "iron",
        needs: { market: 2, watchtower: 2, temple: 1 } },
    { id: "metropolis", pop: 151, beds: 151, wealth: 16384, happy: 65, radius: 6, era: "medieval",
        needs: { market: 3, watchtower: 4, well: 2, temple: 1 } },
    { id: "capital", pop: 300, beds: 300, wealth: 65536, happy: 70, radius: 8, era: "renaissance",
        needs: { market: 4, watchtower: 6, warehouse: 2, temple: 1 } },
];

/** Research points needed to enter each era. */
export const ERA_POINTS = { founding: 0, iron: 120, medieval: 360, renaissance: 800, industrial: 1500, modern: 2500 };

export const MIN_DISTANCE = 128;
export const MAX_HISTORY = 30;

const NAMES = {
    plains: [["Rot", "Schön", "Linden", "Eichen", "Wald", "Stein", "Hohen", "Neu", "Alt", "Kirch", "Mühl", "Rosen"],
        ["burg", "dorf", "heim", "feld", "bach", "hausen", "stadt", "au", "furt", "berg", "tal"]],
    desert: [["Al-", "Dar ", "Qasr ", "Wadi ", "Bir ", "Ain ", "Ras ", "Bab "],
        ["Safa", "Rumah", "Nahr", "Sahra", "Zahir", "Qamar", "Najm", "Tamr", "Layl", "Bahr", "Hilal"]],
    savanna: [["Tom", "Ga", "Ka", "Djen", "Mo", "Ti", "Ba", "Se", "Zan", "Ki", "Ma", "Su"],
        ["buktu", "o", "ne", "bara", "ssi", "mbu", "ndo", "lwa", "nga", "rare", "lima"]],
    snowy: [["Bjorn", "Fjell", "Is", "Frost", "Ulf", "Sval", "Hvit", "Kald", "Rav", "Stor", "Nord", "Ask"],
        ["heim", "vik", "fjord", "gard", "by", "dal", "holm", "stad", "berg", "nes"]],
    taiga: [["Belo", "Novo", "Zlato", "Kras", "Yaro", "Vla", "Sveto", "Bogo", "Vysh", "Lesno", "Rosto"],
        ["grad", "gorod", "sk", "vo", "slavl", "pol", "mir", "dar", "zersk", "gorsk"]],
    other: [["Ash", "Brad", "Chester", "Dun", "Elm", "Glou", "Hart", "Kings", "Lan", "Mel", "Ox", "Wor"],
        ["ford", "bury", "ton", "wich", "ley", "field", "by", "mouth", "bridge", "wick"]],
    cherry: [["Saku", "Kawa", "Yama", "Ishi", "Matsu", "Taka", "Kiyo", "Fuji", "Mori", "Shira", "Haru", "Aki"],
        ["mura", "machi", "saki", "hara", "no", "shima", "zawa", "oka", "ta", "yama", "mizu"]],
    jungle: [["Ix", "Chi", "Uxa", "Tik", "Pa", "Co", "Ka", "Yax", "Bal", "Tu", "Na", "Zac"],
        ["chen", "lenque", "pan", "mal", "kal", "ba", "tun", "ha", "lum", "nal"]],
};

const pick = (list) => list[Math.floor(Math.random() * list.length)];

export function generateName(culture) {
    const [starts, ends] = NAMES[culture] ?? NAMES.other;
    const taken = new Set([...store.all().values()].map((s) => s.name.toLowerCase()));
    for (let i = 0; i < 32; i++) {
        const name = pick(starts) + pick(ends);
        if (!taken.has(name.toLowerCase())) {
            return name;
        }
    }
    return pick(starts) + pick(ends) + " " + (taken.size + 1);
}

export function tierOf(s) {
    return TIERS[s.tier];
}

/** Territory radius in chunks: what the tier grants, but never less than the village it grew from. */
export function radiusChunks(s) {
    return Math.max(TIERS[s.tier].radius, s.core ?? 0);
}

export function chunkOf(v) {
    return Math.floor(v / 16);
}

function owns(s, x, z) {
    const dx = chunkOf(x) - chunkOf(s.x);
    const dz = chunkOf(z) - chunkOf(s.z);
    const r = radiusChunks(s);
    return dx * dx + dz * dz <= r * r + r;
}

/** The settlement whose territory contains (x, z), the nearest one if two claim it. */
export function at(dimension, x, z) {
    let best;
    let bestDist = Infinity;
    for (const s of store.all().values()) {
        if (s.dim !== dimension || !owns(s, x, z)) {
            continue;
        }
        const dist = (s.x - x) ** 2 + (s.z - z) ** 2;
        if (dist < bestDist) {
            best = s;
            bestDist = dist;
        }
    }
    return best;
}

export function nearest(dimension, x, z, maxDistance) {
    let best;
    let bestDist = maxDistance * maxDistance;
    for (const s of store.all().values()) {
        const dist = (s.x - x) ** 2 + (s.z - z) ** 2;
        if (s.dim === dimension && dist <= bestDist) {
            best = s;
            bestDist = dist;
        }
    }
    return best;
}

export function create(dimension, center, culture, day, name) {
    const s = {
        id: Date.now().toString(36) + Math.floor(Math.random() * 1e6).toString(36),
        name: name ?? generateName(culture),
        dim: dimension,
        x: Math.floor(center.x), y: Math.floor(center.y), z: Math.floor(center.z),
        culture, tier: 0, founded: day, potential: 30 + Math.floor(Math.random() * 50),
        food: 80, materials: 400, wealth: 0, happiness: 50,
        adults: 0, children: 0, bedsBase: 0, core: 0,
        research: 0, era: "founding",
        buildings: [], project: null, contracts: [], nextContract: 1,
        history: [], rep: {}, decline: 0, empty: 0, abandoned: false,
    };
    record(s, day, "founded", s.name);
    store.add(s);
    return s;
}

/** Highest tier this settlement's land allows. */
export function maxTier(s) {
    if (s.potential >= 85) return 6;
    if (s.potential >= 70) return 5;
    if (s.potential >= 55) return 4;
    if (s.potential >= 40) return 3;
    return 2;
}

export function count(s, type) {
    return s.buildings.filter((b) => b.t === type).length;
}

export function eraIndex(era) {
    return ERAS.indexOf(era);
}

export function record(s, day, key, arg = "") {
    s.history.push({ d: day, k: key, a: String(arg) });
    while (s.history.length > MAX_HISTORY) {
        s.history.shift();
    }
}

export function changeReputation(s, playerId, delta) {
    s.rep[playerId] = Math.max(-100, Math.min(100, (s.rep[playerId] ?? 0) + delta));
}
