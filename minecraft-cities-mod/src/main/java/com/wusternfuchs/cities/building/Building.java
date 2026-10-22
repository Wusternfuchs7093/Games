package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.settlement.ArchitectureStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A building placed in the world: which blueprint, where its local origin is and how it is turned.
 * {@code stone} buildings use the masonry palette.
 */
public record Building(BuildingType type, BlockPos origin, Rotation rotation, int variant, boolean stone) {

    public Building(BuildingType type, BlockPos origin, Rotation rotation, int variant) {
        this(type, origin, rotation, variant, false);
    }

    public Blueprint blueprint(ArchitectureStyle style) {
        return type.blueprint(style, variant, stone);
    }

    public Building inStone() {
        return new Building(type, origin, rotation, variant, true);
    }

    public BlockPos toWorld(BlockPos local) {
        return origin.offset(local.rotate(rotation));
    }

    public BoundingBox bounds(Blueprint blueprint) {
        return BoundingBox.fromCorners(toWorld(BlockPos.ZERO),
                toWorld(new BlockPos(blueprint.width() - 1, blueprint.height() - 1, blueprint.depth() - 1)));
    }

    /** The direction the front door looks to. */
    public Direction front() {
        return rotation.rotate(Direction.SOUTH);
    }

    @Nullable
    public BlockPos entrance(Blueprint blueprint) {
        return blueprint.entrance() == null ? null : toWorld(blueprint.entrance());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.id());
        tag.put("origin", NbtUtils.writeBlockPos(origin));
        tag.putString("rotation", rotation.name());
        tag.putInt("variant", variant);
        tag.putBoolean("stone", stone);
        return tag;
    }

    @Nullable
    public static Building load(CompoundTag tag) {
        BuildingType type = BuildingType.byId(tag.getString("type"));
        if (type == null) {
            return null;
        }
        Rotation rotation;
        try {
            rotation = Rotation.valueOf(tag.getString("rotation"));
        } catch (IllegalArgumentException e) {
            rotation = Rotation.NONE;
        }
        return new Building(type, NbtUtils.readBlockPos(tag, "origin").orElse(BlockPos.ZERO), rotation,
                tag.getInt("variant"), tag.getBoolean("stone"));
    }
}
