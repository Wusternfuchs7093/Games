// Reading the world: blocks, ground height, and placing a building's local coordinates in it.

import { isAir, isLeaves, isLog, isVegetation } from "./blocks.js";

export function getBlock(dim, x, y, z) {
    try {
        return dim.getBlock({ x, y, z });
    } catch {
        return undefined;
    }
}

export function typeAt(dim, x, y, z) {
    return getBlock(dim, x, y, z)?.typeId;
}

/** Highest non-air y in the column, or a guess above {@code nearY} if the API cannot tell. */
function topOf(dim, x, z, nearY) {
    try {
        const block = dim.getTopmostBlock?.({ x, z });
        if (block) {
            return block.location.y;
        }
    } catch {
        // older API or unloaded column
    }
    for (let y = nearY + 32; y > nearY - 32; y--) {
        const id = typeAt(dim, x, y, z);
        if (id === undefined) {
            return undefined;
        }
        if (!isAir(id)) {
            return y;
        }
    }
    return nearY - 32;
}

/**
 * The natural ground in a column, looking through trees, plants and snow: {y, id}, or undefined when the
 * column is not loaded. Logs only count as trees under leaves, so a log cabin is not mistaken for a forest.
 */
export function ground(dim, x, z, nearY) {
    const top = topOf(dim, x, z, nearY);
    if (top === undefined) {
        return undefined;
    }
    let underLeaves = false;
    for (let y = top; y > top - 48; y--) {
        const id = typeAt(dim, x, y, z);
        if (id === undefined) {
            return undefined;
        }
        if (isLeaves(id)) {
            underLeaves = true;
        } else if (!(isVegetation(id) && (underLeaves || !isLog(id)))) {
            return { y, id };
        }
    }
    return undefined;
}

export function columnTop(dim, x, z, nearY) {
    return topOf(dim, x, z, nearY);
}

/** World position of a building's local (x, y, z); {@code b.r} is the number of clockwise quarter turns. */
export function toWorld(b, lx, ly, lz) {
    let x = lx;
    let z = lz;
    switch (b.r) {
        case 1: [x, z] = [-lz, lx]; break;
        case 2: [x, z] = [-lx, -lz]; break;
        case 3: [x, z] = [lz, -lx]; break;
        default: break;
    }
    return { x: b.x + x, y: b.y + ly, z: b.z + z };
}

/** Footprint and height of a placed building: {minX, maxX, minZ, maxZ, minY, maxY}. */
export function bounds(b, bp) {
    const a = toWorld(b, 0, 0, 0);
    const c = toWorld(b, bp.width - 1, bp.height - 1, bp.depth - 1);
    return {
        minX: Math.min(a.x, c.x), maxX: Math.max(a.x, c.x),
        minZ: Math.min(a.z, c.z), maxZ: Math.max(a.z, c.z),
        minY: a.y, maxY: c.y,
    };
}

export function overlaps(a, b, margin) {
    return a.minX - margin <= b.maxX && a.maxX + margin >= b.minX && a.minZ - margin <= b.maxZ && a.maxZ + margin >= b.minZ;
}
