package com.wusternfuchs.cities.disaster;

import com.wusternfuchs.cities.building.Building;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** What time does to an abandoned town, and what it gives back when people return. */
public final class RuinManager {
    private static final float DECAY_CHANCE = 0.2f;

    private RuinManager() {
    }

    /** Rots wood, grows moss on stone, spins cobwebs and leaves a chest of old treasures at the center. */
    public static void decay(ServerLevel level, Settlement s) {
        RandomSource random = level.getRandom();
        for (Building building : s.buildings()) {
            if (building.type() == BuildingType.WALL || building.type() == BuildingType.HIGHWAY) {
                continue;
            }
            BoundingBox box = building.bounds(building.blueprint(s.style()));
            if (!level.isLoaded(box.getCenter())) {
                continue;
            }
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (random.nextFloat() >= DECAY_CHANCE) {
                    continue;
                }
                BlockState ruined = ruin(level.getBlockState(pos), random);
                if (ruined != null) {
                    // No neighbour updates: a ruin should crumble, not pop items everywhere.
                    level.setBlock(pos, ruined, Block.UPDATE_CLIENTS);
                }
            }
        }
        leaveTreasure(level, s, random);
        s.record(SettlementManager.day(level), "ruined", "");
    }

    private static BlockState ruin(BlockState state, RandomSource random) {
        if (state.isAir() || state.getBlock() instanceof BedBlock || state.getBlock() instanceof DoorBlock) {
            return null;
        }
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.LOGS)) {
            return random.nextInt(5) < 3 ? Blocks.AIR.defaultBlockState() : Blocks.COBWEB.defaultBlockState();
        }
        if (state.is(Blocks.COBBLESTONE)) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (state.is(Blocks.STONE_BRICKS)) {
            return random.nextBoolean() ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                    : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        if (state.is(Blocks.GLASS_PANE) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN)) {
            return Blocks.AIR.defaultBlockState();
        }
        return null;
    }

    private static void leaveTreasure(ServerLevel level, Settlement s, RandomSource random) {
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.center().east(2));
        if (!level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced()) {
            return;
        }
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return;
        }
        int emeralds = Math.min(16, s.wealth() / 36);
        ItemStack[] loot = {
                new ItemStack(Items.EMERALD, Math.max(1, emeralds)),
                new ItemStack(Items.BREAD, 2 + random.nextInt(6)),
                new ItemStack(Items.IRON_INGOT, 1 + random.nextInt(4)),
                new ItemStack(Items.BOOK, 1 + random.nextInt(2)),
                new ItemStack(Items.MAP),
        };
        for (int i = 0; i < loot.length; i++) {
            chest.setItem(i * 3, loot[i]);
        }
    }

    /** When people come back to the ruins, every building goes on the repair list. */
    public static void markForRebuilding(Settlement s) {
        for (Building building : s.buildings()) {
            if (building.type() != BuildingType.WALL && building.type() != BuildingType.HIGHWAY
                    && !s.damaged().contains(building)) {
                s.damaged().add(building);
            }
        }
    }
}
