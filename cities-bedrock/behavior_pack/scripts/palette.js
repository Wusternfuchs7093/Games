// Building materials and shapes of each culture in each architectural period.
// Block names are Java-style ids without the namespace; blocks.js turns them into Bedrock blocks.
// Pure data and logic: no Minecraft imports, so it can be tested with plain Node.

export const CULTURES = ["plains", "desert", "savanna", "snowy", "taiga", "other", "cherry", "jungle"];
export const PERIODS = ["early", "medieval", "renaissance", "industrial", "modern"];
export const ERAS = ["founding", "iron", "medieval", "renaissance", "industrial", "modern"];

/** Architectural period of an era: the first two eras share one. */
export function periodOf(era) {
    switch (era) {
        case "medieval": return "medieval";
        case "renaissance": return "renaissance";
        case "industrial": return "industrial";
        case "modern": return "modern";
        default: return "early";
    }
}

function base() {
    return {
        walls: "oak_planks", lower: null, frame: "oak_log", floor: "oak_planks", foundation: "cobblestone",
        wallStairs: "oak_stairs", roofStairs: "oak_stairs", roofSlab: "oak_slab", roofBlock: "oak_planks",
        trim: "cobblestone", fence: "oak_fence", door: "oak_door", shutter: null, pane: "glass_pane",
        light: "lantern", accent: "red_wool", road: "cobblestone", roof: "gable",
        halfTimber: false, logWalls: false, chimney: false, poles: false, floors: 1,
    };
}

function roofOf(s, stairs, slab, full) {
    s.roofStairs = stairs;
    s.roofSlab = slab;
    s.roofBlock = full;
}

function thatch(s) {
    s.roofStairs = null;
    s.roofSlab = "hay_block";
    s.roofBlock = "hay_block";
}

function modern(s, walls, frame, floors) {
    Object.assign(s, {
        walls, frame, lower: frame, roofStairs: null, roofSlab: "smooth_stone_slab", roofBlock: "gray_concrete",
        trim: "smooth_stone_slab", floor: "smooth_stone", foundation: "gray_concrete", pane: "glass_pane",
        shutter: null, halfTimber: false, logWalls: false, poles: false, chimney: false, light: "sea_lantern",
        road: "gray_concrete", door: "birch_door", roof: "flat", floors,
    });
}

