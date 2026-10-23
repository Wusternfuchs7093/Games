// Procedural buildings in the style of a culture and period (see palette.js).
//
// Every plot has a one-block ring around the building's body for roof eaves, shutters and towers; the body's
// walls stand at x = 1 .. width-2 and z = 1 .. depth-2. Storeys are four blocks high. The front faces +z
// (south), the door is in the middle of the front wall and the entrance is the spot just outside the plot.
// A plot's size depends only on the building type, culture and variant, never on the period, so a building
// can be rebuilt in the style of a new age on the same spot.
//
// Blocks are {b: name, s: states} with neutral states: dir (north/south/east/west; for stairs the high side,
// for doors and chests the way they face, for beds the head), axis (x/y/z), upper, head, open, hanging.
// Pure logic: no Minecraft imports.

import { paletteOf } from "./palette.js";

export const BUILDING_TYPES = ["house", "farm", "workshop", "well", "warehouse", "market", "town_hall", "library",
    "temple", "watchtower", "lamp"];

const HOUSE_SIZES = [[7, 7], [9, 7], [7, 9], [9, 9], [11, 9], [9, 11]];
const WORKSTATIONS = ["smithing_table", "fletching_table", "cartography_table", "loom", "smoker", "blast_furnace",
    "grindstone", "stonecutter_block", "brewing_stand", "lectern", "cauldron", "composter"];
const CROPS = ["wheat", "carrots", "potatoes", "beetroot"];

export function variantCount(type) {
    switch (type) {
        case "house": return HOUSE_SIZES.length;
        case "workshop": return WORKSTATIONS.length;
        case "farm": return CROPS.length;
        case "market": return 4;
        default: return 1;
    }
}

const OPPOSITE = { north: "south", south: "north", east: "west", west: "east" };

function st(b, s) {
    return s ? { b, s } : { b };
}

// ------------------------------------------------------------------ canvas

export class Canvas {
    constructor(width, depth) {
        this.width = width;
        this.depth = depth;
        this.blocks = new Map();
        this.storage = [];
        this.entrance = null;
        this.top = 0;
    }

    static key(x, y, z) {
        return x + "," + y + "," + z;
    }

    set(x, y, z, block) {
        if (x < 0 || z < 0 || y < 0 || x >= this.width || z >= this.depth || !block) {
            return;
        }
        this.blocks.set(Canvas.key(x, y, z), typeof block === "string" ? st(block) : block);
        this.top = Math.max(this.top, y);
    }

    get(x, y, z) {
        return this.blocks.get(Canvas.key(x, y, z));
    }

    isEmpty(x, y, z) {
        const block = this.get(x, y, z);
        return !block || block.b === "air";
    }

    fill(x1, y1, z1, x2, y2, z2, block) {
        for (let y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (let z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (let x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    this.set(x, y, z, block);
                }
            }
        }
    }

    door(x, y, z, door, dir) {
        this.set(x, y, z, st(door, { dir, upper: false }));
        this.set(x, y + 1, z, st(door, { dir, upper: true }));
    }

    /** A bed whose foot is at (x, y, z) and whose head points towards {@code dir}. */
    bed(x, y, z, dir) {
        const d = DIRS[dir];
        this.set(x, y, z, st("bed", { dir, head: false }));
        this.set(x + d[0], y, z + d[1], st("bed", { dir, head: true }));
    }

    chest(x, y, z, dir) {
        this.set(x, y, z, st("chest", { dir }));
        this.storage.push([x, y, z]);
    }

    /** Freezes the drawing: a list of [x, y, z, block] in build order (bottom-up) plus metadata. */
    build() {
        const list = [];
        for (const [key, block] of this.blocks) {
            if (block.b === "air") {
                continue;
            }
            const [x, y, z] = key.split(",").map(Number);
            list.push([x, y, z, block]);
        }
        list.sort((a, b) => a[1] - b[1] || a[2] - b[2] || a[0] - b[0]);
        return {
            width: this.width, depth: this.depth, height: this.top + 1, blocks: list,
            storage: this.storage.filter(([x, y, z]) => this.get(x, y, z)?.b === "chest"),
            entrance: this.entrance ?? [Math.floor(this.width / 2), this.depth],
        };
    }
}

const DIRS = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] };

// ------------------------------------------------------------------ helpers

function perimeter(x0, z0, x1, z1, visit) {
    for (let x = x0; x <= x1; x++) {
        for (let z = z0; z <= z1; z++) {
            if (x === x0 || x === x1 || z === z0 || z === z1) {
                visit(x, z, (x === x0 || x === x1) && (z === z0 || z === z1));
            }
        }
    }
}

