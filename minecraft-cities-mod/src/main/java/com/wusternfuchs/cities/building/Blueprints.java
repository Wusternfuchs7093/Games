package com.wusternfuchs.cities.building;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Consumer;

/** Procedural blueprints for every building type, dressed in a regional palette. */
public final class Blueprints {
    private static final Block[] WORKSTATIONS = {
            Blocks.SMITHING_TABLE, Blocks.FLETCHING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.LOOM,
            Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.GRINDSTONE, Blocks.STONECUTTER,
            Blocks.BREWING_STAND, Blocks.LECTERN, Blocks.CAULDRON, Blocks.COMPOSTER
    };
    private static final Block[] CROPS = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
    private static final Block[] CANOPIES = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.BLUE_WOOL, Blocks.GREEN_WOOL};

    private Blueprints() {
    }

    public static int workstationVariants() {
        return WORKSTATIONS.length;
    }

    // ------------------------------------------------------------------ building types

    public static Blueprint house(Palette p, int variant) {
        boolean large = variant % 2 == 1;
        int width = large ? 9 : 7;
        return hall(p, width, 7, b -> {
            b.bed(1, 1, 2, p.bed(), Direction.NORTH);
            b.bed(width - 2, 1, 2, p.bed(), Direction.NORTH);
            if (large) {
                b.bed(3, 1, 2, p.bed(), Direction.NORTH);
            }
            b.set(width - 2, 1, 5, Blocks.CRAFTING_TABLE.defaultBlockState());
        }).build();
    }

    public static Blueprint workshop(Palette p, int variant) {
        int width = 7;
        return hall(p, width, 7, b -> {
            b.set(1, 1, 1, workstation(variant));
            b.set(width - 2, 1, 1, workstation(variant + 5));
        }).build();
    }

    public static Blueprint warehouse(Palette p, int variant) {
        return hall(p, 9, 7, b -> {
            BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
            for (int x = 1; x <= 7; x += 2) {
                b.storage(x, 1, 1, chest);
            }
            b.set(1, 1, 4, Blocks.HAY_BLOCK.defaultBlockState());
            b.set(7, 1, 4, Blocks.HAY_BLOCK.defaultBlockState());
        }).build();
    }

    public static Blueprint townHall(Palette p, int variant) {
        int width = 11;
        int depth = 9;
        return hall(p, width, depth, b -> {
            b.set(width / 2, 1, 1, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH));
            for (int z = 2; z <= depth - 2; z++) {
                b.set(width / 2, 1, z, Blocks.RED_CARPET.defaultBlockState());
            }
            b.set(2, 1, depth - 3, Blocks.BELL.defaultBlockState());
            b.set(width - 3, 1, 1, Blocks.BOOKSHELF.defaultBlockState());
            b.set(2, 1, 1, Blocks.BOOKSHELF.defaultBlockState());
        }).build();
    }

    public static Blueprint library(Palette p, int variant) {
        return hall(p, 9, 7, b -> {
            for (int x = 1; x <= 7; x += 2) {
                b.fill(x, 1, 1, x, 2, 1, Blocks.BOOKSHELF.defaultBlockState());
            }
            BlockState lectern = Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH);
            b.set(2, 1, 3, lectern);
            b.set(6, 1, 3, lectern);
            b.set(1, 1, 5, Blocks.BOOKSHELF.defaultBlockState());
            b.set(7, 1, 5, Blocks.BOOKSHELF.defaultBlockState());
        }).build();
    }

    public static Blueprint hospital(Palette p, int variant) {
        return hall(p, 9, 7, b -> {
            b.set(2, 1, 1, Blocks.BREWING_STAND.defaultBlockState());
            b.set(6, 1, 1, Blocks.BREWING_STAND.defaultBlockState());
            b.set(4, 1, 1, Blocks.CAULDRON.defaultBlockState());
            BlockState cot = Blocks.WHITE_CARPET.defaultBlockState();
            b.fill(1, 1, 3, 2, 1, 4, cot);
            b.fill(6, 1, 3, 7, 1, 4, cot);
        }).build();
    }

    /** A roomy house for the town's elite: two beds, books, carpets and light. */
    public static Blueprint manor(Palette p, int variant) {
        int width = 11;
        int depth = 9;
        return hall(p, width, depth, b -> {
            b.bed(1, 1, 2, p.bed(), Direction.NORTH);
            b.bed(width - 2, 1, 2, p.bed(), Direction.NORTH);
            b.fill(3, 1, 1, 7, 1, 1, Blocks.BOOKSHELF.defaultBlockState());
            b.fill(3, 1, 3, 7, 1, 6, Blocks.RED_CARPET.defaultBlockState());
            b.set(1, 1, depth - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
            b.set(width - 2, 1, depth - 2, Blocks.FLOWER_POT.defaultBlockState());
            b.set(3, 2, 1, Blocks.LANTERN.defaultBlockState());
            b.set(7, 2, 1, Blocks.LANTERN.defaultBlockState());
        }).build();
    }

    /** A fenced graveyard: stones and flowers in two rows along a gravel path. */
    public static Blueprint cemetery(Palette p, int variant) {
        Blueprint.Builder b = new Blueprint.Builder(7, 3, 7);
        b.fill(0, 0, 0, 6, 0, 6, Blocks.COARSE_DIRT.defaultBlockState());
        b.fill(3, 0, 1, 3, 0, 6, Blocks.GRAVEL.defaultBlockState());
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                if (x == 0 || x == 6 || z == 0 || z == 6) {
                    b.set(x, 1, z, p.fence());
                }
            }
        }
        b.set(3, 1, 6, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH));
        for (int x : new int[]{1, 5}) {
            for (int z : new int[]{1, 3}) {
                b.set(x, 1, z, Blocks.COBBLESTONE_WALL.defaultBlockState());
                b.set(x, 1, z + 1, Blocks.POPPY.defaultBlockState());
            }
        }
        b.set(0, 2, 0, Blocks.LANTERN.defaultBlockState());
        b.set(6, 2, 0, Blocks.LANTERN.defaultBlockState());
        b.entrance(3, 7);
        return b.build();
    }

    public static Blueprint farm(Palette p, int variant) {
        Blueprint.Builder b = new Blueprint.Builder(9, 2, 9);
        b.fill(0, 0, 0, 8, 0, 8, p.pillar());
        b.fill(1, 0, 1, 7, 0, 7, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        b.set(4, 0, 4, Blocks.WATER.defaultBlockState());
        BlockState crop = CROPS[Math.floorMod(variant, CROPS.length)].defaultBlockState();
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                if (x != 4 || z != 4) {
                    b.set(x, 1, z, crop);
                }
            }
        }
        b.set(0, 1, 0, Blocks.COMPOSTER.defaultBlockState());
        b.entrance(4, 9);
        return b.build();
    }

    public static Blueprint well(Palette p, int variant) {
        Blueprint.Builder b = new Blueprint.Builder(5, 4, 5);
        b.fill(0, 0, 0, 4, 0, 4, p.foundation());
        b.fill(1, 0, 1, 3, 0, 3, Blocks.WATER.defaultBlockState());
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (x == 0 || x == 4 || z == 0 || z == 4) {
                    b.set(x, 1, z, p.foundation());
                }
            }
        }
        for (int x = 0; x <= 4; x += 4) {
            for (int z = 0; z <= 4; z += 4) {
                b.set(x, 2, z, p.fence());
            }
        }
        b.fill(0, 3, 0, 4, 3, 4, p.roofSlab());
        b.entrance(2, 5);
        return b.build();
    }

    public static Blueprint market(Palette p, int variant) {
        Blueprint.Builder b = new Blueprint.Builder(9, 4, 7);
        b.fill(0, 0, 0, 8, 0, 6, p.foundation());
        for (int x = 0; x <= 8; x += 8) {
            for (int z = 0; z <= 6; z += 6) {
                b.fill(x, 1, z, x, 2, z, p.fence());
            }
        }
        b.fill(0, 3, 0, 8, 3, 6, CANOPIES[Math.floorMod(variant, CANOPIES.length)].defaultBlockState());
        b.fill(1, 1, 4, 3, 1, 4, p.roofSlab());
        b.fill(5, 1, 4, 7, 1, 4, p.roofSlab());
        b.set(2, 1, 1, Blocks.HAY_BLOCK.defaultBlockState());
        b.set(4, 1, 1, Blocks.MELON.defaultBlockState());
        b.set(6, 1, 1, Blocks.PUMPKIN.defaultBlockState());
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        b.set(2, 2, 3, hanging);
        b.set(6, 2, 3, hanging);
        b.entrance(4, 7);
        return b.build();
    }

    public static Blueprint watchtower(Palette p, int variant) {
        Blueprint.Builder b = new Blueprint.Builder(5, 10, 5);
        b.fill(0, 0, 0, 4, 0, 4, p.floor());
        for (int y = 1; y <= 6; y++) {
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 4; z++) {
                    boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
                    boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
                    if (corner) {
                        b.set(x, y, z, p.pillar());
                    } else if (edge) {
                        b.set(x, y, z, p.walls());
                    }
                }
            }
        }
        b.set(0, 4, 2, p.pane());
        b.set(4, 4, 2, p.pane());
        b.fill(0, 7, 0, 4, 7, 4, p.walls());
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = 1; y <= 7; y++) {
            b.set(2, y, 1, ladder);
        }
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (x == 0 || x == 4 || z == 0 || z == 4) {
                    b.set(x, 8, z, p.fence());
                }
            }
        }
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        for (int x = 0; x <= 4; x += 4) {
            for (int z = 0; z <= 4; z += 4) {
                b.set(x, 9, z, lantern);
            }
        }
        b.door(2, 1, 4, p.door(), Direction.SOUTH);
        b.entrance(2, 5);
        return b.build();
    }

    public static Blueprint lamp(Palette p, int variant) {
        Blueprint.Builder b = new Blueprint.Builder(1, 4, 1);
        b.set(0, 0, 0, p.foundation());
        b.fill(0, 1, 0, 0, 2, 0, p.fence());
        b.set(0, 3, 0, Blocks.LANTERN.defaultBlockState());
        return b.build();
    }

    // ------------------------------------------------------------------ shared shapes

    /**
     * A one-room building with a gable roof: floor at y=0, walls y=1..3, roof above.
     * The door is in the middle of the front (z = depth - 1) wall.
     */
    private static Blueprint.Builder hall(Palette p, int width, int depth, Consumer<Blueprint.Builder> interior) {
        int roofRows = width / 2 + 1;
        Blueprint.Builder b = new Blueprint.Builder(width, 4 + roofRows, depth);
        b.fill(0, 0, 0, width - 1, 0, depth - 1, p.floor());

        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    boolean corner = (x == 0 || x == width - 1) && (z == 0 || z == depth - 1);
                    boolean edge = x == 0 || x == width - 1 || z == 0 || z == depth - 1;
                    if (corner) {
                        b.set(x, y, z, p.pillar());
                    } else if (edge) {
                        b.set(x, y, z, p.walls());
                    }
                }
            }
        }
        for (int z = 2; z <= depth - 3; z += 2) {
            b.set(0, 2, z, p.pane());
            b.set(width - 1, 2, z, p.pane());
        }
        for (int x = 2; x <= width - 3; x += 2) {
            b.set(x, 2, 0, p.pane());
        }

        BlockState eastFacing = p.roofStairs().defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        BlockState westFacing = p.roofStairs().defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        for (int k = 0; k < roofRows; k++) {
            int y = 4 + k;
            int left = k;
            int right = width - 1 - k;
            if (left < right) {
                for (int z = 0; z < depth; z++) {
                    b.set(left, y, z, eastFacing);
                    b.set(right, y, z, westFacing);
                }
                for (int x = left + 1; x < right; x++) {
                    b.set(x, y, 0, p.walls());
                    b.set(x, y, depth - 1, p.walls());
                }
            } else if (left == right) {
                for (int z = 0; z < depth; z++) {
                    b.set(left, y, z, p.roofSlab());
                }
            }
        }

        b.door(width / 2, 1, depth - 1, p.door(), Direction.SOUTH);
        b.set(width / 2, 3, 1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        b.entrance(width / 2, depth);
        interior.accept(b);
        return b;
    }

    private static BlockState workstation(int variant) {
        Block block = WORKSTATIONS[Math.floorMod(variant, WORKSTATIONS.length)];
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            state = state.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        }
        return state;
    }
}
