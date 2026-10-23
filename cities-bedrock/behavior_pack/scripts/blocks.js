// Turns the neutral block names and states of architecture.js into Bedrock block permutations.
// Bedrock renamed many blocks over the years ("flattening"), so each name has a list of candidates and each
// orientation a list of state spellings; the first one this game version accepts wins and is cached.

import { BlockPermutation } from "@minecraft/server";

const WOOD = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"];
const COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
    "purple", "blue", "brown", "green", "red", "black"];

/** name → list of candidates; a candidate is an id or [id, states]. */
const ALIASES = {
    bricks: ["minecraft:brick_block"],
    stone_bricks: ["minecraft:stone_bricks", ["minecraft:stonebrick", { stone_brick_type: "default" }]],
    mossy_stone_bricks: ["minecraft:mossy_stone_bricks", ["minecraft:stonebrick", { stone_brick_type: "mossy" }]],
    chiseled_stone_bricks: ["minecraft:chiseled_stone_bricks", ["minecraft:stonebrick", { stone_brick_type: "chiseled" }]],
    cut_sandstone: ["minecraft:cut_sandstone", ["minecraft:sandstone", { sand_stone_type: "cut" }]],
    smooth_sandstone: ["minecraft:smooth_sandstone", ["minecraft:sandstone", { sand_stone_type: "smooth" }]],
    chiseled_sandstone: ["minecraft:chiseled_sandstone", ["minecraft:sandstone", { sand_stone_type: "heiroglyphs" }]],
    sandstone: ["minecraft:sandstone", ["minecraft:sandstone", { sand_stone_type: "default" }]],
    polished_diorite: ["minecraft:polished_diorite", ["minecraft:stone", { stone_type: "diorite_smooth" }]],
    polished_andesite: ["minecraft:polished_andesite", ["minecraft:stone", { stone_type: "andesite_smooth" }]],
    terracotta: ["minecraft:hardened_clay"],
    dirt_path: ["minecraft:grass_path", "minecraft:dirt_path"],
    grass_block: ["minecraft:grass_block", "minecraft:grass"],
    coarse_dirt: ["minecraft:coarse_dirt", ["minecraft:dirt", { dirt_type: "coarse" }]],
    dark_oak_log: ["minecraft:dark_oak_log", ["minecraft:log2", { new_log_type: "dark_oak" }]],
    acacia_log: ["minecraft:acacia_log", ["minecraft:log2", { new_log_type: "acacia" }]],
    oak_door: ["minecraft:wooden_door"],
    oak_trapdoor: ["minecraft:trapdoor"],
    oak_fence: ["minecraft:oak_fence", ["minecraft:fence", { wood_type: "oak" }]],
    oak_pressure_plate: ["minecraft:wooden_pressure_plate"],
    melon_block: ["minecraft:melon_block"],
    stonecutter_block: ["minecraft:stonecutter_block"],
    snow_layer: ["minecraft:snow_layer"],
    smooth_stone_slab: ["minecraft:smooth_stone_slab", ["minecraft:stone_block_slab", { stone_slab_type: "smooth_stone" }]],
    brick_slab: ["minecraft:brick_slab", ["minecraft:stone_block_slab", { stone_slab_type: "brick" }]],
    stone_brick_slab: ["minecraft:stone_brick_slab", ["minecraft:stone_block_slab", { stone_slab_type: "stone_brick" }]],
    smooth_sandstone_slab: ["minecraft:smooth_sandstone_slab", ["minecraft:stone_block_slab2", { stone_slab_type_2: "smooth_sandstone" }]],
    sandstone_wall: ["minecraft:sandstone_wall", ["minecraft:cobblestone_wall", { wall_block_type: "sandstone" }]],
    cobblestone_wall: ["minecraft:cobblestone_wall"],
    lantern: ["minecraft:lantern"],
    water: ["minecraft:water"],
    air: ["minecraft:air"],
};
for (const wood of WOOD) {
    ALIASES[wood + "_planks"] = ["minecraft:" + wood + "_planks", ["minecraft:planks", { wood_type: wood }]];
    ALIASES[wood + "_slab"] = ["minecraft:" + wood + "_slab", ["minecraft:wooden_slab", { wood_type: wood }]];
    if (wood !== "oak") {
        ALIASES[wood + "_fence"] = ["minecraft:" + wood + "_fence", ["minecraft:fence", { wood_type: wood }]];
    }
}
for (const color of COLORS) {
    ALIASES[color + "_wool"] = ["minecraft:" + color + "_wool", ["minecraft:wool", { color: color === "light_gray" ? "silver" : color }]];
    ALIASES[color + "_terracotta"] = ["minecraft:" + color + "_terracotta",
        ["minecraft:stained_hardened_clay", { color: color === "light_gray" ? "silver" : color }]];
    ALIASES[color + "_concrete"] = ["minecraft:" + color + "_concrete", ["minecraft:concrete", { color: color === "light_gray" ? "silver" : color }]];
    ALIASES[color + "_carpet"] = ["minecraft:" + color + "_carpet", ["minecraft:carpet", { color: color === "light_gray" ? "silver" : color }]];
    ALIASES[color + "_stained_glass_pane"] = ["minecraft:" + color + "_stained_glass_pane",
        ["minecraft:stained_glass_pane", { color: color === "light_gray" ? "silver" : color }]];
}

const STAIRS_DIR = { east: 0, west: 1, south: 2, north: 3 };
const DOOR_DIR = { east: 0, south: 1, west: 2, north: 3 };
const BED_DIR = { south: 0, west: 1, north: 2, east: 3 };
const FACING = { down: 0, up: 1, north: 2, south: 3, west: 4, east: 5 };
const CLOCKWISE = { north: "east", east: "south", south: "west", west: "north" };