/** The four walls of a rectangle with their own numbering; visitor(x, z, index, length, axis, outwards). */
function wallCells(x0, z0, x1, z1, visit) {
    const lenX = x1 - x0 + 1;
    const lenZ = z1 - z0 + 1;
    for (let x = x0; x <= x1; x++) {
        visit(x, z0, x - x0, lenX, "x", "north");
        visit(x, z1, x - x0, lenX, "x", "south");
    }
    for (let z = z0; z <= z1; z++) {
        visit(x0, z, z - z0, lenZ, "z", "west");
        visit(x1, z, z - z0, lenZ, "z", "east");
    }
}

function ring(c, x0, z0, x1, z1, y, block) {
    perimeter(x0, z0, x1, z1, (x, z) => c.set(x, y, z, block));
}

function isLog(name) {
    return name.endsWith("_log") || name.endsWith("_wood");
}

function axis(name, a) {
    return isLog(name) ? st(name, { axis: a }) : st(name);
}

function slope(p, dir) {
    return p.roofStairs ? st(p.roofStairs, { dir }) : st(p.roofBlock);
}

function facing(name, dir) {
    return st(name, { dir });
}

// ------------------------------------------------------------------ shell and roofs

function wallBlock(p, storey, i, len, dy, along) {
    if (i === 0 || i === len - 1) {
        return axis(p.frame, "y");
    }
    const infill = storey === 0 ? p.lower : p.walls;
    const timber = p.halfTimber && (storey > 0 || p.lower === p.walls);
    if (timber && (dy === 0 || dy === 3)) {
        return axis(p.frame, along);
    }
    if (timber && i % 3 === 0) {
        return axis(p.frame, "y");
    }
    if (dy === 0 && !p.logWalls) {
        return axis(p.frame, along);
    }
    return p.logWalls ? axis(infill, along) : st(infill);
}

function windowAt(p, storey, i, len) {
    if (i <= 0 || i >= len - 1) {
        return false;
    }
    if (p.period === "modern") {
        return i % 3 !== 0;
    }
    const timber = p.halfTimber && (storey > 0 || p.lower === p.walls);
    if (timber) {
        return i % 3 === 2;
    }
    return i % 2 === 0 && i >= 2 && i <= len - 3;
}

/**
 * Raises the body: floor, walls with windows and shutters, storey floors with a ladder, the front door and the
 * roof. A hall has no floors between storeys, only tall windows. Returns the roof's top y.
 */
function shell(c, p, floors, roof, hall = false) {
    const w = c.width;
    const d = c.depth;
    const x0 = 1;
    const x1 = w - 2;
    const z0 = 1;
    const z1 = d - 2;
    const roofBase = 4 * floors;
    const modernStyle = p.period === "modern";

    for (let x = x0; x <= x1; x++) {
        for (let z = z0; z <= z1; z++) {
            const edge = x === x0 || x === x1 || z === z0 || z === z1;
            c.set(x, 0, z, edge ? p.foundation : p.floor);
        }
    }
    for (let storey = 0; storey < floors; storey++) {
        const base = 4 * storey;
        if (storey > 0 && !hall) {
            c.fill(x0 + 1, base, z0 + 1, x1 - 1, base, z1 - 1, p.floor);
        }
        for (let y = storey === 0 ? 1 : base; y <= base + 3; y++) {
            wallCells(x0, z0, x1, z1, (x, z, i, len, along) => c.set(x, y, z, wallBlock(p, storey, i, len, y - base, along)));
        }
        wallCells(x0, z0, x1, z1, (x, z, i, len, along, out) => {
            if (!windowAt(p, storey, i, len) || (out === "south" && storey === 0 && x === Math.floor(w / 2))) {
                return;
            }
            let low = modernStyle || hall ? base + 1 : base + 2;
            const high = hall ? base + 3 : base + 2;
            if (hall && storey === 0) {
                low = base + 2;
            }
            for (let y = low; y <= high; y++) {
                c.set(x, y, z, p.pane);
            }
            if (p.shutter && !modernStyle) {
                const side = along === "x" ? "east" : "south";
                for (const dir of [side, OPPOSITE[side]]) {
                    const sx = x + DIRS[dir][0] + DIRS[out][0];
                    const sz = z + DIRS[dir][1] + DIRS[out][1];
                    if (c.isEmpty(sx, base + 2, sz)) {
                        c.set(sx, base + 2, sz, st(p.shutter, { dir: out, open: true }));
                    }
                }
            }
        });
        if (!hall) {
            c.set(x1 - 1, base + 1, z0 + 1, st("lantern", { hanging: false })); // furniture may take the corner
        }
    }
    if (floors > 1 && !hall) {
        for (let y = 1; y <= 4 * (floors - 1); y++) {
            c.set(x0 + 1, y, z1 - 1, st("ladder", { dir: "east" }));
        }
    }
    c.door(Math.floor(w / 2), 1, z1, p.door, "south");
    c.set(Math.floor(w / 2) + 1, 1, z1 + 1, st(p.light === "sea_lantern" ? "lantern" : p.light, { hanging: false }));
    c.entrance = [Math.floor(w / 2), d];

    switch (roof) {
        case "gable": return gable(c, p, 0, 0, w - 1, d - 1, roofBase, false);
        case "steep": return gable(c, p, 0, 0, w - 1, d - 1, roofBase, true);
        case "flat": return flat(c, p, x0, z0, x1, z1, roofBase);
        case "stepped":
            plate(c, p, x0, z0, x1, z1, roofBase);
            return hip(c, p, 0, 0, w - 1, d - 1, roofBase, 1, true);
        case "pagoda": {
            plate(c, p, x0, z0, x1, z1, roofBase);
            const top = hip(c, p, 0, 0, w - 1, d - 1, roofBase, 1, !p.roofStairs);
            for (const x of [0, w - 1]) {
                for (const z of [0, d - 1]) {
                    c.set(x, roofBase + 1, z, p.roofSlab);
                }
            }
            return top;
        }
        default:
            plate(c, p, x0, z0, x1, z1, roofBase);
            return hip(c, p, 0, 0, w - 1, d - 1, roofBase, 1, !p.roofStairs);
    }
}

