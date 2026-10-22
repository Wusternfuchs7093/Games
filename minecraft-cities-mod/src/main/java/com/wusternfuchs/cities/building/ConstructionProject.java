package com.wusternfuchs.cities.building;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** The building a settlement is working on right now. */
public final class ConstructionProject {
    /** One block to place; {@code costs} says whether it uses up building materials. */
    public record Placement(BlockPos pos, BlockState state, boolean costs) {
    }

    private final Building building;
    @Nullable
    private UUID builder;
    private int placed;
    private boolean stalled;

    // Rebuilt from the world after loading, so not saved.
    @Nullable
    private Deque<List<Placement>> queue;
    private int total;
    private long lastNearSite;

    public ConstructionProject(Building building) {
        this.building = building;
    }

    public Building building() {
        return building;
    }

    @Nullable
    public UUID builder() {
        return builder;
    }

    public void setBuilder(@Nullable UUID builder) {
        this.builder = builder;
    }

    public int placed() {
        return placed;
    }

    public void countPlaced() {
        placed++;
    }

    public boolean stalled() {
        return stalled;
    }

    public void setStalled(boolean stalled) {
        this.stalled = stalled;
    }

    @Nullable
    public Deque<List<Placement>> queue() {
        return queue;
    }

    public void setQueue(List<List<Placement>> steps, long now) {
        this.queue = new ArrayDeque<>(steps);
        this.total = steps.size();
        this.lastNearSite = now;
    }

    /** Share of the work done, 0..100. Unknown (0) until the queue is built. */
    public int percent() {
        if (queue == null || total == 0) {
            return 0;
        }
        return (int) (100L * (total - queue.size()) / total);
    }

    public long lastNearSite() {
        return lastNearSite;
    }

    public void setLastNearSite(long time) {
        this.lastNearSite = time;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("building", building.save());
        if (builder != null) {
            tag.putUUID("builder", builder);
        }
        tag.putInt("placed", placed);
        tag.putBoolean("stalled", stalled);
        return tag;
    }

    @Nullable
    public static ConstructionProject load(CompoundTag tag) {
        Building building = Building.load(tag.getCompound("building"));
        if (building == null) {
            return null;
        }
        ConstructionProject project = new ConstructionProject(building);
        if (tag.hasUUID("builder")) {
            project.builder = tag.getUUID("builder");
        }
        project.placed = tag.getInt("placed");
        project.stalled = tag.getBoolean("stalled");
        return project;
    }
}
