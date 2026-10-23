package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.research.Era;
import com.wusternfuchs.cities.settlement.ArchitectureStyle;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Building materials and shapes of one culture in one architectural period: the German half-timbered house,
 * the Arab courtyard house with a flat roof, the Russian log izba and so on.
 *
 * @param walls      wall infill
 * @param lower      ground-floor walls (a stone plinth in later periods)
 * @param frame      corner posts, beams and pilasters
 * @param roofStairs null for thatch, which is laid in full blocks
 * @param trim       parapets, cornices, chimneys
 * @param shutter    trapdoor hung beside windows, or null
 * @param accent     domes and other colourful details
 * @param chimney    houses get a brick chimney with a smoking hearth
 * @param poles      wooden poles stick out of the parapets (Djenné mud architecture)
 * @param floors     storeys of an ordinary house
 */
public record Palette(
        ArchitectureStyle style,
        Period period,
        BlockState walls,
        BlockState lower,
        BlockState frame,
        BlockState floor,
        BlockState foundation,
        Block wallStairs,
        @Nullable Block roofStairs,
        BlockState roofSlab,
        BlockState roofBlock,
        BlockState trim,
        BlockState fence,
        Block door,
        Block bed,
        BlockState pane,
        @Nullable Block shutter,
        BlockState light,
        BlockState accent,
        BlockState road,
        Roof roof,
        boolean halfTimber,
        boolean logWalls,
        boolean chimney,
        boolean poles,
        int floors) {

    public enum Roof {
        GABLE, STEEP, FLAT, HIP, PAGODA, STEPPED
    }

    /** Kept for code that thinks of posts as pillars. */
    public BlockState pillar() {
        return frame;
    }

    public static Palette of(ArchitectureStyle style) {
        return of(style, Era.FOUNDING, false);
    }

    /** {@code masonry}: early buildings of a town that knows masonry stand on a stone ground floor. */
    public static Palette of(ArchitectureStyle style, Era era, boolean masonry) {
        Period period = Period.of(era);
        Spec spec = spec(style, period);
        if (masonry && period == Period.EARLY && spec.lower == spec.walls) {
            spec.lower = spec.foundation;
        }
        return spec.build(style, period);
    }

    // ------------------------------------------------------------------ cultures

    private static Spec spec(ArchitectureStyle style, Period period) {
        return switch (style) {
            case PLAINS -> german(period);
            case DESERT -> arabic(period);
            case TAIGA -> russian(period);
            case SNOWY -> nordic(period);
            case SAVANNA -> sahel(period);
            case OTHER -> british(period);
            case CHERRY -> japanese(period);
            case JUNGLE -> maya(period);
        };
    }

    /** Thatched birch cottages, then half-timbering under red tiles, stone guild houses, brick, white concrete. */
    private static Spec german(Period period) {
        Spec s = new Spec();
        s.bed = Blocks.RED_BED;
        s.shutter = Blocks.SPRUCE_TRAPDOOR;
        s.chimney = true;
        s.accent = Blocks.RED_TERRACOTTA.defaultBlockState();
        switch (period) {
            case EARLY -> {
                s.walls(Blocks.BIRCH_PLANKS).frame(Blocks.OAK_LOG).thatch().roof = Roof.STEEP;
                s.floors = 1;
            }
            case MEDIEVAL -> {
                s.walls(Blocks.WHITE_TERRACOTTA).frame(Blocks.OAK_LOG).lower(Blocks.COBBLESTONE)
                        .roof(Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.BRICKS).roof = Roof.STEEP;
                s.halfTimber = true;
                s.floors = 2;
            }
            case RENAISSANCE -> {
                s.walls(Blocks.WHITE_TERRACOTTA).frame(Blocks.STRIPPED_OAK_LOG).lower(Blocks.STONE_BRICKS)
                        .roof(Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.BRICKS).roof = Roof.STEEP;
                s.halfTimber = true;
                s.trim = Blocks.POLISHED_ANDESITE.defaultBlockState();
                s.road = Blocks.STONE_BRICKS.defaultBlockState();
                s.floors = 2;
            }
            case INDUSTRIAL -> {
                s.walls(Blocks.BRICKS).frame(Blocks.STONE_BRICKS).lower(Blocks.STONE_BRICKS)
                        .roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES).roof = Roof.STEEP;
                s.trim = Blocks.SMOOTH_STONE.defaultBlockState();
                s.road = Blocks.POLISHED_ANDESITE.defaultBlockState();
                s.door = Blocks.SPRUCE_DOOR;
                s.floors = 3;
            }
            case MODERN -> modern(s, Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, 4);
        }
        return s;
    }

    /** Mud-brick houses with flat roofs and parapets, sandstone with blue domes, white stone, modern white. */
    private static Spec arabic(Period period) {
        Spec s = new Spec();
        s.roof = Roof.FLAT;
        s.bed = Blocks.CYAN_BED;
        s.door = Blocks.JUNGLE_DOOR;
        s.fence = Blocks.JUNGLE_FENCE.defaultBlockState();
        s.shutter = Blocks.JUNGLE_TRAPDOOR;
        s.wallStairs = Blocks.SMOOTH_SANDSTONE_STAIRS;
        s.floor = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        s.foundation = Blocks.SANDSTONE.defaultBlockState();
        s.road = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        s.accent = Blocks.CYAN_TERRACOTTA.defaultBlockState();
        switch (period) {
            case EARLY -> {
                s.walls(Blocks.MUD_BRICKS).frame(Blocks.PACKED_MUD)
                        .roof(Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.PACKED_MUD);
                s.trim = Blocks.MUD_BRICK_WALL.defaultBlockState();
                s.wallStairs = Blocks.MUD_BRICK_STAIRS;
                s.floors = 1;
            }
            case MEDIEVAL -> {
                s.walls(Blocks.SMOOTH_SANDSTONE).frame(Blocks.CUT_SANDSTONE).lower(Blocks.SANDSTONE)
                        .roof(Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE);
                s.trim = Blocks.SANDSTONE_WALL.defaultBlockState();
                s.accent = Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState();
                s.floors = 2;
            }
            case RENAISSANCE -> {
                s.walls(Blocks.SMOOTH_SANDSTONE).frame(Blocks.CHISELED_SANDSTONE).lower(Blocks.CUT_SANDSTONE)
                        .roof(Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE);
                s.trim = Blocks.CUT_SANDSTONE.defaultBlockState();
                s.accent = Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState();
                s.floors = 2;
            }
            case INDUSTRIAL -> {
                s.walls(Blocks.TERRACOTTA).frame(Blocks.CUT_SANDSTONE).lower(Blocks.SANDSTONE)
                        .roof(Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE);
                s.trim = Blocks.CUT_SANDSTONE.defaultBlockState();
                s.floors = 3;
            }
            case MODERN -> {
                modern(s, Blocks.WHITE_CONCRETE, Blocks.SMOOTH_SANDSTONE, 4);
                s.accent = Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState();
            }
        }
        return s;
    }

    /** Log izbas with white carved window frames, white stone and onion domes, baroque, brick, panel blocks. */
    private static Spec russian(Period period) {
        Spec s = new Spec();
        s.bed = Blocks.RED_BED;
        s.door = Blocks.SPRUCE_DOOR;
        s.fence = Blocks.SPRUCE_FENCE.defaultBlockState();
        s.shutter = Blocks.BIRCH_TRAPDOOR;
        s.wallStairs = Blocks.SPRUCE_STAIRS;
        s.floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        s.chimney = true;
        switch (period) {
            case EARLY -> {
                s.walls(Blocks.SPRUCE_LOG).frame(Blocks.SPRUCE_LOG)
                        .roof(Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS).roof = Roof.STEEP;
                s.logWalls = true;
                s.accent = Blocks.SPRUCE_PLANKS.defaultBlockState();
                s.floors = 1;
            }
            case MEDIEVAL -> {
                s.walls(Blocks.SPRUCE_LOG).frame(Blocks.SPRUCE_LOG).lower(Blocks.POLISHED_DIORITE)
                        .roof(Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS).roof = Roof.STEEP;
                s.logWalls = true;
                s.accent = Blocks.BLUE_TERRACOTTA.defaultBlockState();
                s.floors = 2;
            }
            case RENAISSANCE -> {
                s.walls(Blocks.YELLOW_TERRACOTTA).frame(Blocks.POLISHED_DIORITE).lower(Blocks.POLISHED_DIORITE)
                        .roof(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB,
                                Blocks.WAXED_OXIDIZED_CUT_COPPER).roof = Roof.GABLE;
                s.trim = Blocks.POLISHED_DIORITE.defaultBlockState();
                s.accent = Blocks.HONEYCOMB_BLOCK.defaultBlockState();
                s.road = Blocks.STONE_BRICKS.defaultBlockState();
                s.floors = 2;
            }
            case INDUSTRIAL -> {
                s.walls(Blocks.BRICKS).frame(Blocks.POLISHED_DIORITE).lower(Blocks.STONE_BRICKS)
                        .roof(Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB,
                                Blocks.WAXED_WEATHERED_CUT_COPPER).roof = Roof.GABLE;
                s.trim = Blocks.POLISHED_DIORITE.defaultBlockState();
                s.accent = Blocks.HONEYCOMB_BLOCK.defaultBlockState();
                s.road = Blocks.POLISHED_ANDESITE.defaultBlockState();
                s.floors = 3;
            }
            case MODERN -> {
                modern(s, Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, 5);
                s.accent = Blocks.HONEYCOMB_BLOCK.defaultBlockState();
            }
        }
        return s;
    }

    /** Dark timber longhouses, stave churches, Falu-red houses with white corners, stone, black modern gables. */
    private static Spec nordic(Period period) {
        Spec s = new Spec();
        s.bed = Blocks.BLUE_BED;
        s.door = Blocks.SPRUCE_DOOR;
        s.fence = Blocks.SPRUCE_FENCE.defaultBlockState();
        s.shutter = Blocks.SPRUCE_TRAPDOOR;
        s.wallStairs = Blocks.SPRUCE_STAIRS;
        s.floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        s.roof = Roof.STEEP;
        s.chimney = true;
        s.accent = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        switch (period) {
            case EARLY -> {
                s.walls(Blocks.SPRUCE_PLANKS).frame(Blocks.STRIPPED_SPRUCE_LOG)
                        .roof(Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS);
                s.floors = 1;
            }
            case MEDIEVAL -> {
                s.walls(Blocks.DARK_OAK_PLANKS).frame(Blocks.DARK_OAK_LOG).lower(Blocks.COBBLESTONE)
                        .roof(Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS);
                s.floors = 2;
            }
            case RENAISSANCE -> {
                s.walls(Blocks.RED_TERRACOTTA).frame(Blocks.WHITE_CONCRETE).lower(Blocks.STONE_BRICKS)
                        .roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES);
                s.shutter = Blocks.BIRCH_TRAPDOOR;
                s.road = Blocks.STONE_BRICKS.defaultBlockState();
                s.floors = 2;
            }
            case INDUSTRIAL -> {
                s.walls(Blocks.BRICKS).frame(Blocks.STONE_BRICKS).lower(Blocks.STONE_BRICKS)
                        .roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES);
                s.road = Blocks.POLISHED_ANDESITE.defaultBlockState();
                s.floors = 3;
            }
            case MODERN -> {
                modern(s, Blocks.WHITE_CONCRETE, Blocks.STRIPPED_SPRUCE_LOG, 3);
                s.roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES).roof = Roof.STEEP;
            }
        }
        return s;
    }

    /** Clay huts under thatch, Djenné-style mud with poles, white coral-stone, copper roofs, bright concrete. */
    private static Spec sahel(Period period) {
        Spec s = new Spec();
        s.bed = Blocks.ORANGE_BED;
        s.door = Blocks.ACACIA_DOOR;
        s.fence = Blocks.ACACIA_FENCE.defaultBlockState();
        s.shutter = Blocks.ACACIA_TRAPDOOR;
        s.wallStairs = Blocks.ACACIA_STAIRS;
        s.floor = Blocks.ACACIA_PLANKS.defaultBlockState();
        s.foundation = Blocks.PACKED_MUD.defaultBlockState();
        s.accent = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        switch (period) {
            case EARLY -> {
                s.walls(Blocks.TERRACOTTA).frame(Blocks.ACACIA_LOG).thatch().roof = Roof.HIP;
                s.floors = 1;
            }
            case MEDIEVAL -> {
                s.walls(Blocks.PACKED_MUD).frame(Blocks.MUD_BRICKS)
                        .roof(Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.PACKED_MUD).roof = Roof.FLAT;
                s.trim = Blocks.MUD_BRICKS.defaultBlockState();
                s.poles = true;
                s.floors = 2;
            }
            case RENAISSANCE -> {
                s.walls(Blocks.CALCITE).frame(Blocks.ACACIA_LOG).lower(Blocks.CALCITE)
                        .roof(Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE).roof = Roof.FLAT;
                s.trim = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                s.road = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                s.floors = 2;
            }
            case INDUSTRIAL -> {
                s.walls(Blocks.ORANGE_TERRACOTTA).frame(Blocks.BRICKS).lower(Blocks.BRICKS)
                        .roof(Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB,
                                Blocks.WAXED_EXPOSED_CUT_COPPER).roof = Roof.HIP;
                s.floors = 2;
            }
            case MODERN -> {
                modern(s, Blocks.WHITE_CONCRETE, Blocks.ORANGE_CONCRETE, 4);
                s.accent = Blocks.YELLOW_CONCRETE.defaultBlockState();
            }
        }
        return s;
    }

    /** Thatched stone cottages, Tudor black-and-white, Georgian stone, Victorian brick, glass and concrete. */
    private static Spec british(Period period) {
        Spec s = new Spec();
        s.bed = Blocks.WHITE_BED;
        s.chimney = true;
        s.accent = Blocks.GRAY_TERRACOTTA.defaultBlockState();
        switch (period) {
            case EARLY -> {
                s.walls(Blocks.COBBLESTONE).frame(Blocks.OAK_LOG).thatch().roof = Roof.STEEP;
                s.floors = 1;
            }
            case MEDIEVAL -> {
                s.walls(Blocks.CALCITE).frame(Blocks.DARK_OAK_LOG).lower(Blocks.COBBLESTONE)
                        .roof(Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS).roof = Roof.STEEP;
                s.halfTimber = true;
                s.door = Blocks.DARK_OAK_DOOR;
                s.floors = 2;
            }
            case RENAISSANCE -> {
                s.walls(Blocks.STONE_BRICKS).frame(Blocks.SMOOTH_STONE).lower(Blocks.STONE_BRICKS)
                        .roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES).roof = Roof.GABLE;
                s.trim = Blocks.SMOOTH_STONE.defaultBlockState();
                s.door = Blocks.DARK_OAK_DOOR;
                s.road = Blocks.STONE_BRICKS.defaultBlockState();
                s.floors = 3;
            }
            case INDUSTRIAL -> {
                s.walls(Blocks.BRICKS).frame(Blocks.STONE_BRICKS).lower(Blocks.STONE_BRICKS)
                        .roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES).roof = Roof.GABLE;
                s.trim = Blocks.STONE_BRICKS.defaultBlockState();
                s.door = Blocks.DARK_OAK_DOOR;
                s.road = Blocks.POLISHED_ANDESITE.defaultBlockState();
                s.floors = 3;
            }
            case MODERN -> modern(s, Blocks.LIGHT_GRAY_CONCRETE, Blocks.WHITE_CONCRETE, 5);
        }
        return s;
    }

    /** Thatched minka, white plaster and dark beams under curved tile roofs, Meiji brick, Tokyo concrete. */
    private static Spec japanese(Period period) {
        Spec s = new Spec();
        s.bed = Blocks.WHITE_BED;
        s.door = Blocks.BAMBOO_DOOR;
        s.fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
        s.wallStairs = Blocks.DARK_OAK_STAIRS;
        s.floor = Blocks.BAMBOO_MOSAIC.defaultBlockState();
        s.foundation = Blocks.STONE_BRICKS.defaultBlockState();
        s.pane = Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState();
        s.shutter = null;
        s.halfTimber = true;
        s.accent = Blocks.RED_TERRACOTTA.defaultBlockState();
        s.roof = Roof.PAGODA;
        switch (period) {
            case EARLY -> {
                s.walls(Blocks.BIRCH_PLANKS).frame(Blocks.DARK_OAK_LOG).thatch().roof = Roof.HIP;
                s.floors = 1;
            }
            case MEDIEVAL -> {
                s.walls(Blocks.WHITE_TERRACOTTA).frame(Blocks.DARK_OAK_LOG).lower(Blocks.STONE_BRICKS)
                        .roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES);
                s.floors = 2;
            }
            case RENAISSANCE -> {
                s.walls(Blocks.WHITE_CONCRETE).frame(Blocks.STRIPPED_DARK_OAK_LOG).lower(Blocks.STONE_BRICKS)
                        .roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES);
                s.floors = 2;
            }
            case INDUSTRIAL -> {
                s.walls(Blocks.BRICKS).frame(Blocks.STONE_BRICKS).lower(Blocks.STONE_BRICKS)
                        .roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES);
                s.halfTimber = false;
                s.pane = Blocks.GLASS_PANE.defaultBlockState();
                s.floors = 3;
            }
            case MODERN -> modern(s, Blocks.LIGHT_GRAY_CONCRETE, Blocks.WHITE_CONCRETE, 5);
        }
        return s;
    }

    /** Jungle-wood huts, stepped stone of the classic cities, tuff temples, painted colonial walls, concrete. */
    private static Spec maya(Period period) {
        Spec s = new Spec();
        s.bed = Blocks.GREEN_BED;
        s.door = Blocks.JUNGLE_DOOR;
        s.fence = Blocks.JUNGLE_FENCE.defaultBlockState();
        s.shutter = Blocks.JUNGLE_TRAPDOOR;
        s.wallStairs = Blocks.JUNGLE_STAIRS;
        s.floor = Blocks.JUNGLE_PLANKS.defaultBlockState();
        s.foundation = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        s.accent = Blocks.RED_TERRACOTTA.defaultBlockState();
        s.roof = Roof.STEPPED;
        switch (period) {
            case EARLY -> {
                s.walls(Blocks.JUNGLE_PLANKS).frame(Blocks.JUNGLE_LOG).thatch().roof = Roof.HIP;
                s.floors = 1;
            }
            case MEDIEVAL -> {
                s.walls(Blocks.STONE_BRICKS).frame(Blocks.CHISELED_STONE_BRICKS).lower(Blocks.MOSSY_STONE_BRICKS)
                        .roof(Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICKS);
                s.trim = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                s.floor = Blocks.STONE_BRICKS.defaultBlockState();
                s.floors = 1;
            }
            case RENAISSANCE -> {
                s.walls(Blocks.TUFF_BRICKS).frame(Blocks.CHISELED_TUFF).lower(Blocks.TUFF_BRICKS)
                        .roof(Blocks.TUFF_BRICK_STAIRS, Blocks.TUFF_BRICK_SLAB, Blocks.TUFF_BRICKS);
                s.trim = Blocks.TUFF_BRICK_WALL.defaultBlockState();
                s.road = Blocks.TUFF_BRICKS.defaultBlockState();
                s.floors = 2;
            }
            case INDUSTRIAL -> {
                s.walls(Blocks.YELLOW_TERRACOTTA).frame(Blocks.STONE_BRICKS).lower(Blocks.STONE_BRICKS)
                        .roof(Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICKS).roof = Roof.FLAT;
                s.trim = Blocks.STONE_BRICKS.defaultBlockState();
                s.road = Blocks.POLISHED_ANDESITE.defaultBlockState();
                s.floors = 3;
            }
            case MODERN -> modern(s, Blocks.WHITE_CONCRETE, Blocks.LIME_CONCRETE, 4);
        }
        return s;
    }

    /** Flat-roofed concrete blocks with big windows; every culture ends up here, in its own colours. */
    private static void modern(Spec s, Block walls, Block frame, int floors) {
        s.walls(walls).frame(frame).lower(frame)
                .roof(Blocks.SMOOTH_STONE_SLAB, Blocks.SMOOTH_STONE_SLAB, Blocks.GRAY_CONCRETE).roof = Roof.FLAT;
        s.roofStairs = null;
        s.trim = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        s.floor = Blocks.SMOOTH_STONE.defaultBlockState();
        s.foundation = Blocks.GRAY_CONCRETE.defaultBlockState();
        s.pane = Blocks.GLASS_PANE.defaultBlockState();
        s.shutter = null;
        s.halfTimber = false;
        s.logWalls = false;
        s.poles = false;
        s.chimney = false;
        s.light = Blocks.SEA_LANTERN.defaultBlockState();
        s.road = Blocks.GRAY_CONCRETE.defaultBlockState();
        s.door = Blocks.BIRCH_DOOR;
        s.floors = floors;
    }

    /** Mutable defaults that each culture and period overrides. */
    private static final class Spec {
        BlockState walls = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState lower = walls;
        BlockState frame = Blocks.OAK_LOG.defaultBlockState();
        BlockState floor = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState foundation = Blocks.COBBLESTONE.defaultBlockState();
        Block wallStairs = Blocks.OAK_STAIRS;
        Block roofStairs = Blocks.OAK_STAIRS;
        BlockState roofSlab = Blocks.OAK_SLAB.defaultBlockState();
        BlockState roofBlock = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState trim = Blocks.COBBLESTONE.defaultBlockState();
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        Block door = Blocks.OAK_DOOR;
        Block bed = Blocks.RED_BED;
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        Block shutter;
        BlockState light = Blocks.LANTERN.defaultBlockState();
        BlockState accent = Blocks.RED_WOOL.defaultBlockState();
        BlockState road = Blocks.COBBLESTONE.defaultBlockState();
        Roof roof = Roof.GABLE;
        boolean halfTimber;
        boolean logWalls;
        boolean chimney;
        boolean poles;
        int floors = 1;

        Spec walls(Block block) {
            boolean same = lower == walls;
            walls = block.defaultBlockState();
            if (same) {
                lower = walls;
            }
            return this;
        }

        Spec lower(Block block) {
            lower = block.defaultBlockState();
            return this;
        }

        Spec frame(Block block) {
            frame = block.defaultBlockState();
            return this;
        }

        Spec roof(Block stairs, Block slab, Block full) {
            roofStairs = stairs;
            roofSlab = slab.defaultBlockState();
            roofBlock = full.defaultBlockState();
            return this;
        }

        /** Straw: laid as full blocks. */
        Spec thatch() {
            roofStairs = null;
            roofSlab = Blocks.HAY_BLOCK.defaultBlockState();
            roofBlock = Blocks.HAY_BLOCK.defaultBlockState();
            return this;
        }

        Palette build(ArchitectureStyle style, Period period) {
            return new Palette(style, period, walls, lower, frame, floor, foundation, wallStairs, roofStairs, roofSlab,
                    roofBlock, trim, fence, door, bed, pane, shutter, light, accent, road, roof, halfTimber, logWalls,
                    chimney, poles, floors);
        }
    }
}