/** Pitched roof over the rectangle, ridge along the longer side; gable ends and wall plates walled up. */
function gable(c, p, x0, z0, x1, z1, base, steep) {
    const w = x1 - x0 + 1;
    const d = z1 - z0 + 1;
    const alongZ = d >= w;
    const span = alongZ ? w : d;
    const length = alongZ ? d : w;
    const rise = steep ? 2 : 1;
    const loDir = alongZ ? "east" : "south";
    const put = (s, l, y, block) => alongZ ? c.set(x0 + s, y, z0 + l, block) : c.set(x0 + l, y, z0 + s, block);
    let top = base;
    for (let k = 0; k <= span / 2; k++) {
        const lo = k;
        const hi = span - 1 - k;
        if (lo > hi) {
            break;
        }
        const y = base + k * rise;
        top = y;
        for (let l = 0; l < length; l++) {
            if (lo === hi) {
                put(lo, l, y, p.roofSlab);
                if (steep && k > 0) {
                    put(lo, l, y - 1, p.roofBlock);
                }
            } else {
                put(lo, l, y, slope(p, loDir));
                put(hi, l, y, slope(p, OPPOSITE[loDir]));
                if (steep && k > 0) {
                    put(lo, l, y - 1, p.roofBlock);
                    put(hi, l, y - 1, p.roofBlock);
                }
            }
        }
    }
    const endAxis = alongZ ? "x" : "z";
    const sideAxis = alongZ ? "z" : "x";
    const gableBlock = (a) => p.logWalls ? axis(p.walls, a) : st(p.walls);
    for (let j = 1; j <= span - 2; j++) {
        const k = Math.min(j, span - 1 - j);
        const surface = base + k * rise;
        const highest = surface - (steep && k > 0 ? 2 : 1);
        for (let y = base; y <= highest; y++) {
            put(j, 1, y, gableBlock(endAxis));
            put(j, length - 2, y, gableBlock(endAxis));
            if (j === 1 || j === span - 2) {
                for (let l = 2; l <= length - 3; l++) {
                    put(j, l, y, gableBlock(sideAxis));
                }
            }
        }
    }
    return top;
}

/** Concentric rings rising {@code rise} per step; rise 2 sits each ring on full blocks (a spire). */
function hip(c, p, x0, z0, x1, z1, base, rise, blocks) {
    let top = base;
    for (let k = 0; ; k++) {
        const ax = x0 + k;
        const bx = x1 - k;
        const az = z0 + k;
        const bz = z1 - k;
        if (ax > bx || az > bz) {
            break;
        }
        const y = base + k * rise;
        top = y;
        if (rise > 1 && k > 0) {
            ring(c, ax, az, bx, bz, y - 1, p.roofBlock);
        }
        if (ax === bx || az === bz) {
            c.fill(ax, y, az, bx, y, bz, blocks ? p.roofBlock : p.roofSlab);
            break;
        }
        if (blocks) {
            ring(c, ax, az, bx, bz, y, p.roofBlock);
        } else {
            hipRing(c, p, ax, az, bx, bz, y);
        }
    }
    return top;
}

