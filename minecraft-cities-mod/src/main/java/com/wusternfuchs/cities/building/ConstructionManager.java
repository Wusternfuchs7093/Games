package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.api.CitiesApi;
import com.wusternfuchs.cities.api.SettlementEvent;
import com.wusternfuchs.cities.building.ConstructionProject.Placement;
import com.wusternfuchs.cities.profession.CitiesProfessions;
import com.wusternfuchs.cities.settlement.ArchitectureStyle;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.SettlementTier;
import com.wusternfuchs.cities.settlement.Stock;
import com.wusternfuchs.cities.research.Tech;
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
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
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
    /** Deepest hollow filled under a building or its surroundings. */
    private static final int MAX_FOUNDATION = 12;
    /** Width of the levelled strip around a building. */
    private static final int LEVEL_MARGIN = 2;
    private static final int EARTHWORK_BATCH = 6;
    /** Builders beyond this many no longer speed construction up. */
    public static final int MAX_BUILDER_BONUS = 3;

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
            if (building.type() == BuildingType.HIGHWAY) {
                queue.poll(); // far stretches of road wait for the next time someone is around
            }
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
        if (s.hasTech(Tech.CARPENTRY) && project.placed() % 4 == 3) {
            cost = 0; // every fourth block comes from leftovers
        }
        if (s.hasTech(Tech.CONCRETE) && project.placed() % 2 == 1) {
            cost = 0; // poured concrete needs half the materials
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
        s.replaceBuilding(building);
        s.setProject(null);
        s.record(SettlementManager.day(level), "built", building.type().id());
        CitiesApi.post(new SettlementEvent.BuildingCompleted(level, s, building));
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
        int radius = (s.radiusChunks() + 1) * 16;
        List<Villager> candidates = level.getEntitiesOfClass(Villager.class,
                new AABB(s.center()).inflate(radius, 48, radius),
                v -> v.isAlive() && !v.isBaby() && data.at(v.blockPosition()) == s);
        Villager chosen = candidates.stream()
                .min(Comparator.comparingInt(ConstructionManager::builderRank)
                        .thenComparingDouble(v -> v.distanceToSqr(site.getX(), site.getY(), site.getZ())))
                .orElse(null);
        project.setBuilder(chosen == null ? null : chosen.getUUID());
        if (chosen != null) {
            project.setLastNearSite(level.getGameTime());
            data.setDirty();
        }
        return chosen;
    }

    /** Builders by trade first, then villagers without work, then anybody. */
    private static int builderRank(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (CitiesProfessions.is(profession, CitiesProfessions.BUILDER)) {
            return 0;
        }
        return profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT ? 1 : 2;
    }

    private static void walkTo(Villager villager, BlockPos target) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.6f, 3));
    }

    /** Somewhere to stand while working on {@code target}: the door, a corner, or inside a wall. */
    private static BlockPos standingSpot(ServerLevel level, Settlement s, Building building, Blueprint bp,
                                         BlockPos target) {
        BlockPos base;
        if (building.type() == BuildingType.HIGHWAY) {
            base = target;
        } else if (building.type() == BuildingType.WALL) {
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
     * Lists every block change in build order: level the site (cut trees and hills, fill hollows, smooth a strip of
     * ground around it) top-down, lay the foundation, raise the structure bottom-up, then add doors, beds, lights
     * and crops, and finally lay a road to the center. The list is recomputed from the world after a reload;
     * blocks already in place are skipped and parts of the building already standing are never torn down.
     */
    static List<List<Placement>> planSteps(ServerLevel level, SettlementData data, Settlement s, Building building,
                                           Blueprint bp) {
        Palette palette = Palette.of(s.style(), building.era(), building.stone());
        if (building.type() == BuildingType.WALL) {
            return planWall(level, s, building, palette);
        }
        if (building.type() == BuildingType.HIGHWAY) {
            return Highways.plan(level, s, building, palette);
        }
        List<Placement> clearing = new ArrayList<>();
        List<Placement> filling = new ArrayList<>();
        List<List<Placement>> foundation = new ArrayList<>();
        List<List<Placement>> structure = new ArrayList<>();
        List<List<Placement>> details = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean sandy = s.style() == ArchitectureStyle.DESERT;
        BlockState surface = sandy ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState soil = sandy ? Blocks.SANDSTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        int floorY = building.origin().getY();

        // The building's own columns.
        for (int x = 0; x < bp.width(); x++) {
            for (int z = 0; z < bp.depth(); z++) {
                BlockPos floor = building.toWorld(new BlockPos(x, 0, z));
                if (!level.isLoaded(floor)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, floor.getX(), floor.getZ()) - 1;
                for (int y = top; y > floorY; y--) {
                    int local = y - floorY;
                    if (local < bp.height() && !bp.get(x, local, z).isAir()) {
                        continue; // the structure replaces whatever is there
                    }
                    BlockPos pos = new BlockPos(floor.getX(), y, floor.getZ());
                    if (!level.getBlockState(pos).isAir()) {
                        clearing.add(new Placement(pos, air, false));
                    }
                }
                boolean ground = bp.get(x, 0, z).isAir();
                if (ground && !isSolidGround(level.getBlockState(floor))) {
                    filling.add(new Placement(floor, surface, false));
                }
                for (int y = floorY - 1; y > floorY - MAX_FOUNDATION; y--) {
                    BlockPos pos = new BlockPos(floor.getX(), y, floor.getZ());
                    if (isSolidGround(level.getBlockState(pos))) {
                        break;
                    }
                    foundation.add(List.of(new Placement(pos, ground ? soil : palette.foundation(), !ground)));
                }
            }
        }

        // A strip of levelled ground around it, blending into the land.
        BoundingBox box = building.bounds(bp);
        for (int x = box.minX() - LEVEL_MARGIN; x <= box.maxX() + LEVEL_MARGIN; x++) {
            for (int z = box.minZ() - LEVEL_MARGIN; z <= box.maxZ() + LEVEL_MARGIN; z++) {
                int distance = Math.max(Math.max(box.minX() - x, x - box.maxX()), Math.max(box.minZ() - z, z - box.maxZ()));
                if (distance <= 0 || !level.isLoaded(new BlockPos(x, floorY, z))) {
                    continue;
                }
                int natural = TownPlanner.groundLevel(level, x, z);
                int target = distance == 1 ? floorY : (floorY + natural) / 2;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int y = top; y > target; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && (y > natural || TownPlanner.isBuildableGround(state) || TownPlanner.isVegetation(state))) {
                        clearing.add(new Placement(pos, air, false));
                    }
                }
                for (int y = target; y > target - MAX_FOUNDATION; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isSolidGround(level.getBlockState(pos))) {
                        break;
                    }
                    filling.add(new Placement(pos, y == target ? surface : soil, false));
                }
            }
        }

        // Clear from the top down so nothing is left hanging; fill from the bottom up.
        clearing.sort(Comparator.comparingInt((Placement placement) -> placement.pos().getY()).reversed());
        filling.sort(Comparator.comparingInt(placement -> placement.pos().getY()));
        foundation.sort(Comparator.comparingInt(group -> group.get(0).pos().getY()));

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

        List<List<Placement>> steps = new ArrayList<>();
        steps.addAll(batches(clearing));
        steps.addAll(foundation);
        steps.addAll(batches(filling));
        steps.addAll(structure);
        steps.addAll(details);
        if (building.type().hasRoad()) {
            steps.addAll(planRoad(level, data, s, building, bp, palette));
        }
        return steps;
    }

    /** Earthworks are quick: several blocks of digging or filling per step. */
    private static List<List<Placement>> batches(List<Placement> placements) {
        List<List<Placement>> result = new ArrayList<>();
        for (int i = 0; i < placements.size(); i += EARTHWORK_BATCH) {
            result.add(List.copyOf(placements.subList(i, Math.min(placements.size(), i + EARTHWORK_BATCH))));
        }
        return result;
    }

    /** Solid enough to stand on and build on: not air, water, plants or snow. */
    private static boolean isSolidGround(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()
                && !TownPlanner.isVegetation(state);
    }

    /**
     * A ring wall around the center at the radius stored in the building's variant. It follows the ground,
     * skips water and anything built, and leaves gates on the four main directions and wherever roads cross.
     */
    private static List<List<Placement>> planWall(ServerLevel level, Settlement s, Building building, Palette palette) {
        List<List<Placement>> steps = new ArrayList<>();
        BlockPos center = s.center();
        int radius = building.variant();
        boolean stone = s.tier().ordinal() >= SettlementTier.CITY.ordinal() || s.hasTech(Tech.MASONRY);
        BlockState wall = stone ? palette.foundation() : palette.pillar();
        int height = (stone ? 4 : 3) + (s.hasTech(Tech.ENGINEERING) ? 1 : 0);

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
                || block instanceof BushBlock || block instanceof CarpetBlock || block instanceof TrapDoorBlock
                || block instanceof BasePressurePlateBlock || block instanceof CampfireBlock
                || block instanceof LightningRodBlock;
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
