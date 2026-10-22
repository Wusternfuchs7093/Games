package com.wusternfuchs.cities.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A building in local coordinates: x across, y up (0 = floor), z deep. The front faces +z (south);
 * the door sits in the last row and the entrance is the spot just outside it.
 * Positions that are not set are air, so building on a site also clears it.
 */
public final class Blueprint {
    private final int width;
    private final int height;
    private final int depth;
    private final BlockState[] states;
    private final Map<BlockPos, BlockPos> partners;
    private final List<BlockPos> storage;
    @Nullable
    private final BlockPos entrance;

    private Blueprint(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;
        this.depth = builder.depth;
        this.states = builder.states;
        this.partners = Map.copyOf(builder.partners);
        this.storage = List.copyOf(builder.storage);
        this.entrance = builder.entrance;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    public BlockState get(int x, int y, int z) {
        return states[(y * depth + z) * width + x];
    }

    /** Second half of a two-block piece (door top, bed head) whose first half is at {@code pos}. */
    @Nullable
    public BlockPos partnerOf(BlockPos pos) {
        return partners.get(pos);
    }

    public boolean isSecondHalf(BlockPos pos) {
        return partners.containsValue(pos);
    }

    /** Containers the settlement treats as its public storage. */
    public List<BlockPos> storage() {
        return storage;
    }

    @Nullable
    public BlockPos entrance() {
        return entrance;
    }

    public static final class Builder {
        private final int width;
        private final int height;
        private final int depth;
        private final BlockState[] states;
        private final Map<BlockPos, BlockPos> partners = new HashMap<>();
        private final List<BlockPos> storage = new ArrayList<>();
        private BlockPos entrance;

        public Builder(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.states = new BlockState[width * height * depth];
            Arrays.fill(states, Blocks.AIR.defaultBlockState());
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int depth() {
            return depth;
        }

        public Builder set(int x, int y, int z, BlockState state) {
            if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
                throw new IllegalArgumentException("Outside blueprint: " + x + "," + y + "," + z);
            }
            states[(y * depth + z) * width + x] = state;
            return this;
        }

        public Builder fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                        set(x, y, z, state);
                    }
                }
            }
            return this;
        }

        public Builder door(int x, int y, int z, Block door, Direction facing) {
            BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, facing)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            set(x, y, z, lower);
            set(x, y + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            partners.put(new BlockPos(x, y, z), new BlockPos(x, y + 1, z));
            return this;
        }

        /** A bed whose foot is at (x, y, z) and whose head points towards {@code facing}. */
        public Builder bed(int x, int y, int z, Block bed, Direction facing) {
            BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT);
            BlockPos footPos = new BlockPos(x, y, z);
            BlockPos headPos = footPos.relative(facing);
            set(x, y, z, foot);
            set(headPos.getX(), headPos.getY(), headPos.getZ(), foot.setValue(BedBlock.PART, BedPart.HEAD));
            partners.put(footPos, headPos);
            return this;
        }

        public Builder storage(int x, int y, int z, BlockState container) {
            set(x, y, z, container);
            storage.add(new BlockPos(x, y, z));
            return this;
        }

        /** Marks the ground spot in front of the door; roads start here. May lie outside the blueprint. */
        public Builder entrance(int x, int z) {
            this.entrance = new BlockPos(x, 0, z);
            return this;
        }

        public Blueprint build() {
            return new Blueprint(this);
        }
    }
}