function hipRing(c, p, ax, az, bx, bz, y) {
    for (let x = ax; x <= bx; x++) {
        c.set(x, y, az, slope(p, "south"));
        c.set(x, y, bz, slope(p, "north"));
    }
    for (let z = az + 1; z <= bz - 1; z++) {
        c.set(ax, y, z, slope(p, "east"));
        c.set(bx, y, z, slope(p, "west"));
    }
}

function flat(c, p, x0, z0, x1, z1, base) {
    c.fill(x0, base, z0, x1, base, z1, p.roofBlock);
    ring(c, x0, z0, x1, z1, base + 1, p.trim);
    let top = base + 1;
    const crenels = p.culture === "desert" && ["early", "medieval", "renaissance"].includes(p.period);
    if (crenels || p.poles) {
        let index = 0;
        perimeter(x0, z0, x1, z1, (x, z) => {
            if (index++ % 2 === 0) {
                c.set(x, base + 2, z, crenels ? p.trim : p.fence);
            }
        });
        top = base + 2;
    }
    return top;
}

function plate(c, p, x0, z0, x1, z1, y) {
    perimeter(x0, z0, x1, z1, (x, z, corner) => c.set(x, y, z, corner ? axis(p.frame, "y") : st(p.walls)));
}

function tower(c, p, x0, z0, x1, z1, y0, y1, material) {
    for (let y = y0; y <= y1; y++) {
        perimeter(x0, z0, x1, z1, (x, z, corner) => c.set(x, y, z, corner ? axis(p.frame, "y") : st(material ?? p.walls)));
    }
}

/** A hollow dome; {@code profile} gives the radius of each layer. */
function drawProfile(c, cx, cz, baseY, radii, block) {
    const inDisc = (dx, dz, r) => dx * dx + dz * dz <= r * r + r;
    radii.forEach((r, i) => {
        const solid = i === 0 || i === radii.length - 1;
        for (let dx = -r; dx <= r; dx++) {
            for (let dz = -r; dz <= r; dz++) {
                if (!inDisc(dx, dz, r)) {
                    continue;
                }
                const rim = !inDisc(dx + 1, dz, r) || !inDisc(dx - 1, dz, r) || !inDisc(dx, dz + 1, r) || !inDisc(dx, dz - 1, r);
                if (solid || rim) {
                    c.set(cx + dx, baseY + i, cz + dz, block);
                }
            }
        }
    });
}

function dome(c, cx, cz, baseY, radius, block) {
    const radii = [];
    for (let dy = 0; dy <= radius; dy++) {
        radii.push(Math.round(Math.sqrt(radius * radius - dy * dy)));
    }
    drawProfile(c, cx, cz, baseY, radii, block);
    c.set(cx, baseY + radii.length, cz, "lightning_rod");
}

function onion(c, cx, cz, baseY, size, block) {
    const radii = size >= 2 ? [2, 3, 3, 2, 1, 0] : [1, 2, 1, 0];
    drawProfile(c, cx, cz, baseY, radii, block);
    c.set(cx, baseY + radii.length, cz, "chain");
    c.set(cx, baseY + radii.length + 1, cz, "lightning_rod");
}