/** Bedrock state spellings to try, most likely first. */
function stateVariants(name, s) {
    if (name.endsWith("_stairs")) {
        return [{ weirdo_direction: STAIRS_DIR[s?.dir ?? "north"], upside_down_bit: false }];
    }
    if (name.endsWith("_door")) {
        const common = { upper_block_bit: !!s?.upper, door_hinge_bit: false, open_bit: false };
        return [{ ...common, direction: DOOR_DIR[s?.dir ?? "south"] },
            { ...common, "minecraft:cardinal_direction": s?.dir ?? "south" }];
    }
    if (name.endsWith("_trapdoor")) {
        return [{ direction: STAIRS_DIR[s?.dir ?? "north"], open_bit: !!s?.open, upside_down_bit: false }];
    }
    if (name === "bed") {
        return [{ direction: BED_DIR[s?.dir ?? "north"], head_piece_bit: !!s?.head, occupied_bit: false }];
    }
    if (name.endsWith("_slab")) {
        return [{ "minecraft:vertical_half": "bottom" }, { top_slot_bit: false }];
    }
    if (s?.axis) {
        return [{ pillar_axis: s.axis }];
    }
    if (name === "ladder") {
        return [{ facing_direction: FACING[s?.dir ?? "south"] }];
    }
    if (name === "lantern") {
        return [{ hanging: !!s?.hanging }];
    }
    if (name === "bell") {
        return [{ attachment: "standing", direction: 0, toggle_bit: false }];
    }
    if (name === "farmland") {
        return [{ moisturized_amount: 7 }];
    }
    if (["wheat", "carrots", "potatoes", "beetroot"].includes(name)) {
        return [{ growth: 7 }];
    }
    if (name === "lightning_rod") {
        return [{ facing_direction: 1 }];
    }
    if (name === "chain") {
        return [{ pillar_axis: "y" }];
    }
    if (s?.dir) {
        return [{ "minecraft:cardinal_direction": s.dir }, { facing_direction: FACING[s.dir] }, { direction: DOOR_DIR[s.dir] }];
    }
    return [];
}

const cache = new Map();
const missing = new Set();

/** The permutation for a neutral block, or undefined if this game version knows no such block. */
export function permutation(block) {
    const key = block.b + JSON.stringify(block.s ?? {});
    if (cache.has(key)) {
        return cache.get(key);
    }
    const candidates = ALIASES[block.b] ?? ["minecraft:" + block.b];
    let result;
    outer:
    for (const variant of [...stateVariants(block.b, block.s), null]) {
        for (const candidate of candidates) {
            const [id, base] = Array.isArray(candidate) ? candidate : [candidate, {}];
            try {
                result = BlockPermutation.resolve(id, variant ? { ...base, ...variant } : base);
                break outer;
            } catch {
                // try the next spelling
            }
        }
    }
    if (!result && !missing.has(block.b)) {
        missing.add(block.b);
        console.warn("[cities] unknown block " + block.b);
    }
    cache.set(key, result);
    return result;
}

/** Turns a direction-carrying block by {@code turns} quarter turns clockwise. */
export function rotateBlock(block, turns) {
    if (!block.s || turns % 4 === 0) {
        return block;
    }
    const s = { ...block.s };
    if (s.dir) {
        for (let i = 0; i < turns; i++) {
            s.dir = CLOCKWISE[s.dir];
        }
    }
    if (s.axis && s.axis !== "y" && turns % 2 === 1) {
        s.axis = s.axis === "x" ? "z" : "x";
    }
    return { b: block.b, s };
}

export function rotateDir(dir, turns) {
    let d = dir;
    for (let i = 0; i < turns; i++) {
        d = CLOCKWISE[d];
    }
    return d;
}

// ------------------------------------------------------------------ what the ground is made of

const NATURAL = ["dirt", "grass", "sand", "gravel", "stone", "deepslate", "granite", "diorite", "andesite", "tuff",
    "hardened_clay", "terracotta", "snow", "podzol", "mycelium", "moss_block", "mud", "clay", "calcite", "ice"];
const VEGETATION = ["leaves", "flower", "tallgrass", "short_grass", "tall_grass", "fern", "sapling", "vine", "bush",
    "dandelion", "poppy", "orchid", "allium", "azure", "tulip", "daisy", "cornflower", "lily", "rose", "peony",
    "lilac", "sunflower", "snow_layer", "mushroom", "bamboo", "reeds", "sugar_cane", "cactus", "bee_nest", "cocoa",
    "moss_carpet", "pumpkin", "melon_block", "sweet_berry", "azalea", "pink_petals", "deadbush", "dead_bush", "seagrass"];

export function isAir(id) {
    return id === "minecraft:air" || id === "minecraft:cave_air" || id === "minecraft:void_air";
}

export function isLeaves(id) {
    return id.includes("leaves");
}

export function isLog(id) {
    return id.endsWith("_log") || id === "minecraft:log" || id === "minecraft:log2" || id.endsWith("_stem")
        || id.endsWith("_wood");
}

export function isVegetation(id) {
    return isAir(id) || isLog(id) || VEGETATION.some((word) => id.includes(word));
}

export function isWater(id) {
    return id.includes("water");
}

/** Untouched natural ground: no planks, roads, fields or buildings. */
export function isNaturalGround(id) {
    if (id.includes("path") || id.includes("farmland") || id.includes("brick") || id.includes("polished")
        || id.includes("smooth") || id.includes("cobble") || id.includes("planks") || id.includes("concrete")
        || id.includes("sandstone") || id.includes("wool") || id.includes("glass")) {
        return false;
    }
    return NATURAL.some((word) => id.includes(word));
}
