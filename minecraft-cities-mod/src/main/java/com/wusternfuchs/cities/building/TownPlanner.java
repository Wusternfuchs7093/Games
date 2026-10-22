package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.research.Tech;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.SettlementTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Map;

/** Decides what a settlement builds next and finds a free spot of land for it. */
public final class TownPlanner {
    /** Free ground kept around every new building. */
    private static final int MARGIN = 2;
    private static final int MAX_SLOPE = 3;
    private static final int MAX_ATTEMPTS = 400;
    private static final int MIN_MATERIALS_TO_START = 32;

    private TownPlanner() {
    }

    /** Starts a new project if the settlement is idle and can afford one. */
    public static void plan(ServerLevel level, SettlementData data, Settlement s) {
        if (!CitiesConfig.CONSTRUCTION_ENABLED.get() || s.project() != null || s.abandoned() || s.adults() == 0) {
            return;
        }
        if (!CitiesConfig.FREE_CONSTRUCTION.get() && s.materials() < MIN_MATERIALS_TO_START) {
            return;
        }
        if (!s.damaged().isEmpty()) {
            Building ruin = s.damaged().remove(0);
            s.setProject(new ConstructionProject(ruin));
            s.record(SettlementManager.day(level), "repair", ruin.type().id());
            data.setDirty();
            return;
        }
        BuildingType type = choose(s);
        if (type != null) {
            start(level, data, s, type);
        } else {
            startUpgrade(level, data, s);
        }
    }

    /** With masonry known and nothing else to do, the oldest wooden building is rebuilt in stone. */
    private static boolean startUpgrade(ServerLevel level, SettlementData data, Settlement s) {
        if (!s.hasTech(Tech.MASONRY)) {
            return false;
        }
        for (Building building : s.buildings()) {
            if (!building.stone() && building.type().upgradable()) {
                s.setProject(new ConstructionProject(building.inStone()));
                s.record(SettlementManager.day(level), "upgrade", building.type().id());
                data.setDirty();
                return true;
            }
        }
        return false;
    }

    /** Tries to start building {@code type}. Returns false if no suitable land was found. */
    public static boolean start(ServerLevel level, SettlementData data, Settlement s, BuildingType type) {
        Building building;
        if (type == BuildingType.WALL) {
            building = new Building(BuildingType.WALL, s.center(), Rotation.NONE, wallRadius(s));
        } else {
            building = findSite(level, data, s, type, level.getRandom().nextInt(type.variants()));
        }
        if (building == null) {
            return false;
        }
        s.setProject(new ConstructionProject(building));
        s.record(SettlementManager.day(level), "construction", type.id());
        data.setDirty();
        return true;
    }

    @Nullable
    static BuildingType choose(Settlement s) {
        int freeBeds = s.beds() - s.population();
        if (freeBeds < 1) {
            return BuildingType.HOUSE;
        }

        SettlementTier next = s.tier().next();
        boolean canGrow = next != null && (CitiesConfig.IGNORE_POTENTIAL.get() || next.ordinal() <= s.maxTier().ordinal());
        if (canGrow) {
            for (Map.Entry<BuildingType, Integer> entry : BuildingType.requiredFor(next).entrySet()) {
                if (entry.getKey() == BuildingType.WALL && hasWallAt(s, wallRadius(s))) {
                    continue; // the next wall needs a bigger territory
                }
                if (s.count(entry.getKey()) < entry.getValue()) {
                    return entry.getKey();
                }
            }
        }

        if (freeBeds < 2) {
            return BuildingType.HOUSE;
        }
        if (s.food() < s.population() * 10 && s.count(BuildingType.FARM) < 1 + s.population() / 6) {
            return BuildingType.FARM;
        }
        if (s.tier().ordinal() >= SettlementTier.VILLAGE.ordinal() && s.count(BuildingType.WAREHOUSE) == 0) {
            return BuildingType.WAREHOUSE;
        }
        if (s.workstations() < s.adults() && s.count(BuildingType.WORKSHOP) < 1 + s.population() / 4) {
            return BuildingType.WORKSHOP;
        }
        if (s.tier().ordinal() >= SettlementTier.TOWNSHIP.ordinal() && s.count(BuildingType.LIBRARY) == 0) {
            return BuildingType.LIBRARY;
        }
        if (s.tier().ordinal() >= SettlementTier.TOWNSHIP.ordinal() && s.count(BuildingType.HOSPITAL) == 0
                && (s.epidemic() > 0 || s.population() >= 30)) {
            return BuildingType.HOSPITAL;
        }
        if (s.tier().ordinal() >= SettlementTier.SMALL_TOWN.ordinal() && !hasWallAt(s, wallRadius(s))
                && s.count(BuildingType.WALL) < 2) {
            return BuildingType.WALL;
        }
        if (canGrow) {
            double scale = CitiesConfig.POPULATION_SCALE.get();
            if (s.beds() < next.minBeds(scale) + 2) {
                return BuildingType.HOUSE;
            }
            if (s.workstations() < next.minWorkstations()) {
                return BuildingType.WORKSHOP;
            }
        }
        int others = s.buildings().size() - s.count(BuildingType.LAMP);
        if (s.count(BuildingType.LAMP) < others / 2) {
            return BuildingType.LAMP;
        }
        return null;
    }