/** What rises above a town hall: a clock tower, a dome, an onion, mud towers, a pagoda tier or a roof comb. */
function crown(c, p, cx, cz, roofBase, top) {
    switch (p.culture) {
        case "desert":
            drawProfile(c, cx, cz, roofBase + 1, [3, 3], st(p.walls));
            dome(c, cx, cz, roofBase + 3, 3, p.accent);
            break;
        case "taiga":
            tower(c, p, cx - 1, cz - 1, cx + 1, cz + 1, roofBase, top + 1);
            onion(c, cx, cz, top + 2, 1, p.accent);
            break;
        case "savanna":
            for (const x of [1, c.width - 2]) {
                c.fill(x, 1, c.depth - 2, x, top + 3, c.depth - 2, axis(p.frame, "y"));
                c.set(x, top + 4, c.depth - 2, p.fence);
            }
            break;
        case "cherry": {
            tower(c, p, cx - 1, cz - 1, cx + 1, cz + 1, roofBase + 2, top + 1);
            const peak = hip(c, p, cx - 2, cz - 2, cx + 2, cz + 2, top + 2, 1, !p.roofStairs);
            c.set(cx, peak + 1, cz, "lightning_rod");
            break;
        }
        case "jungle":
            for (let x = 1; x <= c.width - 2; x += 2) {
                c.fill(x, top + 1, cz, x, top + 2, cz, p.accent);
            }
            break;
        default: {
            const towerTop = top + 3;
            tower(c, p, cx - 1, cz - 1, cx + 1, cz + 1, roofBase, towerTop);
            c.set(cx, towerTop - 1, cz - 1, p.accent);
            c.set(cx, towerTop - 1, cz + 1, p.accent);
            c.set(cx - 1, towerTop - 1, cz, p.accent);
            c.set(cx + 1, towerTop - 1, cz, p.accent);
            const peak = hip(c, p, cx - 2, cz - 2, cx + 2, cz + 2, towerTop + 1, 2, !p.roofStairs);
            c.set(cx, peak + 1, cz, "lightning_rod");
        }
    }
}

// ------------------------------------------------------------------ furniture

function beds(c, y, count) {
    for (let i = 0; i < count; i++) {
        const x = 2 + i;
        if (x > c.width - 4) {
            break;
        }
        c.bed(x, y, 3, "north");
    }
}

function kitchen(c, p) {
    c.set(3, 1, 3, p.fence);
    c.set(3, 2, 3, "oak_pressure_plate"); // ignored if the name is unknown; see blocks.js fallbacks
    c.set(4, 1, 3, facing(p.wallStairs, "west"));
    c.set(c.width - 3, 1, c.depth - 3, "crafting_table");
    c.set(2, 1, 2, facing("barrel", "south"));
}

function chimney(c, p, top) {
    if (!p.chimney) {
        return;
    }
    const x = c.width - 3;
    c.fill(x, 1, 1, x, top + 1, 1, p.period === "early" ? "cobblestone" : "bricks");
    c.set(x, top + 2, 1, "campfire");
    c.set(x, 1, 2, facing("furnace", "south"));
}

function pews(c, p, fromZ, toZ) {
    const cx = Math.floor(c.width / 2);
    for (let z = fromZ; z <= toZ; z += 2) {
        for (let x = 2; x <= c.width - 3; x++) {
            if (Math.abs(x - cx) > 1) {
                c.set(x, 1, z, facing(p.wallStairs, "south"));
            }
        }
    }
}

function altar(c, p, cx) {
    c.set(cx, 1, 2, axis(p.frame, "y"));
    c.set(cx, 2, 2, "lantern");
    c.set(cx - 2, 1, 2, "brewing_stand");
    c.set(cx + 2, 1, 2, facing("lectern", "south"));
}

const CANOPY = {
    plains: ["red_wool", "white_wool"], desert: ["blue_wool", "white_wool"], savanna: ["orange_wool", "yellow_wool"],
    snowy: ["light_blue_wool", "white_wool"], taiga: ["red_wool", "yellow_wool"], other: ["green_wool", "white_wool"],
    cherry: ["pink_wool", "white_wool"], jungle: ["lime_wool", "yellow_wool"],
};

// ------------------------------------------------------------------ building types

function house(p, variant) {
    const [w, d] = HOUSE_SIZES[((variant % HOUSE_SIZES.length) + HOUSE_SIZES.length) % HOUSE_SIZES.length];
    const floors = w * d <= 63 ? Math.min(p.floors, 2) : p.floors;
    const c = new Canvas(w, d);
    const top = shell(c, p, floors, p.roof);
    if (floors === 1) {
        beds(c, 1, w - 5);
        c.set(w - 3, 1, d - 3, "crafting_table");
    } else {
        kitchen(c, p);
        for (let storey = 1; storey < floors; storey++) {
            beds(c, 4 * storey + 1, w - 5);
        }
    }
    chimney(c, p, top);
    return c;
}

function workshop(p, variant) {
    const c = new Canvas(9, 9);
    shell(c, p, Math.min(2, p.floors), p.roof);
    c.set(2, 1, 2, facing(WORKSTATIONS[variant % WORKSTATIONS.length], "south"));
    c.set(6, 1, 2, facing(WORKSTATIONS[(variant + 5) % WORKSTATIONS.length], "south"));
    return c;
}

