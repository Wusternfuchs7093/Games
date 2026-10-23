// Turns a planned building into real blocks, a few at a time. The site is cleared and levelled first, then the
// foundation, the walls and roof, the furniture and finally a path to the town center.
// The list of steps lives only in memory; after a reload it is worked out again from the world, skipping
// whatever is already in place, so finished parts are never torn down.

import { blueprint } from "./architecture.js";
import { isAir, isNaturalGround, isVegetation, isWater, permutation, rotateBlock } from "./blocks.js";
import { t } from "./i18n.js";
import { record } from "./settlement.js";
import * as store from "./store.js";
import { bounds, columnTop, getBlock, ground, toWorld } from "./terrain.js";

const MAX_FOUNDATION = 10;
const MARGIN = 2;
const EARTHWORK_BATCH = 6;
const MAX_ROAD = 80;
const DETAILS = ["lantern", "ladder", "door", "bed", "carpet", "trapdoor", "wheat", "carrots", "potatoes", "beetroot",
    "lightning_rod", "chain", "campfire", "bell", "pressure_plate", "brewing_stand"];

/** In-memory build queues: settlement id → {steps, next, total}. */
const queues = new Map();

export function forget(s) {
    queues.delete(s.id);
}

export function blueprintOf(s, b) {
    return blueprint(b.t, s.culture, b.era, b.v, b.m);
}

/** Share of the current project done, 0..100. */
export function progress(s) {
    const q = queues.get(s.id);
    if (!q || q.total === 0) {
        return 0;
    }
    return Math.floor(100 * q.next / q.total);
}

/** Works on the settlement's project: places up to {@code count} steps. */
export function work(dim, s, steps) {
    const b = s.project;
    if (!b) {
        return;
    }
    const bp = blueprintOf(s, b);
    let q = queues.get(s.id);
    if (!q) {
        const list = plan(dim, s, b, bp);
        q = { steps: list, next: 0, total: list.length };
        queues.set(s.id, q);
    }
    let checked = 0;
    for (let done = 0; done < steps && q.next < q.steps.length; ) {
        const group = q.steps[q.next];
        if (isDone(dim, group)) {
            q.next++;
            if (++checked > 256) {
                return; // a long run of finished work: carry on next time
            }
            continue;
        }
        const cost = group.filter((p) => p.cost).length;
        if (s.materials < cost) {
            s.stalled = true;
            return;
        }
        if (!place(dim, group)) {
            return; // not loaded right now
        }
        s.stalled = false;
        s.materials -= cost;
        q.next++;
        done++;
        effects(dim, group[0]);
    }
    store.markDirty(s);
    if (q.next >= q.steps.length) {
        complete(dim, s);
    }
}

function isDone(dim, group) {
    for (const p of group) {
        const block = getBlock(dim, p.x, p.y, p.z);
        if (!block) {
            return false;
        }
        if (p.perm ? block.typeId !== p.perm.type.id : !isAir(block.typeId)) {
            return false;
        }
    }
    return true;
}

function place(dim, group) {
    for (const p of group) {
        const block = getBlock(dim, p.x, p.y, p.z);
        if (!block) {
            return false;
        }
        try {
            block.setPermutation(p.perm ?? permutation({ b: "air" }));
        } catch (error) {
            console.warn("[cities] could not place block: " + error);
        }
    }
    return true;
}

function effects(dim, p) {
    if (Math.random() < 0.25) {
        try {
            dim.spawnParticle("minecraft:villager_happy", { x: p.x + 0.5, y: p.y + 1, z: p.z + 0.5 });
        } catch {
            // particles are optional
        }
    }
}

function complete(dim, s) {
    const b = s.project;
    s.buildings = s.buildings.filter((other) => !(other.t === b.t && other.x === b.x && other.z === b.z));
    s.buildings.push(b);
    s.project = null;
    s.stalled = false;
    queues.delete(s.id);
    record(s, s.day ?? 0, "built", b.t);
    store.markDirty(s);
    for (const player of dim.getPlayers({ location: { x: s.x, y: s.y, z: s.z }, maxDistance: 160 })) {
        player.sendMessage(t("cities.construction.finished", s.name, t("cities.building." + b.t)));
    }
}

// ------------------------------------------------------------------ planning