    /** Walls go just inside the edge of the current territory. */
    public static int wallRadius(Settlement s) {
        return s.tier().radiusChunks() * 16 - 6;
    }

    private static boolean hasWallAt(Settlement s, int radius) {
        for (Building building : s.buildings()) {
            if (building.type() == BuildingType.WALL && building.variant() == radius) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ site search

    /** Walks rings around the center looking for flat, empty, natural ground inside the territory. */
    @Nullable
    public static Building findSite(ServerLevel level, SettlementData data, Settlement s, BuildingType type, int variant) {
        Blueprint bp = type.blueprint(s.style(), variant);
        Palette palette = Palette.of(s.style());
        BlockPos center = s.center();
        RandomSource random = level.getRandom();
        int maxRadius = s.tier().radiusChunks() * 16 - Math.max(bp.width(), bp.depth()) / 2 - MARGIN;
        int attempts = 0;
        for (int r = 8; r <= maxRadius; r += 3) {
            int steps = Math.max(8, (int) (2 * Math.PI * r / 5));
            int offset = random.nextInt(steps);
            for (int i = 0; i < steps; i++) {
                if (++attempts > MAX_ATTEMPTS) {
                    return null;
                }
                double angle = 2 * Math.PI * (i + offset) / steps;
                int x = center.getX() + (int) Math.round(Math.cos(angle) * r);
                int z = center.getZ() + (int) Math.round(Math.sin(angle) * r);
                Rotation rotation = facing(x, z, center);
                BlockPos turnedCenter = new BlockPos(bp.width() / 2, 0, bp.depth() / 2).rotate(rotation);
                Building candidate = new Building(type, new BlockPos(x - turnedCenter.getX(), 0, z - turnedCenter.getZ()),
                        rotation, variant);
                Integer floor = floorLevel(level, data, s, candidate, bp, palette);
                if (floor != null) {
                    return new Building(type, candidate.origin().atY(floor), rotation, variant,
                            s.hasTech(Tech.MASONRY));
                }
            }
        }
        return null;
    }

    /** Rotation that turns the front door (local south) towards the settlement center. */
    private static Rotation facing(int x, int z, BlockPos center) {
        Direction wanted = Direction.getNearest(center.getX() - x, 0, center.getZ() - z);
        if (wanted.getAxis() == Direction.Axis.Y) {
            wanted = Direction.SOUTH;
        }
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(Direction.SOUTH) == wanted) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }

    /** Returns the floor height for the candidate, or null if the land is unsuitable. */
    @Nullable
    private static Integer floorLevel(ServerLevel level, SettlementData data, Settlement s, Building candidate,
                                      Blueprint bp, Palette palette) {
        BoundingBox box = candidate.bounds(bp);
        for (Building other : s.buildings()) {
            if (overlaps(box, other.bounds(other.blueprint(s.style())))) {
                return null;
            }
        }
        if (s.project() != null) {
            Building current = s.project().building();
            if (overlaps(box, current.bounds(current.blueprint(s.style())))) {
                return null;
            }
        }

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        int columns = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX() - MARGIN; x <= box.maxX() + MARGIN; x++) {
            for (int z = box.minZ() - MARGIN; z <= box.maxZ() + MARGIN; z++) {
                pos.set(x, s.center().getY(), z);
                if (!level.isLoaded(pos) || data.at(pos) != s) {
                    return null;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockState ground = level.getBlockState(pos.setY(top));
                boolean footprint = x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
                if (footprint) {
                    if (!isBuildableGround(ground)) {
                        return null;
                    }
                    min = Math.min(min, top);
                    max = Math.max(max, top);
                    sum += top;
                    columns++;
                } else if (!isOpenGround(ground, palette)) {
                    return null;
                }
            }
        }
        if (columns == 0 || max - min > MAX_SLOPE) {
            return null;
        }
        return (int) Math.round((double) sum / columns);
    }

    private static boolean overlaps(BoundingBox a, BoundingBox b) {
        return a.minX() - MARGIN <= b.maxX() && a.maxX() + MARGIN >= b.minX()
                && a.minZ() - MARGIN <= b.maxZ() && a.maxZ() + MARGIN >= b.minZ();
    }

    /** Untouched natural ground: no fields, roads, trees, water or someone's house. */
    static boolean isBuildableGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK);
    }

    /** What may border a new building: natural ground, roads, fields or water. */
    private static boolean isOpenGround(BlockState state, Palette palette) {
        return isBuildableGround(state) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)
                || state.is(Blocks.WATER) || state.is(palette.road().getBlock());
    }
}