function warehouse(p) {
    const w = 11;
    const d = 9;
    const c = new Canvas(w, d);
    shell(c, p, 1, p.roof);
    for (let x = 2; x <= w - 3; x += 2) {
        c.chest(x, 1, 2, "south");
    }
    c.set(2, 1, d - 4, "hay_block");
    c.set(w - 3, 1, d - 4, facing("barrel", "south"));
    return c;
}

function townHall(p) {
    const w = 13;
    const d = 11;
    const c = new Canvas(w, d);
    const floors = Math.max(2, Math.min(3, p.floors));
    const top = shell(c, p, floors, p.roof);
    const cx = Math.floor(w / 2);
    c.set(cx, 1, 2, facing("lectern", "south"));
    for (let z = 3; z <= d - 3; z++) {
        c.set(cx, 1, z, "red_carpet");
    }
    c.set(3, 1, d - 4, "bell");
    c.fill(2, 1, 2, 3, 1, 2, "bookshelf");
    c.fill(w - 4, 1, 2, w - 3, 1, 2, "bookshelf");
    for (let storey = 1; storey < floors; storey++) {
        c.fill(3, 4 * storey + 1, 2, w - 4, 4 * storey + 1, 2, "bookshelf");
    }
    crown(c, p, cx, Math.floor(d / 2), 4 * floors, top);
    return c;
}

function library(p) {
    const w = 11;
    const d = 9;
    const c = new Canvas(w, d);
    const floors = Math.min(2, p.floors);
    shell(c, p, floors, p.roof);
    for (let storey = 0; storey < floors; storey++) {
        const y = 4 * storey + 1;
        c.fill(3, y, 2, w - 3, y + 1, 2, "bookshelf");
    }
    c.set(3, 1, 4, facing("lectern", "south"));
    c.set(w - 4, 1, 4, facing("lectern", "south"));
    return c;
}

function market(p, variant) {
    const c = new Canvas(9, 7);
    c.fill(0, 0, 0, 8, 0, 6, p.foundation);
    for (const x of [0, 8]) {
        for (const z of [0, 6]) {
            c.fill(x, 1, z, x, 2, z, p.fence);
        }
    }
    const colours = CANOPY[p.culture] ?? CANOPY.plains;
    for (let x = 0; x <= 8; x++) {
        c.fill(x, 3, 0, x, 3, 6, colours[x % 2]);
    }
    c.fill(1, 1, 4, 3, 1, 4, p.roofSlab);
    c.fill(5, 1, 4, 7, 1, 4, p.roofSlab);
    const goods = ["hay_block", "melon_block", "pumpkin", "barrel"];
    c.set(2, 1, 1, goods[variant % goods.length]);
    c.set(4, 1, 1, "melon_block");
    c.set(6, 1, 1, "pumpkin");
    c.set(2, 2, 3, st("lantern", { hanging: true }));
    c.set(6, 2, 3, st("lantern", { hanging: true }));
    c.entrance = [4, 7];
    return c;
}

function well(p) {
    const c = new Canvas(5, 5);
    c.fill(0, 0, 0, 4, 0, 4, p.foundation);
    c.fill(1, 0, 1, 3, 0, 3, "water");
    ring(c, 0, 0, 4, 4, 1, p.foundation);
    for (const x of [0, 4]) {
        for (const z of [0, 4]) {
            c.set(x, 2, z, p.fence);
        }
    }
    c.fill(0, 3, 0, 4, 3, 4, p.roofSlab);
    c.entrance = [2, 5];
    return c;
}

function farm(p, variant) {
    const c = new Canvas(9, 9);
    c.fill(0, 0, 0, 8, 0, 8, axis(p.frame, "y"));
    c.fill(1, 0, 1, 7, 0, 7, "farmland");
    c.set(4, 0, 4, "water");
    const crop = CROPS[variant % CROPS.length];
    for (let x = 1; x <= 7; x++) {
        for (let z = 1; z <= 7; z++) {
            if (x !== 4 || z !== 4) {
                c.set(x, 1, z, crop);
            }
        }
    }
    c.set(0, 1, 0, "composter");
    c.entrance = [4, 9];
    return c;
}