/** Lists every block change, in groups that are placed together (a door and its top, a bed and its head). */
export function plan(dim, s, b, bp) {
    const clearing = [];
    const filling = [];
    const foundation = [];
    const structure = [];
    const details = [];
    const desert = s.culture === "desert";
    const surface = permutation({ b: desert ? "sand" : "grass_block" });
    const soil = permutation({ b: desert ? "sandstone" : "dirt" });
    const footing = permutation({ b: bp.palette.foundation });
    const occupied = new Set(bp.blocks.map(([x, y, z]) => x + "," + y + "," + z));

    // The building's own columns.
    for (let lx = 0; lx < bp.width; lx++) {
        for (let lz = 0; lz < bp.depth; lz++) {
            const floor = toWorld(b, lx, 0, lz);
            const top = columnTop(dim, floor.x, floor.z, b.y + bp.height);
            if (top === undefined) {
                continue;
            }
            for (let y = Math.max(top, b.y + 1); y > b.y; y--) {
                if (occupied.has(lx + "," + (y - b.y) + "," + lz)) {
                    continue; // the structure replaces whatever is there
                }
                const id = getBlock(dim, floor.x, y, floor.z)?.typeId;
                if (id && !isAir(id)) {
                    clearing.push({ x: floor.x, y, z: floor.z });
                }
            }
            const groundCell = !occupied.has(lx + ",0," + lz);
            const here = getBlock(dim, floor.x, b.y, floor.z)?.typeId;
            if (groundCell && here && !isSolid(here)) {
                filling.push({ x: floor.x, y: b.y, z: floor.z, perm: surface });
            }
            for (let y = b.y - 1; y > b.y - MAX_FOUNDATION; y--) {
                const id = getBlock(dim, floor.x, y, floor.z)?.typeId;
                if (!id || isSolid(id)) {
                    break;
                }
                foundation.push([{ x: floor.x, y, z: floor.z, perm: groundCell ? soil : footing, cost: !groundCell }]);
            }
        }
    }

    // A strip of levelled ground around it.
    const box = bounds(b, bp);
    for (let x = box.minX - MARGIN; x <= box.maxX + MARGIN; x++) {
        for (let z = box.minZ - MARGIN; z <= box.maxZ + MARGIN; z++) {
            const distance = Math.max(box.minX - x, x - box.maxX, box.minZ - z, z - box.maxZ);
            if (distance <= 0) {
                continue;
            }
            const natural = ground(dim, x, z, b.y);
            if (!natural) {
                continue;
            }
            const target = distance === 1 ? b.y : Math.floor((b.y + natural.y) / 2);
            const top = columnTop(dim, x, z, b.y) ?? natural.y;
            for (let y = top; y > target; y--) {
                const id = getBlock(dim, x, y, z)?.typeId;
                if (id && !isAir(id) && (y > natural.y || isNaturalGround(id) || isVegetation(id))) {
                    clearing.push({ x, y, z });
                }
            }
            for (let y = target; y > target - MAX_FOUNDATION; y--) {
                const id = getBlock(dim, x, y, z)?.typeId;
                if (!id || isSolid(id)) {
                    break;
                }
                filling.push({ x, y, z, perm: y === target ? surface : soil });
            }
        }
    }
    clearing.sort((a, c) => c.y - a.y);
    filling.sort((a, c) => a.y - c.y);
    foundation.sort((a, c) => a[0].y - c[0].y);

    // The structure, bottom-up; two-block pieces go together, small things last.
    const byKey = new Map(bp.blocks.map(([x, y, z, block]) => [x + "," + y + "," + z, block]));
    const partnerOf = (x, y, z, block) => {
        if (block.b.endsWith("_door") && block.s && !block.s.upper) {
            return [x, y + 1, z];
        }
        if (block.b === "bed" && block.s && !block.s.head) {
            const d = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] }[block.s.dir];
            return [x + d[0], y, z + d[1]];
        }
        return null;
    };
    for (const [lx, ly, lz, block] of bp.blocks) {
        if ((block.b.endsWith("_door") && block.s?.upper) || (block.b === "bed" && block.s?.head)) {
            continue; // placed with its other half
        }
        const group = [toPlacement(b, lx, ly, lz, block, true)];
        const partner = partnerOf(lx, ly, lz, block);
        if (partner && byKey.has(partner.join(","))) {
            group.push(toPlacement(b, partner[0], partner[1], partner[2], byKey.get(partner.join(",")), false));
        }
        if (!group[0].perm) {
            continue; // a block this game version does not have
        }
        (DETAILS.some((word) => block.b.includes(word)) ? details : structure).push(group.filter((p) => p.perm));
    }

    const steps = [];
    for (let i = 0; i < clearing.length; i += EARTHWORK_BATCH) {
        steps.push(clearing.slice(i, i + EARTHWORK_BATCH));
    }
    steps.push(...foundation);
    for (let i = 0; i < filling.length; i += EARTHWORK_BATCH) {
        steps.push(filling.slice(i, i + EARTHWORK_BATCH));
    }
    steps.push(...structure, ...details, ...road(dim, s, b, bp));
    return steps;
}

function toPlacement(b, lx, ly, lz, block, cost) {
    const pos = toWorld(b, lx, ly, lz);
    return { ...pos, perm: permutation(rotateBlock(block, b.r)), cost };
}

function isSolid(id) {
    return !isAir(id) && !isWater(id) && !isVegetation(id);
}

/** An L-shaped path from the door to the center that stops at an existing path. */
function road(dim, s, b, bp) {
    const steps = [];
    if (b.t === "lamp") {
        return steps;
    }
    const path = permutation({ b: "dirt_path" });
    if (!path) {
        return steps;
    }
    const entrance = toWorld(b, bp.entrance[0], 0, bp.entrance[1]);
    const box = bounds(b, bp);
    let x = entrance.x;
    let z = entrance.z;
    const alongZFirst = b.r % 2 === 0;
    for (let i = 0; i < MAX_ROAD; i++) {
        if (Math.abs(x - s.x) <= 2 && Math.abs(z - s.z) <= 2) {
            break;
        }
        const inside = x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ;
        const top = ground(dim, x, z, b.y);
        if (!top) {
            break;
        }
        if (i > 2 && top.id.includes("path")) {
            break;
        }
        if (!inside && (top.id.includes("grass") || top.id.includes("dirt"))) {
            const above = getBlock(dim, x, top.y + 1, z)?.typeId;
            if (above && !isAir(above) && isVegetation(above)) {
                steps.push([{ x, y: top.y + 1, z, cost: false }]);
            }
            steps.push([{ x, y: top.y, z, perm: path, cost: false }]);
        }
        const moveZ = alongZFirst ? z !== s.z : x === s.x;
        if (moveZ) {
            z += Math.sign(s.z - z);
        } else {
            x += Math.sign(s.x - x);
        }
    }
    return steps;
}