function german(p) {
    const s = base();
    Object.assign(s, { shutter: "spruce_trapdoor", chimney: true, accent: "red_terracotta" });
    if (p === "early") {
        Object.assign(s, { walls: "birch_planks", frame: "oak_log", roof: "gable", floors: 1 });
        thatch(s);
    } else if (p === "medieval") {
        Object.assign(s, { walls: "white_terracotta", frame: "oak_log", lower: "cobblestone", roof: "steep", halfTimber: true, floors: 2 });
        roofOf(s, "brick_stairs", "brick_slab", "bricks");
    } else if (p === "renaissance") {
        Object.assign(s, { walls: "white_terracotta", frame: "stripped_oak_log", lower: "stone_bricks", roof: "steep",
            halfTimber: true, trim: "polished_andesite", road: "stone_bricks", floors: 2 });
        roofOf(s, "brick_stairs", "brick_slab", "bricks");
    } else if (p === "industrial") {
        Object.assign(s, { walls: "bricks", frame: "stone_bricks", lower: "stone_bricks", roof: "steep",
            trim: "smooth_stone", road: "polished_andesite", door: "spruce_door", floors: 3 });
        roofOf(s, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
    } else {
        modern(s, "white_concrete", "light_gray_concrete", 4);
    }
    return s;
}

function arabic(p) {
    const s = base();
    Object.assign(s, { roof: "flat", door: "jungle_door", fence: "jungle_fence", shutter: "jungle_trapdoor",
        wallStairs: "smooth_sandstone_stairs", floor: "smooth_sandstone", foundation: "sandstone",
        road: "smooth_sandstone", accent: "cyan_terracotta" });
    if (p === "early") {
        Object.assign(s, { walls: "mud_bricks", frame: "packed_mud", trim: "mud_brick_wall", wallStairs: "mud_brick_stairs", floors: 1 });
        roofOf(s, "mud_brick_stairs", "mud_brick_slab", "packed_mud");
    } else if (p === "medieval") {
        Object.assign(s, { walls: "smooth_sandstone", frame: "cut_sandstone", lower: "sandstone", trim: "sandstone_wall",
            accent: "light_blue_terracotta", floors: 2 });
        roofOf(s, "smooth_sandstone_stairs", "smooth_sandstone_slab", "smooth_sandstone");
    } else if (p === "renaissance") {
        Object.assign(s, { walls: "smooth_sandstone", frame: "chiseled_sandstone", lower: "cut_sandstone", trim: "cut_sandstone",
            accent: "light_blue_terracotta", floors: 2 });
        roofOf(s, "smooth_sandstone_stairs", "smooth_sandstone_slab", "smooth_sandstone");
    } else if (p === "industrial") {
        Object.assign(s, { walls: "terracotta", frame: "cut_sandstone", lower: "sandstone", trim: "cut_sandstone", floors: 3 });
        roofOf(s, "smooth_sandstone_stairs", "smooth_sandstone_slab", "smooth_sandstone");
    } else {
        modern(s, "white_concrete", "smooth_sandstone", 4);
        s.accent = "light_blue_terracotta";
    }
    return s;
}

function russian(p) {
    const s = base();
    Object.assign(s, { door: "spruce_door", fence: "spruce_fence", shutter: "birch_trapdoor", wallStairs: "spruce_stairs",
        floor: "spruce_planks", chimney: true });
    if (p === "early") {
        Object.assign(s, { walls: "spruce_log", frame: "spruce_log", roof: "steep", logWalls: true, accent: "spruce_planks", floors: 1 });
        roofOf(s, "spruce_stairs", "spruce_slab", "spruce_planks");
    } else if (p === "medieval") {
        Object.assign(s, { walls: "spruce_log", frame: "spruce_log", lower: "polished_diorite", roof: "steep", logWalls: true,
            accent: "blue_terracotta", floors: 2 });
        roofOf(s, "spruce_stairs", "spruce_slab", "spruce_planks");
    } else if (p === "renaissance") {
        Object.assign(s, { walls: "yellow_terracotta", frame: "polished_diorite", lower: "polished_diorite", roof: "gable",
            trim: "polished_diorite", accent: "honeycomb_block", road: "stone_bricks", floors: 2 });
        roofOf(s, "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab", "waxed_oxidized_cut_copper");
    } else if (p === "industrial") {
        Object.assign(s, { walls: "bricks", frame: "polished_diorite", lower: "stone_bricks", roof: "gable",
            trim: "polished_diorite", accent: "honeycomb_block", road: "polished_andesite", floors: 3 });
        roofOf(s, "waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper_slab", "waxed_weathered_cut_copper");
    } else {
        modern(s, "white_concrete", "light_gray_concrete", 5);
        s.accent = "honeycomb_block";
    }
    return s;
}

function nordic(p) {
    const s = base();
    Object.assign(s, { door: "spruce_door", fence: "spruce_fence", shutter: "spruce_trapdoor", wallStairs: "spruce_stairs",
        floor: "spruce_planks", roof: "steep", chimney: true, accent: "dark_oak_planks" });
    if (p === "early") {
        Object.assign(s, { walls: "spruce_planks", frame: "stripped_spruce_log", floors: 1 });
        roofOf(s, "dark_oak_stairs", "dark_oak_slab", "dark_oak_planks");
    } else if (p === "medieval") {
        Object.assign(s, { walls: "dark_oak_planks", frame: "dark_oak_log", lower: "cobblestone", floors: 2 });
        roofOf(s, "dark_oak_stairs", "dark_oak_slab", "dark_oak_planks");
    } else if (p === "renaissance") {
        Object.assign(s, { walls: "red_terracotta", frame: "white_concrete", lower: "stone_bricks", shutter: "birch_trapdoor",
            road: "stone_bricks", floors: 2 });
        roofOf(s, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
    } else if (p === "industrial") {
        Object.assign(s, { walls: "bricks", frame: "stone_bricks", lower: "stone_bricks", road: "polished_andesite", floors: 3 });
        roofOf(s, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
    } else {
        modern(s, "white_concrete", "stripped_spruce_log", 3);
        roofOf(s, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
        s.roof = "steep";
    }
    return s;
}

function sahel(p) {
    const s = base();
    Object.assign(s, { door: "acacia_door", fence: "acacia_fence", shutter: "acacia_trapdoor", wallStairs: "acacia_stairs",
        floor: "acacia_planks", foundation: "packed_mud", accent: "orange_terracotta" });
    if (p === "early") {
        Object.assign(s, { walls: "terracotta", frame: "acacia_log", roof: "hip", floors: 1 });
        thatch(s);
    } else if (p === "medieval") {
        Object.assign(s, { walls: "packed_mud", frame: "mud_bricks", roof: "flat", trim: "mud_bricks", poles: true, floors: 2 });
        roofOf(s, "mud_brick_stairs", "mud_brick_slab", "packed_mud");
    } else if (p === "renaissance") {
        Object.assign(s, { walls: "calcite", frame: "acacia_log", lower: "calcite", roof: "flat", trim: "smooth_sandstone",
            road: "smooth_sandstone", floors: 2 });
        roofOf(s, "smooth_sandstone_stairs", "smooth_sandstone_slab", "smooth_sandstone");
    } else if (p === "industrial") {
        Object.assign(s, { walls: "orange_terracotta", frame: "bricks", lower: "bricks", roof: "hip", floors: 2 });
        roofOf(s, "waxed_exposed_cut_copper_stairs", "waxed_exposed_cut_copper_slab", "waxed_exposed_cut_copper");
    } else {
        modern(s, "white_concrete", "orange_concrete", 4);
        s.accent = "yellow_concrete";
    }
    return s;
}

function british(p) {
    const s = base();
    Object.assign(s, { chimney: true, accent: "gray_terracotta" });
    if (p === "early") {
        Object.assign(s, { walls: "cobblestone", frame: "oak_log", roof: "gable", floors: 1 });
        thatch(s);
    } else if (p === "medieval") {
        Object.assign(s, { walls: "calcite", frame: "dark_oak_log", lower: "cobblestone", roof: "steep", halfTimber: true,
            door: "dark_oak_door", floors: 2 });
        roofOf(s, "spruce_stairs", "spruce_slab", "spruce_planks");
    } else if (p === "renaissance") {
        Object.assign(s, { walls: "stone_bricks", frame: "smooth_stone", lower: "stone_bricks", trim: "smooth_stone",
            door: "dark_oak_door", road: "stone_bricks", floors: 3 });
        roofOf(s, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
    } else if (p === "industrial") {
        Object.assign(s, { walls: "bricks", frame: "stone_bricks", lower: "stone_bricks", trim: "stone_bricks",
            door: "dark_oak_door", road: "polished_andesite", floors: 3 });
        roofOf(s, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
    } else {
        modern(s, "light_gray_concrete", "white_concrete", 5);
    }
    return s;
}

function japanese(p) {
    const s = base();
    Object.assign(s, { door: "bamboo_door", fence: "dark_oak_fence", wallStairs: "dark_oak_stairs", floor: "bamboo_mosaic",
        foundation: "stone_bricks", pane: "white_stained_glass_pane", halfTimber: true, accent: "red_terracotta", roof: "pagoda" });
    if (p === "early") {
        Object.assign(s, { walls: "birch_planks", frame: "dark_oak_log", roof: "hip", floors: 1 });
        thatch(s);
    } else if (p === "medieval") {
        Object.assign(s, { walls: "white_terracotta", frame: "dark_oak_log", lower: "stone_bricks", floors: 2 });
        roofOf(s, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
    } else if (p === "renaissance") {
        Object.assign(s, { walls: "white_concrete", frame: "stripped_dark_oak_log", lower: "stone_bricks", floors: 2 });
        roofOf(s, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
    } else if (p === "industrial") {
        Object.assign(s, { walls: "bricks", frame: "stone_bricks", lower: "stone_bricks", halfTimber: false, pane: "glass_pane", floors: 3 });
        roofOf(s, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
    } else {
        modern(s, "light_gray_concrete", "white_concrete", 5);
    }
    return s;
}

function maya(p) {
    const s = base();
    Object.assign(s, { door: "jungle_door", fence: "jungle_fence", shutter: "jungle_trapdoor", wallStairs: "jungle_stairs",
        floor: "jungle_planks", foundation: "mossy_cobblestone", accent: "red_terracotta", roof: "stepped" });
    if (p === "early") {
        Object.assign(s, { walls: "jungle_planks", frame: "jungle_log", roof: "hip", floors: 1 });
        thatch(s);
    } else if (p === "medieval") {
        Object.assign(s, { walls: "stone_bricks", frame: "chiseled_stone_bricks", lower: "mossy_stone_bricks",
            trim: "mossy_stone_bricks", floor: "stone_bricks", floors: 1 });
        roofOf(s, "stone_brick_stairs", "stone_brick_slab", "stone_bricks");
    } else if (p === "renaissance") {
        Object.assign(s, { walls: "tuff_bricks", frame: "chiseled_tuff", lower: "tuff_bricks", trim: "tuff_brick_wall",
            road: "tuff_bricks", floors: 2 });
        roofOf(s, "tuff_brick_stairs", "tuff_brick_slab", "tuff_bricks");
    } else if (p === "industrial") {
        Object.assign(s, { walls: "yellow_terracotta", frame: "stone_bricks", lower: "stone_bricks", trim: "stone_bricks",
            road: "polished_andesite", roof: "flat", floors: 3 });
        roofOf(s, "stone_brick_stairs", "stone_brick_slab", "stone_bricks");
    } else {
        modern(s, "white_concrete", "lime_concrete", 4);
    }
    return s;
}

const BUILDERS = { plains: german, desert: arabic, taiga: russian, snowy: nordic, savanna: sahel, other: british,
    cherry: japanese, jungle: maya };

/** Palette of a culture in an era. {@code masonry}: early buildings stand on a stone ground floor. */
export function paletteOf(culture, era, masonry = false) {
    const period = periodOf(era);
    const s = (BUILDERS[culture] ?? british)(period);
    if (s.lower === null) {
        s.lower = s.walls;
    }
    if (masonry && period === "early" && s.lower === s.walls) {
        s.lower = s.foundation;
    }
    s.culture = culture;
    s.period = period;
    return s;
}