function watchtower(p) {
    const c = new Canvas(7, 7);
    c.fill(1, 0, 1, 5, 0, 5, p.foundation);
    for (let y = 1; y <= 7; y++) {
        perimeter(1, 1, 5, 5, (x, z, corner) => c.set(x, y, z, corner ? axis(p.frame, "y") : st(y <= 3 ? p.lower : p.walls)));
    }
    c.set(1, 4, 3, p.pane);
    c.set(5, 4, 3, p.pane);
    c.fill(0, 8, 0, 6, 8, 6, p.roofBlock);
    for (let y = 1; y <= 8; y++) {
        c.set(3, y, 2, st("ladder", { dir: "south" }));
    }
    perimeter(0, 0, 6, 6, (x, z, corner) => c.set(x, 9, z, corner ? axis(p.frame, "y") : st(p.fence)));
    if (p.roof === "flat") {
        perimeter(0, 0, 6, 6, (x, z, corner) => corner && c.set(x, 10, z, p.light));
    } else {
        for (const x of [0, 6]) {
            for (const z of [0, 6]) {
                c.set(x, 10, z, axis(p.frame, "y"));
            }
        }
        hip(c, p, 0, 0, 6, 6, 11, p.roof === "steep" ? 2 : 1, !p.roofStairs);
        c.set(3, 9, 3, "lantern");
    }
    c.door(3, 1, 5, p.door, "south");
    c.entrance = [3, 7];
    return c;
}

function lamp(p) {
    const c = new Canvas(1, 1);
    c.set(0, 0, 0, p.foundation);
    c.fill(0, 1, 0, 0, 2, 0, p.fence);
    c.set(0, 3, 0, p.light);
    return c;
}

// ------------------------------------------------------------------ temples

function church(p) {
    const w = 11;
    const d = 17;
    const c = new Canvas(w, d);
    const top = shell(c, p, 2, p.roof === "flat" ? "flat" : "steep", true);
    const cx = Math.floor(w / 2);
    pews(c, p, 6, d - 8);
    altar(c, p, cx);
    const tz0 = d - 6;
    const tz1 = d - 2;
    const towerTop = top + 4;
    tower(c, p, cx - 2, tz0, cx + 2, tz1, 1, towerTop);
    c.fill(cx - 1, 8, tz0 + 1, cx + 1, 8, tz1 - 1, p.floor);
    c.fill(cx, 1, tz0, cx, 2, tz0, "air");
    const mz = Math.floor((tz0 + tz1) / 2);
    for (const [ox, oz] of [[0, -2], [0, 2], [-2, 0], [2, 0]]) {
        c.fill(cx + ox, towerTop - 2, mz + oz, cx + ox, towerTop - 1, mz + oz, "air");
    }
    c.door(cx, 1, tz1, p.door, "south");
    const spireTop = hip(c, p, cx - 2, tz0, cx + 2, tz1, towerTop + 1, 2, !p.roofStairs);
    c.set(cx, spireTop + 1, mz, "lightning_rod");
    return c;
}

function mosque(p) {
    const w = 13;
    const d = 13;
    const c = new Canvas(w, d);
    shell(c, p, 2, "flat", true);
    c.fill(2, 1, 2, w - 3, 1, d - 3, "red_carpet");
    c.fill(6, 1, 3, 6, 1, d - 3, "blue_carpet");
    c.set(6, 1, 2, "brewing_stand");
    c.set(6, 2, 1, p.accent);
    drawProfile(c, 6, 6, 9, [3, 3], st(p.walls));
    dome(c, 6, 6, 11, 3, p.accent);
    const minaretTop = 17;
    tower(c, p, 0, d - 3, 2, d - 1, 0, minaretTop);
    ring(c, 0, d - 3, 2, d - 1, minaretTop - 3, p.trim);
    dome(c, 1, d - 2, minaretTop + 1, 1, p.accent);
    return c;
}

function russianChurch(p) {
    const w = 11;
    const d = 13;
    const c = new Canvas(w, d);
    const top = shell(c, p, 2, p.roof, true);
    const cx = 5;
    const cz = 6;
    pews(c, p, 5, d - 4);
    altar(c, p, cx);
    const drumTop = top + 3;
    tower(c, p, cx - 1, cz - 1, cx + 1, cz + 1, 8, drumTop);
    onion(c, cx, cz, drumTop + 1, 2, p.accent);
    if (p.roof === "flat") {
        for (const [x, z] of [[1, 1], [w - 2, 1], [1, d - 2], [w - 2, d - 2]]) {
            c.fill(x, 9, z, x, 11, z, axis(p.frame, "y"));
            onion(c, x, z, 12, 1, p.accent);
        }
    } else {
        for (const z of [2, d - 3]) {
            c.set(cx, top + 1, z, axis(p.frame, "y"));
            onion(c, cx, z, top + 2, 1, p.accent);
        }
    }
    return c;
}

