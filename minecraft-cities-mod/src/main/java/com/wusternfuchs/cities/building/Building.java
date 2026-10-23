package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.settlement.ArchitectureStyle;
import com.wusternfuchs.cities.research.Era;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A building placed in the world: which blueprint, where its local origin is and how it is turned.
 * {@code era} is the age it was built in, which sets its architecture; {@code stone} early buildings stand on a
 * stone ground floor.
 */
public record Building(BuildingType type, BlockPos origin, Rotation rotation, int variant, boolean stone, Era era) {

    public Building(BuildingType type, BlockPos origin, Rotation rotation, int variant) {
        this(type, origin, rotation, variant, false, Era.FOUNDING);
    }

    public Building(BuildingType type, BlockPos origin, Rotation rotation, int variant, boolean stone) {
        this(type, origin, rotation, variant, stone, Era.FOUNDING);
    }

    public Blueprint blueprint(ArchitectureStyle style) {
        return type.blueprint(style, variant, stone, era);
    }

    /** The same building rebuilt in the style of {@code newEra}. */
    public Building rebuilt(Era newEra, boolean masonry) {
        return new Building(type, origin, rotation, variant, masonry, newEra);
    }

    /** Early buildings that do not stand on stone are wooden and can catch fire. */
    public boolean wooden() {
        return Period.of(era) == Period.EARLY && !stone;
    }

    public Period period() {
        return Period.of(era);
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
        tag.putString("era", era.id());
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
        Era era = Era.byId(tag.getString("era"));
        return new Building(type, NbtUtils.readBlockPos(tag, "origin").orElse(BlockPos.ZERO), rotation,
                tag.getInt("variant"), tag.getBoolean("stone"), era == null ? Era.FOUNDING : era);
    }
}
