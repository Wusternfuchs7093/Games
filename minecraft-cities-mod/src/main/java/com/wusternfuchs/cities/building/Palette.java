package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.settlement.ArchitectureStyle;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Building materials of one regional style. */
public record Palette(
        BlockState walls,
        BlockState pillar,
        BlockState floor,
        BlockState foundation,
        Block wallStairs,
        Block roofStairs,
        BlockState roofSlab,
        BlockState fence,
        Block door,
        Block bed,
        BlockState pane,
        BlockState road) {

    /** The masonry palette replaces wooden walls with the region's stone. */
    public static Palette of(ArchitectureStyle style, boolean stone) {
        Palette base = of(style);
        if (!stone) {
            return base;
        }
        BlockState walls = switch (style) {
            case PLAINS, SNOWY -> Blocks.STONE_BRICKS.defaultBlockState();
            case DESERT -> Blocks.CUT_SANDSTONE.defaultBlockState();
            case SAVANNA -> Blocks.TERRACOTTA.defaultBlockState();
            case TAIGA -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            case OTHER -> Blocks.BRICKS.defaultBlockState();
        };
        BlockState pillar = style == ArchitectureStyle.DESERT ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : base.pillar();
        return new Palette(walls, pillar, base.floor(), base.foundation(), base.wallStairs(), base.roofStairs(),
                base.roofSlab(), base.fence(), base.door(), base.bed(), base.pane(), base.road());
    }

    public static Palette of(ArchitectureStyle style) {
        return switch (style) {
            case DESERT -> new Palette(
                    Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                    Blocks.CUT_SANDSTONE.defaultBlockState(),
                    Blocks.SANDSTONE.defaultBlockState(),
                    Blocks.SANDSTONE.defaultBlockState(),
                    Blocks.SMOOTH_SANDSTONE_STAIRS,
                    Blocks.SANDSTONE_STAIRS,
                    Blocks.SANDSTONE_SLAB.defaultBlockState(),
                    Blocks.JUNGLE_FENCE.defaultBlockState(),
                    Blocks.JUNGLE_DOOR,
                    Blocks.GREEN_BED,
                    Blocks.GLASS_PANE.defaultBlockState(),
                    Blocks.SMOOTH_SANDSTONE.defaultBlockState());
            case SAVANNA -> new Palette(
                    Blocks.ACACIA_PLANKS.defaultBlockState(),
                    Blocks.ACACIA_LOG.defaultBlockState(),
                    Blocks.ACACIA_PLANKS.defaultBlockState(),
                    Blocks.COBBLESTONE.defaultBlockState(),
                    Blocks.ACACIA_STAIRS,
                    Blocks.ACACIA_STAIRS,
                    Blocks.ACACIA_SLAB.defaultBlockState(),
                    Blocks.ACACIA_FENCE.defaultBlockState(),
                    Blocks.ACACIA_DOOR,
                    Blocks.ORANGE_BED,
                    Blocks.GLASS_PANE.defaultBlockState(),
                    Blocks.COBBLESTONE.defaultBlockState());
            case SNOWY -> new Palette(
                    Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(),
                    Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.STONE_BRICKS.defaultBlockState(),
                    Blocks.SPRUCE_STAIRS,
                    Blocks.STONE_BRICK_STAIRS,
                    Blocks.STONE_BRICK_SLAB.defaultBlockState(),
                    Blocks.SPRUCE_FENCE.defaultBlockState(),
                    Blocks.SPRUCE_DOOR,
                    Blocks.BLUE_BED,
                    Blocks.GLASS_PANE.defaultBlockState(),
                    Blocks.STONE_BRICKS.defaultBlockState());
            case TAIGA -> new Palette(
                    Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.SPRUCE_LOG.defaultBlockState(),
                    Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
                    Blocks.SPRUCE_STAIRS,
                    Blocks.SPRUCE_STAIRS,
                    Blocks.SPRUCE_SLAB.defaultBlockState(),
                    Blocks.SPRUCE_FENCE.defaultBlockState(),
                    Blocks.SPRUCE_DOOR,
                    Blocks.BROWN_BED,
                    Blocks.GLASS_PANE.defaultBlockState(),
                    Blocks.COBBLESTONE.defaultBlockState());
            case OTHER -> new Palette(
                    Blocks.BIRCH_PLANKS.defaultBlockState(),
                    Blocks.BIRCH_LOG.defaultBlockState(),
                    Blocks.OAK_PLANKS.defaultBlockState(),
                    Blocks.COBBLESTONE.defaultBlockState(),
                    Blocks.BIRCH_STAIRS,
                    Blocks.SPRUCE_STAIRS,
                    Blocks.SPRUCE_SLAB.defaultBlockState(),
                    Blocks.BIRCH_FENCE.defaultBlockState(),
                    Blocks.BIRCH_DOOR,
                    Blocks.YELLOW_BED,
                    Blocks.GLASS_PANE.defaultBlockState(),
                    Blocks.COBBLESTONE.defaultBlockState());
            case PLAINS -> new Palette(
                    Blocks.OAK_PLANKS.defaultBlockState(),
                    Blocks.OAK_LOG.defaultBlockState(),
                    Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.COBBLESTONE.defaultBlockState(),
                    Blocks.OAK_STAIRS,
                    Blocks.DARK_OAK_STAIRS,
                    Blocks.DARK_OAK_SLAB.defaultBlockState(),
                    Blocks.OAK_FENCE.defaultBlockState(),
                    Blocks.OAK_DOOR,
                    Blocks.RED_BED,
                    Blocks.GLASS_PANE.defaultBlockState(),
                    Blocks.COBBLESTONE.defaultBlockState());
        };
    }
}