function mudMosque(p) {
    const w = 13;
    const d = 11;
    const c = new Canvas(w, d);
    shell(c, p, 2, "flat", true);
    c.set(6, 1, 2, "brewing_stand");
    c.fill(2, 1, 3, w - 3, 1, d - 4, "orange_carpet");
    for (const x of [1, 4, 8, w - 2]) {
        c.fill(x, 1, d - 2, x, 12, d - 2, axis(p.frame, "y"));
        c.set(x, 13, d - 2, p.accent);
        c.set(x, 14, d - 2, p.fence);
    }
    for (const y of [3, 7]) {
        perimeter(0, 0, w - 1, d - 1, (x, z, corner) => {
            if (!corner && (x + z) % 3 === 0) {
                c.set(x, y, z, p.fence);
            }
        });
    }
    return c;
}

function pagoda(p) {
    const c = new Canvas(11, 11);
    shell(c, p, 1, "pagoda");
    const cx = 5;
    const cz = 5;
    c.set(cx, 1, 2, "brewing_stand");
    c.set(cx - 2, 1, 2, "lantern");
    c.set(cx + 2, 1, 2, "lantern");
    let y = 7;
    for (let tier = 0; tier < 2; tier++) {
        tower(c, p, cx - 1, cz - 1, cx + 1, cz + 1, y, y + 2);
        const eave = y + 3;
        hipRing(c, p, cx - 2, cz - 2, cx + 2, cz + 2, eave);
        c.fill(cx - 1, eave, cz - 1, cx + 1, eave, cz + 1, p.roofBlock);
        for (const [x, z] of [[cx - 2, cz - 2], [cx + 2, cz - 2], [cx - 2, cz + 2], [cx + 2, cz + 2]]) {
            c.set(x, eave + 1, z, p.roofSlab);
        }
        y = eave + 1;
    }
    const peak = hip(c, p, cx - 1, cz - 1, cx + 1, cz + 1, y, 1, !p.roofStairs);
    c.set(cx, peak + 1, cz, "lightning_rod");
    return c;
}

function pyramid(p) {
    const w = 13;
    const d = 13;
    const c = new Canvas(w, d);
    const levels = 4;
    for (let level = 0; level < levels; level++) {
        ring(c, level, level, w - 1 - level, d - 1 - level, level, level === 0 ? p.foundation : p.walls);
    }
    c.fill(levels, levels, levels, w - 1 - levels, levels, d - 1 - levels, p.walls);
    const cx = 6;
    const stairs = p.roofStairs ?? "stone_brick_stairs";
    for (let level = 0; level < levels; level++) {
        for (let x = cx - 1; x <= cx + 1; x++) {
            c.set(x, level, d - 1 - level, facing(stairs, "north"));
        }
    }
    const s0 = levels;
    const s1 = w - 1 - levels;
    const floor = levels + 1;
    tower(c, p, s0, s0, s1, s1, floor, floor + 2);
    c.fill(s0, floor + 3, s0, s1, floor + 3, s1, p.roofBlock);
    for (let x = s0; x <= s1; x += 2) {
        c.fill(x, floor + 4, cx, x, floor + 5, cx, p.accent);
    }
    c.door(cx, floor, s1, p.door, "south");
    c.set(cx, floor, s0 + 1, "brewing_stand");
    c.set(cx, floor + 1, s0 + 1, "lantern");
    c.entrance = [cx, d];
    return c;
}

function temple(p) {
    switch (p.culture) {
        case "desert": return mosque(p);
        case "taiga": return russianChurch(p);
        case "savanna": return mudMosque(p);
        case "cherry": return pagoda(p);
        case "jungle": return pyramid(p);
        default: return church(p);
    }
}

const GENERATORS = { house, farm, workshop, well, warehouse, market, town_hall: townHall, library, temple, watchtower, lamp };

const cache = new Map();

/** The blueprint of a building type for a culture in an era. */
export function blueprint(type, culture, era, variant = 0, masonry = false) {
    const p = paletteOf(culture, era, masonry);
    const v = ((variant % variantCount(type)) + variantCount(type)) % variantCount(type);
    const key = [type, culture, p.period, v, masonry && p.period === "early"].join("/");
    let bp = cache.get(key);
    if (!bp) {
        const generator = GENERATORS[type];
        if (!generator) {
            throw new Error("Unknown building type " + type);
        }
        bp = generator(p, v).build();
        bp.palette = p;
        cache.set(key, bp);
    }
    return bp;
}
