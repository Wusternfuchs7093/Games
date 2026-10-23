// Decides what a settlement builds next and finds a free spot of land for it.

import { blueprint, variantCount } from "./architecture.js";
import { isNaturalGround, isWater } from "./blocks.js";
import { blueprintOf } from "./construction.js";
import { periodOf, PERIODS } from "./palette.js";
import { at, count, maxTier, radiusChunks, record, TIERS } from "./settlement.js";
import * as store from "./store.js";
import { bounds, ground, overlaps, toWorld } from "./terrain.js";

const MARGIN = 2;
const MAX_SLOPE = 6;
const MAX_ATTEMPTS = 160;
const MIN_MATERIALS_TO_START = 32;

/** Starts a new project if the settlement is idle and can afford one. */
export function plan(dim, s, day) {
    if (s.project || s.abandoned || s.adults === 0 || s.materials < MIN_MATERIALS_TO_START) {
        return;
    }
    const type = choose(s);
    if (type && start(dim, s, type, day)) {
        return;
    }
    startReconstruction(s, day);
}

/** What the town needs most. */
export function choose(s) {
    const pop = s.adults + s.children;
    const freeBeds = beds(s) - pop;
    if (freeBeds < 1) {
        return "house";
    }
    const next = TIERS[s.tier + 1];
    const canGrow = next && s.tier + 1 <= maxTier(s);
    if (canGrow) {
        for (const [type, wanted] of Object.entries(next.needs)) {
            if (count(s, type) < wanted) {
                return type;
            }
        }
    }
    if (freeBeds < 2) {
        return "house";
    }
    if (s.food < pop * 10 && count(s, "farm") < 1 + Math.floor(pop / 6)) {
        return "farm";
    }
    if (s.tier >= 1 && count(s, "warehouse") === 0) {
        return "warehouse";
    }
    if (count(s, "workshop") < 1 + Math.floor(pop / 5)) {
        return "workshop";
    }
    if (s.tier >= 2 && count(s, "library") === 0) {
        return "library";
    }
    if (s.tier >= 2 && count(s, "temple") === 0) {
        return "temple";
    }
    if (canGrow && beds(s) < next.beds + 2) {
        return "house";
    }
    const others = s.buildings.length - count(s, "lamp");
    if (count(s, "lamp") < others / 2) {
        return "lamp";
    }
    return null;
}

/** Beds of the village as it was found plus those of every house the town built. */
export function beds(s) {
    let total = s.bedsBase ?? 0;
    for (const b of s.buildings) {
        if (b.t === "house") {
            total += blueprintOf(s, b).blocks.filter(([, , , block]) => block.b === "bed" && !block.s?.head).length;
        }
    }
    return total;
}

/** Tries to start building {@code type}; false if no land was found. */
export function start(dim, s, type, day) {
    const variant = Math.floor(Math.random() * variantCount(type));
    const building = findSite(dim, s, type, variant) ?? (variant !== 0 ? findSite(dim, s, type, 0) : null);
    if (!building) {
        return false;
    }
    s.project = building;
    record(s, day, "construction", type);
    store.markDirty(s);
    return true;
}

/** Rebuilds the oldest building whose style belongs to an earlier age, once the materials are there. */
function startReconstruction(s, day) {
    const now = PERIODS.indexOf(periodOf(s.era));
    for (const b of s.buildings) {
        if (PERIODS.indexOf(periodOf(b.era)) >= now || b.t === "lamp" || b.t === "farm") {
            continue;
        }
        const rebuilt = { ...b, era: s.era };
        if (s.materials < blueprintOf(s, rebuilt).blocks.length) {
            return;
        }
        s.project = rebuilt;
        record(s, day, "upgrade", b.t);
        store.markDirty(s);
        return;
    }
}

// ------------------------------------------------------------------ site search

/** Walks rings around the center looking for fairly level natural ground inside the territory. */
export function findSite(dim, s, type, variant) {
    const bp = blueprint(type, s.culture, s.era, variant);
    const taken = s.buildings.map((b) => bounds(b, blueprintOf(s, b)));
    if (s.project) {
        taken.push(bounds(s.project, blueprintOf(s, s.project)));
    }
    const maxRadius = radiusChunks(s) * 16 - Math.max(bp.width, bp.depth) / 2 - MARGIN;
    let attempts = 0;
    for (let r = 8; r <= maxRadius; r += 3) {
        const steps = Math.max(8, Math.floor(2 * Math.PI * r / 5));
        const offset = Math.floor(Math.random() * steps);
        for (let i = 0; i < steps; i++) {
            if (++attempts > MAX_ATTEMPTS) {
                return null;
            }
            const angle = 2 * Math.PI * (i + offset) / steps;
            const x = s.x + Math.round(Math.cos(angle) * r);
            const z = s.z + Math.round(Math.sin(angle) * r);
            const turns = facing(x, z, s);
            const half = toWorld({ x: 0, y: 0, z: 0, r: turns }, Math.floor(bp.width / 2), 0, Math.floor(bp.depth / 2));
            const candidate = { t: type, x: x - half.x, y: s.y, z: z - half.z, r: turns, v: variant, era: s.era, m: false };
            const floor = floorLevel(dim, s, candidate, bp, taken);
            if (floor !== null) {
                candidate.y = floor;
                return candidate;
            }
        }
    }
    return null;
}

/** Quarter turns that point the front door (local south) at the town center. */
function facing(x, z, s) {
    const dx = s.x - x;
    const dz = s.z - z;
    if (Math.abs(dx) > Math.abs(dz)) {
        return dx > 0 ? 3 : 1; // east : west
    }
    return dz >= 0 ? 0 : 2; // south : north
}

function floorLevel(dim, s, candidate, bp, taken) {
    const box = bounds(candidate, bp);
    for (const other of taken) {
        if (overlaps(box, other, MARGIN)) {
            return null;
        }
    }
    // Quick look at the corners and the middle before checking every column.
    const probes = [[box.minX, box.minZ], [box.maxX, box.minZ], [box.minX, box.maxZ], [box.maxX, box.maxZ],
        [Math.floor((box.minX + box.maxX) / 2), Math.floor((box.minZ + box.maxZ) / 2)]];
    for (const [x, z] of probes) {
        const g = ground(dim, x, z, s.y);
        if (!g || !isNaturalGround(g.id) || Math.abs(g.y - s.y) > 12) {
            return null;
        }
    }
    let min = Infinity;
    let max = -Infinity;
    let sum = 0;
    let columns = 0;
    for (let x = box.minX - MARGIN; x <= box.maxX + MARGIN; x++) {
        for (let z = box.minZ - MARGIN; z <= box.maxZ + MARGIN; z++) {
            if (at(s.dim, x, z) !== s) {
                return null;
            }
            const g = ground(dim, x, z, s.y);
            if (!g) {
                return null;
            }
            const footprint = x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ;
            if (footprint) {
                if (!isNaturalGround(g.id)) {
                    return null;
                }
                min = Math.min(min, g.y);
                max = Math.max(max, g.y);
                sum += g.y;
                columns++;
                if (max - min > MAX_SLOPE) {
                    return null;
                }
            } else if (!isNaturalGround(g.id) && !isWater(g.id) && !g.id.includes("path") && !g.id.includes("farmland")) {
                return null;
            }
        }
    }
    return columns === 0 ? null : Math.round(sum / columns);
}
