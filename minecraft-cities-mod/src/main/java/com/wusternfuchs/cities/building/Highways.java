package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.building.ConstructionProject.Placement;
import com.wusternfuchs.cities.diplomacy.DiplomacyManager;
import com.wusternfuchs.cities.diplomacy.Relation;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.SettlementTier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Roads between friendly settlements. Each side builds its own half, from its center to the midpoint, so the two
 * halves meet. A highway is stored as a building at the builder's center whose variant packs the offset to the
 * other settlement's center.
 */
public final class Highways {
    public static final int RANGE = 256;
    private static final int SKIP_NEAR_CENTER = 6;
    private static final int LAMP_SPACING = 24;

    private Highways() {
    }

    static int pack(int dx, int dz) {
        return (dx << 16) | (dz & 0xFFFF);
    }

    static BlockPos target(Building highway) {
        int packed = highway.variant();
        return highway.origin().offset(packed >> 16, 0, (short) packed);
    }

    public static boolean hasHighwayTo(Settlement s, Settlement other) {
        for (Building building : s.buildings()) {
            if (building.type() == BuildingType.HIGHWAY) {
                BlockPos end = target(building);
                if (end.getX() == other.center().getX() && end.getZ() == other.center().getZ()) {
                    return true;
                }
            }
        }
        if (s.project() != null && s.project().building().type() == BuildingType.HIGHWAY) {
            BlockPos end = target(s.project().building());
            return end.getX() == other.center().getX() && end.getZ() == other.center().getZ();
        }
        return false;
    }

    /** Whether a road links the two settlements (built from either end). */
    public static boolean connected(Settlement a, Settlement b) {
        return hasHighwayTo(a, b) || hasHighwayTo(b, a);
    }

    /** Starts a road towards the nearest friendly neighbour that has none yet. */
    static boolean start(ServerLevel level, SettlementData data, Settlement s) {
        if (s.tier().ordinal() < SettlementTier.VILLAGE.ordinal()) {
            return false;
        }
        Settlement best = null;
        double bestDistance = (double) RANGE * RANGE;
        for (Settlement other : data.all()) {
            double distance = s.center().distSqr(other.center());
            if (other != s && distance <= bestDistance
                    && DiplomacyManager.relation(s, other).ordinal() >= Relation.FRIENDLY.ordinal()
                    && !hasHighwayTo(s, other)) {
                best = other;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return false;
        }
        int dx = best.center().getX() - s.center().getX();
        int dz = best.center().getZ() - s.center().getZ();
        s.setProject(new ConstructionProject(new Building(BuildingType.HIGHWAY, s.center(), Rotation.NONE, pack(dx, dz))));
        s.record(SettlementManager.day(level), "highway", best.name());
        data.setDirty();
        return true;
    }

    /**
     * A straight road over the ground from near the center to the midpoint, with plank bridges over water and a
     * lamp post every few dozen blocks. Columns in unloaded chunks are left out.
     */
    static List<List<Placement>> plan(ServerLevel level, Settlement s, Building highway, Palette palette) {
        List<List<Placement>> steps = new ArrayList<>();
        BlockPos from = highway.origin();
        BlockPos end = target(highway);
        int mx = (end.getX() - from.getX()) / 2;
        int mz = (end.getZ() - from.getZ()) / 2;
        int length = Math.max(Math.abs(mx), Math.abs(mz));
        if (length == 0) {
            return steps;
        }
        boolean paved = s.tier().ordinal() >= SettlementTier.SMALL_TOWN.ordinal();
        BlockState road = paved ? palette.road() : Blocks.DIRT_PATH.defaultBlockState();
        List<BoundingBox> obstacles = new ArrayList<>();
        for (Building other : s.buildings()) {
            if (other.type() != BuildingType.HIGHWAY && other.type() != BuildingType.WALL) {
                obstacles.add(other.bounds(other.blueprint(s.style())).inflatedBy(1));
            }
        }
        // Lamps stand beside the road, on the side perpendicular to it.
        int sideX = Math.abs(mx) >= Math.abs(mz) ? 0 : 2;
        int sideZ = sideX == 0 ? 2 : 0;

        for (int i = SKIP_NEAR_CENTER; i <= length; i++) {
            int x = from.getX() + Math.round((float) mx * i / length);
            int z = from.getZ() + Math.round((float) mz * i / length);
            BlockPos column = new BlockPos(x, from.getY(), z);
            if (!level.isLoaded(column) || blocked(obstacles, column)) {
                continue;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
            BlockState ground = level.getBlockState(top);
            if (ground.is(Blocks.WATER)) {
                steps.add(List.of(new Placement(top, palette.floor(), true)));
            } else if (TownPlanner.isBuildableGround(ground) || ground.is(Blocks.DIRT_PATH)) {
                BlockState above = level.getBlockState(top.above());
                if (!above.isAir() && above.canBeReplaced()) {
                    steps.add(List.of(new Placement(top.above(), Blocks.AIR.defaultBlockState(), false)));
                }
                steps.add(List.of(new Placement(top, road, false)));
            } else {
                continue;
            }
            if (i % LAMP_SPACING == 0) {
                BlockPos post = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.offset(sideX, 0, sideZ));
                if (level.isLoaded(post) && level.getBlockState(post).isAir()
                        && TownPlanner.isBuildableGround(level.getBlockState(post.below()))) {
                    steps.add(List.of(new Placement(post, palette.fence(), true)));
                    steps.add(List.of(new Placement(post.above(), palette.fence(), true)));
                    steps.add(List.of(new Placement(post.above(2), Blocks.LANTERN.defaultBlockState(), true)));
                }
            }
        }
        return steps;
    }

    private static boolean blocked(List<BoundingBox> obstacles, BlockPos column) {
        for (BoundingBox box : obstacles) {
            if (column.getX() >= box.minX() && column.getX() <= box.maxX()
                    && column.getZ() >= box.minZ() && column.getZ() <= box.maxZ()) {
                return true;
            }
        }
        return false;
    }
}
