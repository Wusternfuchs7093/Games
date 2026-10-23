package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.people.SocialClass;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Decides what a settlement builds next and finds a free spot of land for it. */
public final class TownPlanner {
    /** Free ground kept around every new building. */
    private static final int MARGIN = 2;
    /** Height difference the builders are willing to level out. */
    private static final int MAX_SLOPE = 6;
    private static final int MAX_TREE_HEIGHT = 40;
    private static final int MAX_ATTEMPTS = 500;
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
            s.setProject(new ConstructionProject(ruin.rebuilt(s.era(), s.hasTech(Tech.MASONRY))));
            s.record(SettlementManager.day(level), "repair", ruin.type().id());
            data.setDirty();
            return;
        }
        BuildingType type = choose(s);
        if (type != null && start(level, data, s, type)) {
            return;
        }
        // Nothing needed, or no land for it right now: modernise an old building or build a road.
        if (!startReconstruction(level, data, s)) {
            Highways.start(level, data, s);
        }
    }

    /**
     * Rebuilds the oldest building whose architecture belongs to an earlier age, in the style of the town's
     * current one (early wooden houses also go onto stone once masonry is known). Houses bought by players
     * are left alone. The town waits until it has the materials for the whole building.
     */
    private static boolean startReconstruction(ServerLevel level, SettlementData data, Settlement s) {
        Period now = Period.of(s.era());
        boolean masonry = s.hasTech(Tech.MASONRY);
        for (Building building : s.buildings()) {
            if (!building.type().upgradable() || s.houseOwners().containsKey(building.origin())) {
                continue;
            }
            boolean outdated = building.period().ordinal() < now.ordinal()
                    || now == Period.EARLY && masonry && !building.stone() && building.period() == Period.EARLY;
            if (!outdated) {
                continue;
            }
            Building rebuilt = building.rebuilt(s.era(), masonry);
            if (!CitiesConfig.FREE_CONSTRUCTION.get() && s.materials() < blockCount(rebuilt.blueprint(s.style()))) {
                return false;
            }
            s.setProject(new ConstructionProject(rebuilt));
            s.record(SettlementManager.day(level), "upgrade", building.type().id());
            data.setDirty();
            return true;
        }
        return false;
    }

    private static int blockCount(Blueprint bp) {
        int count = 0;
        for (int y = 0; y < bp.height(); y++) {
            for (int z = 0; z < bp.depth(); z++) {
                for (int x = 0; x < bp.width(); x++) {
                    if (!bp.get(x, y, z).isAir()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Tries to start building {@code type}. Returns false if no suitable land was found. */
    public static boolean start(ServerLevel level, SettlementData data, Settlement s, BuildingType type) {
        Building building = null;
        if (type == BuildingType.WALL) {
            building = new Building(BuildingType.WALL, s.center(), Rotation.NONE, wallRadius(s), false, s.era());
        } else {
            // A random variant first; if it does not fit anywhere, the smallest one.
            int variant = level.getRandom().nextInt(type.variantCount(s.style()));
            building = findSite(level, data, s, type, variant);
            if (building == null && variant != 0) {
                building = findSite(level, data, s, type, 0);
            }
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
        if (s.tier().ordinal() >= SettlementTier.VILLAGE.ordinal() && s.count(BuildingType.CRAFTHOUSE) == 0) {
            return BuildingType.CRAFTHOUSE;
        }
        if (s.tier().ordinal() >= SettlementTier.VILLAGE.ordinal() && s.deathsTotal() >= 3
                && s.count(BuildingType.CEMETERY) == 0) {
            return BuildingType.CEMETERY;
        }
        int elite = s.count(SocialClass.ELITE);
        if (s.tier().ordinal() >= SettlementTier.TOWNSHIP.ordinal() && s.count(BuildingType.MANOR) * 2 < elite) {
            return BuildingType.MANOR;
        }
        if (s.tier().ordinal() >= SettlementTier.TOWNSHIP.ordinal() && s.count(BuildingType.TAVERN) == 0) {
            return BuildingType.TAVERN;
        }
        if (s.tier().ordinal() >= SettlementTier.TOWNSHIP.ordinal() && s.count(BuildingType.LIBRARY) == 0) {
            return BuildingType.LIBRARY;
        }
        if (s.tier().ordinal() >= SettlementTier.TOWNSHIP.ordinal() && s.count(BuildingType.TEMPLE) == 0) {
            return BuildingType.TEMPLE;
        }
        if (s.tier().ordinal() >= SettlementTier.TOWNSHIP.ordinal()
                && s.artisans() >= s.count(BuildingType.CRAFTHOUSE) * SettlementManager.ARTISANS_PER_CRAFTHOUSE
                && s.count(BuildingType.CRAFTHOUSE) < 1 + s.population() / 40) {
            return BuildingType.CRAFTHOUSE;
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
        return s.radiusChunks() * 16 - 6;
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

    /**
     * Walks rings around the center looking for fairly level, empty, natural ground inside the territory.
     * Trees, bushes and gentle slopes are fine: the builders clear and level the site before they start.
     */
    @Nullable
    public static Building findSite(ServerLevel level, SettlementData data, Settlement s, BuildingType type, int variant) {
        boolean masonry = s.hasTech(Tech.MASONRY);
        Blueprint bp = type.blueprint(s.style(), variant, masonry, s.era());
        Palette palette = Palette.of(s.style(), s.era(), masonry);
        List<BoundingBox> taken = new ArrayList<>();
        for (Building other : s.buildings()) {
            taken.add(other.bounds(other.blueprint(s.style())));
        }
        if (s.project() != null) {
            Building current = s.project().building();
            taken.add(current.bounds(current.blueprint(s.style())));
        }
        BlockPos center = s.center();
        RandomSource random = level.getRandom();
        int maxRadius = s.radiusChunks() * 16 - Math.max(bp.width(), bp.depth()) / 2 - MARGIN;
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
                Integer floor = floorLevel(level, data, s, candidate, bp, palette, taken);
                if (floor != null) {
                    return new Building(type, candidate.origin().atY(floor), rotation, variant, masonry, s.era());
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
                                      Blueprint bp, Palette palette, List<BoundingBox> taken) {
        BoundingBox box = candidate.bounds(bp);
        for (BoundingBox other : taken) {
            if (overlaps(box, other)) {
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
                int top = groundLevel(level, x, z);
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
                    if (max - min > MAX_SLOPE) {
                        return null;
                    }
                } else if (!isOpenGround(ground, palette)) {
                    return null;
                }
            }
        }
        if (columns == 0) {
            return null;
        }
        return (int) Math.round((double) sum / columns);
    }

    /**
     * Height of the natural ground in a column, looking through trees, plants and snow. Logs only count as trees
     * under leaves, so a log cabin is not mistaken for a forest.
     */
    public static int groundLevel(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        boolean underLeaves = false;
        for (int depth = 0; depth < MAX_TREE_HEIGHT && y > level.getMinBuildHeight(); depth++) {
            BlockState state = level.getBlockState(pos.setY(y));
            if (state.is(BlockTags.LEAVES)) {
                underLeaves = true;
            } else if (!(isVegetation(state) && (underLeaves || !state.is(BlockTags.LOGS)))) {
                return y;
            }
            y--;
        }
        return y;
    }

    /** Trees, bushes, flowers, grass, snow and air: what the builders clear away without a second thought. */
    public static boolean isVegetation(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(Blocks.SNOW)
                || state.is(Blocks.BAMBOO) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.BEE_NEST) || state.is(Blocks.COCOA) || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.VINE);
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
