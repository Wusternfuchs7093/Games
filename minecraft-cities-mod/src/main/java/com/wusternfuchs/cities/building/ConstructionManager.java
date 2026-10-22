package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.building.ConstructionProject.Placement;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.SettlementTier;
import com.wusternfuchs.cities.settlement.Stock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Turns a planned building into real blocks, one at a time, by the hands of a villager. */
public final class ConstructionManager {
    private static final int WORK_DISTANCE = 12;
    private static final int TELEPORT_AFTER_TICKS = 1200;
    private static final int MAX_ROAD_LENGTH = 160;

    private ConstructionManager() {
    }

    // ------------------------------------------------------------------ work

    public static void step(ServerLevel level, SettlementData data, Settlement s) {
        ConstructionProject project = s.project();
        if (project == null) {
            return;
        }
        Building building = project.building();
        Blueprint bp = building.blueprint(s.style());
        if (project.queue() == null) {
            project.setQueue(planSteps(level, data, s, building, bp), level.getGameTime());
        }

        Deque<List<Placement>> queue = project.queue();
        while (!queue.isEmpty() && isDone(level, queue.peek())) {
            queue.poll();
        }
        if (queue.isEmpty()) {
            complete(level, data, s, project);
            return;
        }
        List<Placement> next = queue.peek();
        BlockPos target = next.get(0).pos();
        if (!level.isLoaded(target)) {
            return;
        }

        Villager builder = builder(level, data, s, project, bp);
        if (builder == null || builder.isSleeping()) {
            return;
        }
        // The builder walks to whatever block comes next, so long walls get built along their whole length.
        double dx = builder.getX() - (target.getX() + 0.5);
        double dz = builder.getZ() - (target.getZ() + 0.5);
        if (dx * dx + dz * dz > WORK_DISTANCE * WORK_DISTANCE) {
            walkTo(builder, target);
            if (level.getGameTime() - project.lastNearSite() > TELEPORT_AFTER_TICKS) {
                BlockPos spot = standingSpot(level, s, building, bp, target);
                builder.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                project.setLastNearSite(level.getGameTime());
            }
            return;
        }
        project.setLastNearSite(level.getGameTime());

        int cost = 0;
        for (Placement placement : next) {
            if (placement.costs()) {
                cost++;
            }
        }
        boolean free = CitiesConfig.FREE_CONSTRUCTION.get();
        if (!free && s.materials() < cost) {
            if (!project.stalled()) {
                project.setStalled(true);
                data.setDirty();
            }
            return;
        }
        project.setStalled(false);
        queue.poll();
        place(level, next);
        if (!free) {
            s.addMaterials(-cost);
        }
        project.countPlaced();
        data.setDirty();

        Placement shown = next.get(0);
        if (!shown.state().isAir()) {
            builder.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(shown.state().getBlock().asItem()));
        }
        builder.swing(InteractionHand.MAIN_HAND);
        builder.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(shown.pos()));
    }

    private static boolean isDone(ServerLevel level, List<Placement> group) {
        for (Placement placement : group) {
            BlockState current = level.getBlockState(placement.pos());
            boolean done = placement.state().isAir() ? current.isAir() : current.is(placement.state().getBlock());
            if (!done) {
                return false;
            }
        }
        return true;
    }

    private static void place(ServerLevel level, List<Placement> group) {
        for (Placement placement : group) {
            BlockState state = placement.state();
            // Two-block pieces must be placed as they are: shape updates would delete a lone half.
            if (group.size() == 1) {
                state = Block.updateFromNeighbourShapes(state, level, placement.pos());
            }
            level.setBlock(placement.pos(), state, Block.UPDATE_ALL);
        }
        BlockState placed = group.get(0).state();
        if (!placed.isAir()) {
            SoundType sound = placed.getSoundType();
            level.playSound(null, group.get(0).pos(), sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
        }
    }

    private static void complete(ServerLevel level, SettlementData data, Settlement s, ConstructionProject project) {
        Building building = project.building();
        s.addBuilding(building);
        s.setProject(null);
        s.record(SettlementManager.day(level), "built", building.type().id());
        if (project.builder() != null && level.getEntity(project.builder()) instanceof Villager builder) {
            builder.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        data.setDirty();

        Component message = Component.translatable("cities.construction.finished", s.name(),
                building.type().displayName()).withStyle(ChatFormatting.GREEN);
        for (ServerPlayer player : level.players()) {
            if (data.at(player.blockPosition()) == s) {
                player.displayClientMessage(message, true);
            }
        }
    }

    // ------------------------------------------------------------------ builder

    /** The villager assigned to the project; picks a new one if the old one is gone. */
    @Nullable
    private static Villager builder(ServerLevel level, SettlementData data, Settlement s, ConstructionProject project,
                                    Blueprint bp) {
        if (project.builder() != null && level.getEntity(project.builder()) instanceof Villager v && v.isAlive()) {
            return v;
        }
        BlockPos site = project.building().bounds(bp).getCenter();
        int radius = (s.tier().radiusChunks() + 1) * 16;
        List<Villager> candidates = level.getEntitiesOfClass(Villager.class,
                new AABB(s.center()).inflate(radius, 48, radius),
                v -> v.isAlive() && !v.isBaby() && data.at(v.blockPosition()) == s);
        Villager chosen = candidates.stream()
                .min(Comparator.comparingInt((Villager v) -> isIdle(v) ? 0 : 1)
                        .thenComparingDouble(v -> v.distanceToSqr(site.getX(), site.getY(), site.getZ())))
                .orElse(null);
        project.setBuilder(chosen == null ? null : chosen.getUUID());
        if (chosen != null) {
            project.setLastNearSite(level.getGameTime());
            data.setDirty();
        }
        return chosen;
    }

    private static boolean isIdle(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT;
    }

    private static void walkTo(Villager villager, BlockPos target) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.6f, 3));
    }

    /** Somewhere to stand while working on {@code target}: the door, a corner, or inside a wall. */
    private static BlockPos standingSpot(ServerLevel level, Settlement s, Building building, Blueprint bp,
                                         BlockPos target) {
        BlockPos base;
        if (building.type() == BuildingType.WALL) {
            BlockPos center = s.center();
            base = target.offset(2 * Integer.signum(center.getX() - target.getX()), 0,
                    2 * Integer.signum(center.getZ() - target.getZ()));
        } else {
            BlockPos entrance = building.entrance(bp);
            base = entrance != null ? entrance : building.toWorld(new BlockPos(-1, 0, -1));
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
    }

    // ------------------------------------------------------------------ planning the blocks

    /**
     * Lists every block change in build order: clear the site top-down, fill the foundation, raise the structure
     * bottom-up, then add doors, beds, lights and crops, and finally lay a road to the center.
     * The list is recomputed from the world after a reload; blocks already in place are skipped.
     */
    static List<List<Placement>> planSteps(ServerLevel level, SettlementData data, Settlement s, Building building,
                                           Blueprint bp) {
        Palette palette = Palette.of(s.style());
        if (building.type() == BuildingType.WALL) {
            return planWall(level, s, building, palette);
        }
        List<List<Placement>> clearing = new ArrayList<>();
        List<List<Placement>> foundation = new ArrayList<>();
        List<List<Placement>> structure = new ArrayList<>();
        List<List<Placement>> details = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int x = 0; x < bp.width(); x++) {
            for (int z = 0; z < bp.depth(); z++) {
                BlockPos floor = building.toWorld(new BlockPos(x, 0, z));
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, floor.getX(), floor.getZ()) - 1;
                for (int y = top + 1; y < floor.getY(); y++) {
                    foundation.add(List.of(new Placement(new BlockPos(floor.getX(), y, floor.getZ()), palette.foundation(), true)));
                }
                // Terrain sticking out above the blueprint's own volume.
                for (int y = top; y >= floor.getY() + bp.height(); y--) {
                    clearing.add(List.of(new Placement(new BlockPos(floor.getX(), y, floor.getZ()), air, false)));
                }
            }
        }

        for (int y = bp.height() - 1; y >= 0; y--) {
            for (int z = 0; z < bp.depth(); z++) {
                for (int x = 0; x < bp.width(); x++) {
                    if (bp.get(x, y, z).isAir()) {
                        clearing.add(List.of(new Placement(building.toWorld(new BlockPos(x, y, z)), air, false)));
                    }
                }
            }
        }

        for (int y = 0; y < bp.height(); y++) {
            for (int z = 0; z < bp.depth(); z++) {
                for (int x = 0; x < bp.width(); x++) {
                    BlockPos local = new BlockPos(x, y, z);
                    BlockState state = bp.get(x, y, z);
                    if (state.isAir() || bp.isSecondHalf(local)) {
                        continue;
                    }
                    List<Placement> group = new ArrayList<>();
                    group.add(new Placement(building.toWorld(local), state.rotate(building.rotation()), true));
                    BlockPos partner = bp.partnerOf(local);
                    if (partner != null) {
                        group.add(new Placement(building.toWorld(partner),
                                bp.get(partner.getX(), partner.getY(), partner.getZ()).rotate(building.rotation()), false));
                    }
                    (isDetail(state) ? details : structure).add(List.copyOf(group));
                }
            }
        }

        List<List<Placement>> steps = new ArrayList<>(clearing.size() + foundation.size() + structure.size() + details.size());
        steps.addAll(clearing);
        steps.addAll(foundation);
        steps.addAll(structure);
        steps.addAll(details);
        if (building.type().hasRoad()) {
            steps.addAll(planRoad(level, data, s, building, bp, palette));
        }
        return steps;
    }

    /**
     * A ring wall around the center at the radius stored in the building's variant. It follows the ground,
     * skips water and anything built, and leaves gates on the four main directions and wherever roads cross.
     */
    private static List<List<Placement>> planWall(ServerLevel level, Settlement s, Building building, Palette palette) {
        List<List<Placement>> steps = new ArrayList<>();
        BlockPos center = s.center();
        int radius = building.variant();
        boolean stone = s.tier().ordinal() >= SettlementTier.CITY.ordinal();
        BlockState wall = stone ? palette.foundation() : palette.pillar();
        int height = stone ? 4 : 3;

        Set<Long> ring = new LinkedHashSet<>();
        int points = (int) Math.ceil(2 * Math.PI * radius * 2);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            ring.add(BlockPos.asLong(center.getX() + (int) Math.round(Math.cos(angle) * radius), 0,
                    center.getZ() + (int) Math.round(Math.sin(angle) * radius)));
        }

        for (long key : ring) {
            int x = BlockPos.getX(key);
            int z = BlockPos.getZ(key);
            if (Math.abs(x - center.getX()) <= 1 || Math.abs(z - center.getZ()) <= 1) {
                continue; // gates
            }
            BlockPos column = new BlockPos(x, center.getY(), z);
            if (!level.isLoaded(column)) {
                continue;
            }
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
            while (level.getBlockState(ground).is(wall.getBlock()) && ground.getY() > level.getMinBuildHeight()) {
                ground = ground.below();
            }
            BlockState surface = level.getBlockState(ground);
            if (!level.getFluidState(ground).isEmpty() || !TownPlanner.isBuildableGround(surface)) {
                continue; // water, roads, fields, houses
            }
            for (int y = 1; y <= height; y++) {
                steps.add(List.of(new Placement(ground.above(y), wall, true)));
            }
        }
        return steps;
    }

    /** Blocks that need something to stand on or lean against, so they go in last. */
    private static boolean isDetail(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof BedBlock || block instanceof TorchBlock
                || block instanceof LanternBlock || block instanceof LadderBlock || block instanceof BellBlock
                || block instanceof BushBlock || block instanceof CarpetBlock;
    }

    /** An L-shaped road from the door to the center that stops when it meets an existing road. */
    private static List<List<Placement>> planRoad(ServerLevel level, SettlementData data, Settlement s, Building building,
                                                  Blueprint bp, Palette palette) {
        List<List<Placement>> road = new ArrayList<>();
        BlockPos entrance = building.entrance(bp);
        if (entrance == null) {
            return road;
        }
        boolean paved = s.tier().ordinal() >= SettlementTier.SMALL_TOWN.ordinal();
        BlockState roadState = paved ? palette.road() : Blocks.DIRT_PATH.defaultBlockState();
        List<BoundingBox> obstacles = new ArrayList<>();
        obstacles.add(building.bounds(bp));
        for (Building other : s.buildings()) {
            obstacles.add(other.bounds(other.blueprint(s.style())));
        }

        BlockPos goal = s.center();
        Direction.Axis firstAxis = building.front().getAxis();
        int x = entrance.getX();
        int z = entrance.getZ();
        for (int i = 0; i < MAX_ROAD_LENGTH; i++) {
            if (Math.abs(x - goal.getX()) <= 2 && Math.abs(z - goal.getZ()) <= 2) {
                break;
            }
            BlockPos column = new BlockPos(x, goal.getY(), z);
            if (!level.isLoaded(column) || data.at(column) != s) {
                break;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
            BlockState ground = level.getBlockState(top);
            if (i > 2 && (ground.is(Blocks.DIRT_PATH) || ground.is(roadState.getBlock()))) {
                break;
            }
            boolean blocked = false;
            for (BoundingBox box : obstacles) {
                if (box.isInside(top)) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked && TownPlanner.isBuildableGround(ground)) {
                BlockState above = level.getBlockState(top.above());
                if (!above.isAir() && above.canBeReplaced()) {
                    road.add(List.of(new Placement(top.above(), Blocks.AIR.defaultBlockState(), false)));
                }
                road.add(List.of(new Placement(top, roadState, false)));
            }

            // Leave the door along its axis first, then turn towards the center.
            boolean moveZ = firstAxis == Direction.Axis.Z ? z != goal.getZ() : x == goal.getX();
            if (moveZ) {
                z += Integer.signum(goal.getZ() - z);
            } else {
                x += Integer.signum(goal.getX() - x);
            }
        }
        return road;
    }

    // ------------------------------------------------------------------ storage

    /** Empties the warehouse chests into the settlement's stocks. */
    public static void collectStorage(ServerLevel level, Settlement s) {
        for (Building building : s.buildings()) {
            Blueprint bp = building.blueprint(s.style());
            for (BlockPos local : bp.storage()) {
                BlockPos pos = building.toWorld(local);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockEntity entity = level.getBlockEntity(pos);
                if (!(entity instanceof Container container)) {
                    continue;
                }
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (!stack.isEmpty()) {
                        Stock.Value value = Stock.of(stack);
                        value.stock().add(s, value.points());
                        container.setItem(slot, ItemStack.EMPTY);
                    }
                }
                container.setChanged();
            }
        }
    }
}
