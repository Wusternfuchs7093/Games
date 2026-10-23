package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.block.CitiesBlocks;
import com.wusternfuchs.cities.settlement.ArchitectureStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Procedural buildings in the style of a culture and period ({@link Palette}).
 * <p>
 * Every plot has a one-block ring around the building's body for roof eaves, shutters and towers; the body's
 * walls stand at x = 1 .. width-2 and z = 1 .. depth-2. Storeys are four blocks high. The front faces +z (south),
 * the door is in the middle of the front wall and the entrance is the spot just outside the plot.
 * A plot's width and depth depend only on the building type, culture and variant, never on the period, so a
 * building can be rebuilt in the style of a new age on the same spot.
 */
public final class Blueprints {
    private static final Block[] WORKSTATIONS = {
            Blocks.SMITHING_TABLE, Blocks.FLETCHING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.LOOM,
            Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.GRINDSTONE, Blocks.STONECUTTER,
            Blocks.BREWING_STAND, Blocks.LECTERN, Blocks.CAULDRON, Blocks.COMPOSTER
    };
    /** Our own workstations follow the vanilla ones in the workshop variants. */
    private static final int OWN_WORKSTATIONS = 3;
    private static final Block[] CROPS = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
    private static final int[][] HOUSE_SIZES = {{7, 7}, {9, 7}, {7, 9}, {9, 9}, {11, 9}, {9, 11}};

    private Blueprints() {
    }

    public static int workstationVariants() {
        return WORKSTATIONS.length + OWN_WORKSTATIONS;
    }

    public static int houseVariants() {
        return HOUSE_SIZES.length;
    }

    // ------------------------------------------------------------------ homes

    public static Blueprint house(Palette p, int variant) {
        int[] size = HOUSE_SIZES[Math.floorMod(variant, HOUSE_SIZES.length)];
        int w = size[0];
        int d = size[1];
        int floors = w * d <= 63 ? Math.min(p.floors(), 2) : p.floors();
        Canvas c = new Canvas(w, d);
        int top = shell(c, p, floors, p.roof(), false);
        if (floors == 1) {
            beds(c, p, 1, w - 5);
            c.set(w - 3, 1, d - 3, Blocks.CRAFTING_TABLE.defaultBlockState());
        } else {
            kitchen(c, p);
            for (int storey = 1; storey < floors; storey++) {
                beds(c, p, 4 * storey + 1, w - 5);
            }
        }
        chimney(c, p, top);
        return c.build();
    }

    /** A roomy house for the town's elite: beds upstairs, books, carpets and light. */
    public static Blueprint manor(Palette p, int variant) {
        int w = 13;
        int d = 11;
        Canvas c = new Canvas(w, d);
        int floors = Math.max(2, Math.min(3, p.floors()));
        int top = shell(c, p, floors, p.roof(), false);
        c.fill(3, 1, 2, w - 4, 1, 2, Blocks.BOOKSHELF.defaultBlockState());
        c.fill(4, 1, 4, w - 5, 1, d - 5, Blocks.RED_CARPET.defaultBlockState());
        c.set(w - 3, 1, d - 3, Blocks.CRAFTING_TABLE.defaultBlockState());
        c.set(3, 2, 2, Blocks.LANTERN.defaultBlockState());
        c.set(w - 4, 2, 2, Blocks.LANTERN.defaultBlockState());
        for (int storey = 1; storey < floors; storey++) {
            beds(c, p, 4 * storey + 1, 2);
            c.set(w - 3, 4 * storey + 1, 2, Blocks.FLOWER_POT.defaultBlockState());
        }
        chimney(c, p, top);
        return c.build();
    }

    // ------------------------------------------------------------------ work

    public static Blueprint workshop(Palette p, int variant) {
        int w = 9;
        Canvas c = new Canvas(w, 9);
        shell(c, p, Math.min(2, p.floors()), p.roof(), false);
        c.set(2, 1, 2, workstation(variant));
        c.set(w - 3, 1, 2, workstation(variant + 5));
        return c.build();
    }

    public static Blueprint warehouse(Palette p, int variant) {
        int w = 11;
        int d = 9;
        Canvas c = new Canvas(w, d);
        shell(c, p, 1, p.roof(), false);
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        for (int x = 2; x <= w - 3; x += 2) {
            c.storage(x, 1, 2, chest);
        }
        c.set(2, 1, d - 4, Blocks.HAY_BLOCK.defaultBlockState());
        c.set(w - 3, 1, d - 4, Blocks.BARREL.defaultBlockState());
        c.set(w - 3, 1, d - 3, CitiesBlocks.BUILDER_BENCH.get().defaultBlockState());
        return c.build();
    }

    /** The culture's own trade: a clockmaker, carpet weavers, potters, a smokehouse, a mead hall and so on. */
    public static Blueprint craftHouse(Palette p, int variant) {
        int w = 9;
        int d = 9;
        Canvas c = new Canvas(w, d);
        shell(c, p, Math.min(2, p.floors()), p.roof(), false);
        c.set(w - 3, 1, d - 3, CitiesBlocks.CRAFT_BENCH.get().defaultBlockState());
        BlockState a;
        BlockState b;
        switch (p.style().craft()) {
            case CLOCKMAKING -> {
                a = Blocks.NOTE_BLOCK.defaultBlockState();
                b = Blocks.CRAFTING_TABLE.defaultBlockState();
            }
            case CARPETS -> {
                a = facing(Blocks.LOOM.defaultBlockState(), Direction.SOUTH);
                b = Blocks.RED_WOOL.defaultBlockState();
                c.fill(3, 1, 4, w - 4, 1, 5, Blocks.CYAN_CARPET.defaultBlockState());
            }
            case POTTERY -> {
                a = facing(Blocks.FURNACE.defaultBlockState(), Direction.SOUTH);
                b = Blocks.DECORATED_POT.defaultBlockState();
                c.set(4, 1, 2, Blocks.CLAY.defaultBlockState());
            }
            case SMOKEHOUSE -> {
                a = facing(Blocks.SMOKER.defaultBlockState(), Direction.SOUTH);
                b = Blocks.BARREL.defaultBlockState();
            }
            case HONEY -> {
                a = facing(Blocks.BEEHIVE.defaultBlockState(), Direction.SOUTH);
                b = Blocks.HONEYCOMB_BLOCK.defaultBlockState();
                c.set(4, 1, 2, Blocks.CANDLE.defaultBlockState());
            }
            case WOOL -> {
                a = facing(Blocks.LOOM.defaultBlockState(), Direction.SOUTH);
                b = Blocks.WHITE_WOOL.defaultBlockState();
            }
            case SWORDSMITH -> {
                a = Blocks.ANVIL.defaultBlockState();
                b = grindstone();
            }
            case CHOCOLATE -> {
                a = Blocks.CAULDRON.defaultBlockState();
                b = Blocks.BROWN_TERRACOTTA.defaultBlockState();
            }
            default -> {
                a = Blocks.CRAFTING_TABLE.defaultBlockState();
                b = Blocks.BARREL.defaultBlockState();
            }
        }
        c.set(2, 1, 2, a);
        c.set(w - 3, 1, 2, b);
        c.set(2, 1, d - 4, Blocks.BARREL.defaultBlockState());
        return c.build();
    }

    // ------------------------------------------------------------------ public buildings

    public static Blueprint townHall(Palette p, int variant) {
        int w = 13;
        int d = 11;
        Canvas c = new Canvas(w, d);
        int floors = Math.max(2, Math.min(3, p.floors()));
        int top = shell(c, p, floors, p.roof(), false);
        int cx = w / 2;
        c.set(cx, 1, 2, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH));
        for (int z = 3; z <= d - 3; z++) {
            c.set(cx, 1, z, Blocks.RED_CARPET.defaultBlockState());
        }
        c.set(3, 1, d - 4, Blocks.BELL.defaultBlockState());
        c.set(w - 3, 1, d - 3, CitiesBlocks.JOB_BOARD.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        c.fill(2, 1, 2, 3, 1, 2, Blocks.BOOKSHELF.defaultBlockState());
        c.fill(w - 4, 1, 2, w - 3, 1, 2, Blocks.BOOKSHELF.defaultBlockState());
        for (int storey = 1; storey < floors; storey++) {
            c.fill(3, 4 * storey + 1, 2, w - 4, 4 * storey + 1, 2, Blocks.BOOKSHELF.defaultBlockState());
        }
        crown(c, p, cx, d / 2, 4 * floors, top);
        return c.build();
    }

    public static Blueprint library(Palette p, int variant) {
        int w = 11;
        int d = 9;
        Canvas c = new Canvas(w, d);
        int floors = Math.min(2, p.floors());
        shell(c, p, floors, p.roof(), false);
        for (int storey = 0; storey < floors; storey++) {
            int y = 4 * storey + 1;
            c.fill(3, y, 2, w - 3, y + 1, 2, Blocks.BOOKSHELF.defaultBlockState());
        }
        BlockState lectern = Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH);
        c.set(3, 1, 4, lectern);
        c.set(w - 4, 1, 4, lectern);
        c.set(4, 1, d - 3, CitiesBlocks.SCHOLAR_DESK.get().defaultBlockState());
        c.set(w - 3, 1, d - 3, CitiesBlocks.SCHOLAR_DESK.get().defaultBlockState());
        if (floors > 1) {
            c.set(w - 3, 5, d - 3, CitiesBlocks.SCHOLAR_DESK.get().defaultBlockState());
        }
        return c.build();
    }

    public static Blueprint hospital(Palette p, int variant) {
        int w = 11;
        int d = 9;
        Canvas c = new Canvas(w, d);
        shell(c, p, Math.min(2, p.floors()), p.roof(), false);
        c.set(3, 1, 2, Blocks.BREWING_STAND.defaultBlockState());
        c.set(w - 4, 1, 2, Blocks.BREWING_STAND.defaultBlockState());
        c.set(w / 2, 1, 2, Blocks.CAULDRON.defaultBlockState());
        BlockState cot = Blocks.WHITE_CARPET.defaultBlockState();
        c.fill(3, 1, 4, 4, 1, 5, cot);
        c.fill(w - 5, 1, 4, w - 4, 1, 5, cot);
        return c.build();
    }

    /** Tables with stools, barrels and guest rooms upstairs: where rumours are told and swords are hired. */
    public static Blueprint tavern(Palette p, int variant) {
        int w = 11;
        int d = 9;
        Canvas c = new Canvas(w, d);
        int top = shell(c, p, 2, p.roof(), false);
        BlockState top2 = Blocks.OAK_PRESSURE_PLATE.defaultBlockState();
        for (int x : new int[]{4, w - 4}) {
            c.set(x, 1, 4, p.fence());
            c.set(x, 2, 4, top2);
            c.set(x - 1, 1, 4, stairs(p.wallStairs(), Direction.EAST));
            c.set(x + 1, 1, 4, stairs(p.wallStairs(), Direction.WEST));
        }
        c.set(3, 1, 2, Blocks.BARREL.defaultBlockState());
        c.set(4, 1, 2, Blocks.BARREL.defaultBlockState());
        c.set(6, 1, 2, Blocks.CRAFTING_TABLE.defaultBlockState());
        c.set(w - 3, 1, d - 3, CitiesBlocks.JOB_BOARD.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        beds(c, p, 5, 2);
        chimney(c, p, top);
        return c.build();
    }

    /** The culture's house of worship: church, mosque, onion-domed church, Djenné mosque, pagoda, pyramid. */
    public static Blueprint temple(Palette p, int variant) {
        return switch (p.style()) {
            case DESERT -> mosque(p);
            case TAIGA -> russianChurch(p);
            case SAVANNA -> mudMosque(p);
            case CHERRY -> pagoda(p);
            case JUNGLE -> pyramid(p);
            default -> church(p);
        };
    }

    // ------------------------------------------------------------------ open-air

    public static Blueprint market(Palette p, int variant) {
        Canvas c = new Canvas(9, 7);
        c.fill(0, 0, 0, 8, 0, 6, p.foundation());
        for (int x = 0; x <= 8; x += 8) {
            for (int z = 0; z <= 6; z += 6) {
                c.fill(x, 1, z, x, 2, z, p.fence());
            }
        }
        Block[] colours = canopy(p.style());
        for (int x = 0; x <= 8; x++) {
            c.fill(x, 3, 0, x, 3, 6, colours[x % 2].defaultBlockState());
        }
        c.fill(1, 1, 4, 3, 1, 4, p.roofSlab());
        c.fill(5, 1, 4, 7, 1, 4, p.roofSlab());
        Block[] goods = {Blocks.HAY_BLOCK, Blocks.MELON, Blocks.PUMPKIN, Blocks.BARREL};
        c.set(2, 1, 1, goods[Math.floorMod(variant, goods.length)].defaultBlockState());
        c.set(4, 1, 1, Blocks.MELON.defaultBlockState());
        c.set(6, 1, 1, CitiesBlocks.JOB_BOARD.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        c.set(2, 2, 3, hanging);
        c.set(6, 2, 3, hanging);
        c.entrance(4, 7);
        return c.build();
    }

    public static Blueprint well(Palette p, int variant) {
        Canvas c = new Canvas(5, 5);
        c.fill(0, 0, 0, 4, 0, 4, p.foundation());
        c.fill(1, 0, 1, 3, 0, 3, Blocks.WATER.defaultBlockState());
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (x == 0 || x == 4 || z == 0 || z == 4) {
                    c.set(x, 1, z, p.foundation());
                }
            }
        }
        for (int x = 0; x <= 4; x += 4) {
            for (int z = 0; z <= 4; z += 4) {
                c.set(x, 2, z, p.fence());
            }
        }
        c.fill(0, 3, 0, 4, 3, 4, p.roofSlab());
        c.entrance(2, 5);
        return c.build();
    }

    public static Blueprint farm(Palette p, int variant) {
        Canvas c = new Canvas(9, 9);
        c.fill(0, 0, 0, 8, 0, 8, p.frame());
        c.fill(1, 0, 1, 7, 0, 7, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        c.set(4, 0, 4, Blocks.WATER.defaultBlockState());
        BlockState crop = CROPS[Math.floorMod(variant, CROPS.length)].defaultBlockState();
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                if (x != 4 || z != 4) {
                    c.set(x, 1, z, crop);
                }
            }
        }
        c.set(0, 1, 0, Blocks.COMPOSTER.defaultBlockState());
        c.entrance(4, 9);
        return c.build();
    }

    /** A fenced graveyard: stones and flowers in two rows along a gravel path. */
    public static Blueprint cemetery(Palette p, int variant) {
        Canvas c = new Canvas(7, 7);
        c.fill(0, 0, 0, 6, 0, 6, Blocks.COARSE_DIRT.defaultBlockState());
        c.fill(3, 0, 1, 3, 0, 6, Blocks.GRAVEL.defaultBlockState());
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                if (x == 0 || x == 6 || z == 0 || z == 6) {
                    c.set(x, 1, z, p.fence());
                }
            }
        }
        c.set(3, 1, 6, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH));
        for (int x : new int[]{1, 5}) {
            for (int z : new int[]{1, 3}) {
                c.set(x, 1, z, Blocks.COBBLESTONE_WALL.defaultBlockState());
                c.set(x, 1, z + 1, Blocks.POPPY.defaultBlockState());
            }
        }
        c.set(0, 2, 0, Blocks.LANTERN.defaultBlockState());
        c.set(6, 2, 0, Blocks.LANTERN.defaultBlockState());
        c.entrance(3, 7);
        return c.build();
    }

    /** A square tower with a ladder, a railed platform and a roof in the local manner. */
    public static Blueprint watchtower(Palette p, int variant) {
        Canvas c = new Canvas(7, 7);
        c.fill(1, 0, 1, 5, 0, 5, p.foundation());
        for (int y = 1; y <= 7; y++) {
            int yy = y;
            perimeter(1, 1, 5, 5, (x, z, corner) -> c.set(x, yy, z, corner ? p.frame() : yy <= 3 ? p.lower() : p.walls()));
        }
        c.set(1, 4, 3, p.pane());
        c.set(5, 4, 3, p.pane());
        c.fill(0, 8, 0, 6, 8, 6, p.roofBlock());
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = 1; y <= 8; y++) {
            c.set(3, y, 2, ladder);
        }
        perimeter(0, 0, 6, 6, (x, z, corner) -> c.set(x, 9, z, corner ? p.frame() : p.fence()));
        boolean flat = p.roof() == Palette.Roof.FLAT;
        if (flat) {
            perimeter(0, 0, 6, 6, (x, z, corner) -> {
                if (corner) {
                    c.set(x, 10, z, p.light());
                }
            });
        } else {
            for (int x = 0; x <= 6; x += 6) {
                for (int z = 0; z <= 6; z += 6) {
                    c.set(x, 10, z, p.frame());
                }
            }
            hip(c, p, 0, 0, 6, 6, 11, p.roof() == Palette.Roof.STEEP ? 2 : 1, p.roofStairs() == null);
            c.set(3, 9, 3, Blocks.LANTERN.defaultBlockState());
        }
        c.door(3, 1, 5, p.door(), Direction.SOUTH);
        c.entrance(3, 7);
        return c.build();
    }

    public static Blueprint lamp(Palette p, int variant) {
        Canvas c = new Canvas(1, 1);
        c.set(0, 0, 0, p.foundation());
        c.fill(0, 1, 0, 0, 2, 0, p.fence());
        c.set(0, 3, 0, p.light());
        return c.build();
    }

    // ------------------------------------------------------------------ temples

    /** A nave with tall windows and pews, and a bell tower with a spire over the front door. */
    private static Blueprint church(Palette p) {
        int w = 11;
        int d = 17;
        Canvas c = new Canvas(w, d);
        Palette.Roof roof = p.roof() == Palette.Roof.FLAT ? Palette.Roof.FLAT : Palette.Roof.STEEP;
        int top = shell(c, p, 2, roof, true);
        int cx = w / 2;
        pews(c, p, 6, d - 8);
        altar(c, p, cx);
        // Tower over the entrance, open into the nave.
        int tz0 = d - 6;
        int tz1 = d - 2;
        int towerTop = top + 4;
        for (int y = 1; y <= towerTop; y++) {
            int yy = y;
            perimeter(cx - 2, tz0, cx + 2, tz1, (x, z, corner) -> c.set(x, yy, z, corner ? p.frame() : p.walls()));
        }
        c.fill(cx - 1, 4 * 2, tz0 + 1, cx + 1, 4 * 2, tz1 - 1, p.floor());
        c.fill(cx, 1, tz0, cx, 2, tz0, air());
        for (int[] o : new int[][]{{0, -2}, {0, 2}, {-2, 0}, {2, 0}}) {
            c.fill(cx + o[0], towerTop - 2, (tz0 + tz1) / 2 + o[1], cx + o[0], towerTop - 1, (tz0 + tz1) / 2 + o[1], air());
        }
        c.door(cx, 1, tz1, p.door(), Direction.SOUTH);
        int spireTop = hip(c, p, cx - 2, tz0, cx + 2, tz1, towerTop + 1, 2, p.roofStairs() == null);
        c.set(cx, spireTop + 1, (tz0 + tz1) / 2, Blocks.LIGHTNING_ROD.defaultBlockState());
        return c.build();
    }

    /** A flat-roofed prayer hall under a great dome, with a minaret at the corner. */
    private static Blueprint mosque(Palette p) {
        int w = 13;
        int d = 13;
        Canvas c = new Canvas(w, d);
        int base = 8;
        shell(c, p, 2, Palette.Roof.FLAT, true);
        c.fill(2, 1, 2, w - 3, 1, d - 3, Blocks.RED_CARPET.defaultBlockState());
        c.fill(w / 2, 1, 3, w / 2, 1, d - 3, Blocks.BLUE_CARPET.defaultBlockState());
        c.set(w / 2, 1, 2, Blocks.BREWING_STAND.defaultBlockState());
        c.set(w / 2, 2, 1, p.accent());
        drawProfile(c, w / 2, d / 2, base + 1, List.of(3, 3), p.walls());
        dome(c, w / 2, d / 2, base + 3, 3, p.accent(), false);
        // Minaret on the front left corner.
        int mx0 = 0;
        int mz0 = d - 3;
        int minaretTop = base + 9;
        for (int y = 0; y <= minaretTop; y++) {
            int yy = y;
            perimeter(mx0, mz0, mx0 + 2, mz0 + 2, (x, z, corner) -> c.set(x, yy, z, corner ? p.frame() : p.walls()));
        }
        perimeter(mx0, mz0, mx0 + 2, mz0 + 2, (x, z, corner) -> c.set(x, minaretTop - 3, z, p.trim()));
        dome(c, mx0 + 1, mz0 + 1, minaretTop + 1, 1, p.accent(), false);
        c.set(mx0 + 1, minaretTop + 3, mz0 + 1, Blocks.LIGHTNING_ROD.defaultBlockState());
        return c.build();
    }

    /** A white church with a drum and a big onion dome, small domes on the corners. */
    private static Blueprint russianChurch(Palette p) {
        int w = 11;
        int d = 13;
        Canvas c = new Canvas(w, d);
        int top = shell(c, p, 2, p.roof(), true);
        int cx = w / 2;
        int cz = d / 2;
        pews(c, p, 5, d - 4);
        altar(c, p, cx);
        int drumTop = top + 3;
        for (int y = 8; y <= drumTop; y++) {
            int yy = y;
            perimeter(cx - 1, cz - 1, cx + 1, cz + 1, (x, z, corner) -> c.set(x, yy, z, corner ? p.frame() : p.walls()));
        }
        onion(c, cx, cz, drumTop + 1, 2, p.accent());
        if (p.roof() == Palette.Roof.FLAT) {
            for (int[] corner : new int[][]{{1, 1}, {w - 2, 1}, {1, d - 2}, {w - 2, d - 2}}) {
                c.fill(corner[0], 9, corner[1], corner[0], 11, corner[1], p.frame());
                onion(c, corner[0], corner[1], 12, 1, p.accent());
            }
        } else {
            // Smaller domes stand on the ridge in front of and behind the drum.
            for (int z : new int[]{2, d - 3}) {
                c.set(cx, top + 1, z, p.frame());
                onion(c, cx, z, top + 2, 1, p.accent());
            }
        }
        return c.build();
    }

    /** Mud walls bristling with wooden poles and three towers on the front: the Great Mosque of Djenné. */
    private static Blueprint mudMosque(Palette p) {
        int w = 13;
        int d = 11;
        Canvas c = new Canvas(w, d);
        shell(c, p, 2, Palette.Roof.FLAT, true);
        c.set(w / 2, 1, 2, Blocks.BREWING_STAND.defaultBlockState());
        c.fill(2, 1, 3, w - 3, 1, d - 4, Blocks.ORANGE_CARPET.defaultBlockState());
        for (int x : new int[]{1, w / 2 - 2, w / 2 + 2, w - 2}) {
            c.fill(x, 1, d - 2, x, 12, d - 2, p.frame());
            c.set(x, 13, d - 2, p.accent());
            c.set(x, 14, d - 2, p.fence());
        }
        for (int y = 3; y <= 7; y += 4) {
            int yy = y;
            perimeter(0, 0, w - 1, d - 1, (x, z, corner) -> {
                if (!corner && (x + z) % 3 == 0) {
                    c.set(x, yy, z, p.fence());
                }
            });
        }
        return c.build();
    }

    /** A temple hall under a curved roof, with two more pagoda tiers above it. */
    private static Blueprint pagoda(Palette p) {
        int w = 11;
        int d = 11;
        Canvas c = new Canvas(w, d);
        shell(c, p, 1, Palette.Roof.PAGODA, false);
        int cx = w / 2;
        int cz = d / 2;
        c.set(cx, 1, 2, Blocks.BREWING_STAND.defaultBlockState());
        c.set(cx - 2, 1, 2, Blocks.LANTERN.defaultBlockState());
        c.set(cx + 2, 1, 2, Blocks.LANTERN.defaultBlockState());
        int y = 7;
        for (int tier = 0; tier < 2; tier++) {
            int yy = y;
            for (int k = 0; k < 3; k++) {
                int level = yy + k;
                perimeter(cx - 1, cz - 1, cx + 1, cz + 1, (x, z, corner) -> c.set(x, level, z, corner ? p.frame() : p.walls()));
            }
            int eave = yy + 3;
            hipRing(c, p, cx - 2, cz - 2, cx + 2, cz + 2, eave);
            c.fill(cx - 1, eave, cz - 1, cx + 1, eave, cz + 1, p.roofBlock());
            for (int[] corner : new int[][]{{cx - 2, cz - 2}, {cx + 2, cz - 2}, {cx - 2, cz + 2}, {cx + 2, cz + 2}}) {
                c.set(corner[0], eave + 1, corner[1], p.roofSlab());
            }
            y = eave + 1;
        }
        int peak = hip(c, p, cx - 1, cz - 1, cx + 1, cz + 1, y, 1, p.roofStairs() == null);
        c.set(cx, peak + 1, cz, Blocks.LIGHTNING_ROD.defaultBlockState());
        return c.build();
    }

    /** A stepped pyramid with a staircase up the front and a shrine on the top. */
    private static Blueprint pyramid(Palette p) {
        int w = 13;
        int d = 13;
        Canvas c = new Canvas(w, d);
        int levels = 4;
        for (int level = 0; level < levels; level++) {
            int lv = level;
            perimeter(level, level, w - 1 - level, d - 1 - level,
                    (x, z, corner) -> c.set(x, lv, z, lv == 0 ? p.foundation() : p.walls()));
        }
        c.fill(levels, levels, levels, w - 1 - levels, levels, d - 1 - levels, p.walls());
        int cx = w / 2;
        for (int level = 0; level < levels; level++) {
            for (int x = cx - 1; x <= cx + 1; x++) {
                c.set(x, level, d - 1 - level, stairs(p.roofStairs() == null ? Blocks.STONE_BRICK_STAIRS : p.roofStairs(),
                        Direction.NORTH));
            }
        }
        int s0 = levels;
        int s1 = w - 1 - levels;
        int floor = levels + 1;
        for (int y = floor; y <= floor + 2; y++) {
            int yy = y;
            perimeter(s0, s0, s1, s1, (x, z, corner) -> c.set(x, yy, z, corner ? p.frame() : p.walls()));
        }
        c.fill(s0, floor + 3, s0, s1, floor + 3, s1, p.roofBlock());
        for (int x = s0; x <= s1; x += 2) {
            c.fill(x, floor + 4, cx, x, floor + 5, cx, p.accent());
        }
        c.door(cx, floor, s1, p.door(), Direction.SOUTH);
        c.set(cx, floor, s0 + 1, Blocks.BREWING_STAND.defaultBlockState());
        c.set(cx, floor + 2, s0 + 1, wallTorch(Direction.SOUTH));
        c.entrance(cx, d);
        return c.build();
    }

    // ------------------------------------------------------------------ shell

    /**
     * Raises the body of a building: floor, walls with windows, storey floors with a ladder between them, the
     * front door, shutters and the roof. A {@code hall} has no floors between its storeys, only tall windows.
     * Returns the highest y of the roof.
     */
    private static int shell(Canvas c, Palette p, int floors, Palette.Roof roof, boolean hall) {
        int w = c.width;
        int d = c.depth;
        int x0 = 1;
        int x1 = w - 2;
        int z0 = 1;
        int z1 = d - 2;
        int roofBase = 4 * floors;
        boolean modern = p.period() == Period.MODERN;

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                c.set(x, 0, z, edge ? p.foundation() : p.floor());
            }
        }
        for (int storey = 0; storey < floors; storey++) {
            int base = 4 * storey;
            if (storey > 0 && !hall) {
                c.fill(x0 + 1, base, z0 + 1, x1 - 1, base, z1 - 1, p.floor());
            }
            int s = storey;
            for (int y = storey == 0 ? 1 : base; y <= base + 3; y++) {
                int dy = y - base;
                int yy = y;
                wallCells(x0, z0, x1, z1, (x, z, i, len, along, out) ->
                        c.set(x, yy, z, wallBlock(p, s, i, len, dy, along)));
            }
            // Windows, and shutters beside them.
            int ws = storey;
            wallCells(x0, z0, x1, z1, (x, z, i, len, along, out) -> {
                if (!windowAt(p, ws, i, len)) {
                    return;
                }
                if (out == Direction.SOUTH && ws == 0 && x == w / 2) {
                    return;
                }
                int low = modern || hall ? base + 1 : base + 2;
                int high = hall ? base + 3 : base + 2;
                if (hall && ws == 0) {
                    low = base + 2;
                }
                for (int y = low; y <= high; y++) {
                    c.set(x, y, z, p.pane());
                }
                if (p.shutter() != null && !modern) {
                    Direction side = along == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
                    for (Direction dir : new Direction[]{side, side.getOpposite()}) {
                        int sx = x + dir.getStepX() + out.getStepX();
                        int sz = z + dir.getStepZ() + out.getStepZ();
                        if (c.get(sx, base + 2, sz).isAir()) {
                            c.set(sx, base + 2, sz, p.shutter().defaultBlockState()
                                    .setValue(BlockStateProperties.HORIZONTAL_FACING, out)
                                    .setValue(BlockStateProperties.OPEN, true));
                        }
                    }
                }
            });
            if (!hall) {
                c.set(w / 2, base + 3, z0 + 1, wallTorch(Direction.SOUTH));
            }
        }
        if (floors > 1 && !hall) {
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
            for (int y = 1; y <= 4 * (floors - 1); y++) {
                c.set(x0 + 1, y, z1 - 1, ladder);
            }
        }
        c.door(w / 2, 1, z1, p.door(), Direction.SOUTH);
        c.set(w / 2 + 1, 1, z1 + 1, Blocks.LANTERN.defaultBlockState());
        c.entrance(w / 2, d);

        return switch (roof) {
            case GABLE -> gable(c, p, 0, 0, w - 1, d - 1, roofBase, false);
            case STEEP -> gable(c, p, 0, 0, w - 1, d - 1, roofBase, true);
            case FLAT -> flat(c, p, x0, z0, x1, z1, roofBase);
            case HIP -> {
                plate(c, p, x0, z0, x1, z1, roofBase);
                yield hip(c, p, 0, 0, w - 1, d - 1, roofBase, 1, p.roofStairs() == null);
            }
            case STEPPED -> {
                plate(c, p, x0, z0, x1, z1, roofBase);
                yield hip(c, p, 0, 0, w - 1, d - 1, roofBase, 1, true);
            }
            case PAGODA -> {
                plate(c, p, x0, z0, x1, z1, roofBase);
                int top = hip(c, p, 0, 0, w - 1, d - 1, roofBase, 1, p.roofStairs() == null);
                for (int x = 0; x <= w - 1; x += w - 1) {
                    for (int z = 0; z <= d - 1; z += d - 1) {
                        c.set(x, roofBase + 1, z, p.roofSlab());
                    }
                }
                yield top;
            }
        };
    }

    /** Wall block at position {@code i} of {@code len} along a wall, {@code dy} blocks above the storey base. */
    private static BlockState wallBlock(Palette p, int storey, int i, int len, int dy, Direction.Axis along) {
        if (i == 0 || i == len - 1) {
            return p.frame();
        }
        BlockState infill = storey == 0 ? p.lower() : p.walls();
        boolean timber = p.halfTimber() && (storey > 0 || p.lower() == p.walls());
        if (timber && (dy == 0 || dy == 3)) {
            return axis(p.frame(), along);
        }
        if (timber && i % 3 == 0) {
            return p.frame();
        }
        if (dy == 0 && !p.logWalls()) {
            return axis(p.frame(), along);
        }
        return p.logWalls() ? axis(infill, along) : infill;
    }

    private static boolean windowAt(Palette p, int storey, int i, int len) {
        if (i <= 0 || i >= len - 1) {
            return false;
        }
        if (p.period() == Period.MODERN) {
            return i % 3 != 0;
        }
        boolean timber = p.halfTimber() && (storey > 0 || p.lower() == p.walls());
        if (timber) {
            return i % 3 == 2;
        }
        return i % 2 == 0 && i >= 2 && i <= len - 3;
    }

    // ------------------------------------------------------------------ roofs

    /**
     * A pitched roof over the rectangle, ridge along its longer side, one block of overhang all around; the gable
     * ends and the tops of the side walls under it are walled up. Returns the ridge height.
     */
    private static int gable(Canvas c, Palette p, int x0, int z0, int x1, int z1, int base, boolean steep) {
        int w = x1 - x0 + 1;
        int d = z1 - z0 + 1;
        boolean alongZ = d >= w;
        int span = alongZ ? w : d;
        int length = alongZ ? d : w;
        int rise = steep ? 2 : 1;
        Direction loFacing = alongZ ? Direction.EAST : Direction.SOUTH;
        int top = base;
        for (int k = 0; k <= span / 2; k++) {
            int lo = k;
            int hi = span - 1 - k;
            if (lo > hi) {
                break;
            }
            int y = base + k * rise;
            top = y;
            for (int l = 0; l < length; l++) {
                if (lo == hi) {
                    put(c, alongZ, x0, z0, lo, l, y, p.roofSlab());
                    if (steep && k > 0) {
                        put(c, alongZ, x0, z0, lo, l, y - 1, p.roofBlock());
                    }
                } else {
                    put(c, alongZ, x0, z0, lo, l, y, slope(p, loFacing));
                    put(c, alongZ, x0, z0, hi, l, y, slope(p, loFacing.getOpposite()));
                    if (steep && k > 0) {
                        put(c, alongZ, x0, z0, lo, l, y - 1, p.roofBlock());
                        put(c, alongZ, x0, z0, hi, l, y - 1, p.roofBlock());
                    }
                }
            }
        }
        // Gable ends and wall plates under the slopes.
        Direction.Axis endAxis = alongZ ? Direction.Axis.X : Direction.Axis.Z;
        Direction.Axis sideAxis = alongZ ? Direction.Axis.Z : Direction.Axis.X;
        for (int j = 1; j <= span - 2; j++) {
            int k = Math.min(j, span - 1 - j);
            int surface = base + k * rise;
            int highest = surface - (steep && k > 0 ? 2 : 1);
            for (int y = base; y <= highest; y++) {
                put(c, alongZ, x0, z0, j, 1, y, gableBlock(p, endAxis));
                put(c, alongZ, x0, z0, j, length - 2, y, gableBlock(p, endAxis));
                if (j == 1 || j == span - 2) {
                    for (int l = 2; l <= length - 3; l++) {
                        put(c, alongZ, x0, z0, j, l, y, gableBlock(p, sideAxis));
                    }
                }
            }
        }
        return top;
    }

    /** Places at span position {@code s}, length position {@code l} of a roof. */
    private static void put(Canvas c, boolean alongZ, int x0, int z0, int s, int l, int y, BlockState state) {
        if (alongZ) {
            c.set(x0 + s, y, z0 + l, state);
        } else {
            c.set(x0 + l, y, z0 + s, state);
        }
    }

    /**
     * Concentric rings rising {@code rise} blocks per step until they meet. With rise 2 each ring sits on a full
     * block, which makes a spire. {@code blocks}: full blocks instead of stairs (thatch, stepped roofs).
     */
    private static int hip(Canvas c, Palette p, int x0, int z0, int x1, int z1, int base, int rise, boolean blocks) {
        int top = base;
        for (int k = 0; ; k++) {
            int ax = x0 + k;
            int bx = x1 - k;
            int az = z0 + k;
            int bz = z1 - k;
            if (ax > bx || az > bz) {
                break;
            }
            int y = base + k * rise;
            top = y;
            if (rise > 1 && k > 0) {
                ring(c, ax, az, bx, bz, y - 1, p.roofBlock());
            }
            if (ax == bx || az == bz) {
                c.fill(ax, y, az, bx, y, bz, blocks ? p.roofBlock() : p.roofSlab());
                break;
            }
            if (blocks) {
                ring(c, ax, az, bx, bz, y, p.roofBlock());
            } else {
                hipRing(c, p, ax, az, bx, bz, y);
            }
        }
        return top;
    }

    /** One ring of stairs, all facing inwards. */
    private static void hipRing(Canvas c, Palette p, int ax, int az, int bx, int bz, int y) {
        for (int x = ax; x <= bx; x++) {
            c.set(x, y, az, slope(p, Direction.SOUTH));
            c.set(x, y, bz, slope(p, Direction.NORTH));
        }
        for (int z = az + 1; z <= bz - 1; z++) {
            c.set(ax, y, z, slope(p, Direction.EAST));
            c.set(bx, y, z, slope(p, Direction.WEST));
        }
    }

    /** A flat roof with a parapet; crenellated in the old Arab towns, with poles in the Sahel. */
    private static int flat(Canvas c, Palette p, int x0, int z0, int x1, int z1, int base) {
        c.fill(x0, base, z0, x1, base, z1, p.roofBlock());
        ring(c, x0, z0, x1, z1, base + 1, p.trim());
        int top = base + 1;
        boolean crenels = p.style() == ArchitectureStyle.DESERT && p.period().ordinal() <= Period.RENAISSANCE.ordinal();
        if (crenels || p.poles()) {
            int[] index = {0};
            perimeter(x0, z0, x1, z1, (x, z, corner) -> {
                if (index[0]++ % 2 == 0) {
                    c.set(x, base + 2, z, crenels ? p.trim() : p.fence());
                }
            });
            top = base + 2;
        }
        return top;
    }

    /** Tops the walls with one course so a sloping roof has something to rest on. */
    private static void plate(Canvas c, Palette p, int x0, int z0, int x1, int z1, int y) {
        perimeter(x0, z0, x1, z1, (x, z, corner) -> c.set(x, y, z, corner ? p.frame() : p.walls()));
    }

    // ------------------------------------------------------------------ crowns, domes, towers

    /** What rises above a town hall: a clock tower, a dome, an onion, mud towers, a pagoda tier or a roof comb. */
    private static void crown(Canvas c, Palette p, int cx, int cz, int roofBase, int top) {
        switch (p.style()) {
            case DESERT -> {
                drawProfile(c, cx, cz, roofBase + 1, List.of(3, 3), p.walls());
                dome(c, cx, cz, roofBase + 3, 3, p.accent(), false);
            }
            case TAIGA -> {
                for (int y = roofBase; y <= top + 1; y++) {
                    int yy = y;
                    perimeter(cx - 1, cz - 1, cx + 1, cz + 1, (x, z, corner) -> c.set(x, yy, z, corner ? p.frame() : p.walls()));
                }
                onion(c, cx, cz, top + 2, 1, p.accent());
            }
            case SAVANNA -> {
                for (int x : new int[]{1, c.width - 2}) {
                    c.fill(x, 1, c.depth - 2, x, top + 3, c.depth - 2, p.frame());
                    c.set(x, top + 4, c.depth - 2, p.fence());
                }
            }
            case CHERRY -> {
                for (int y = roofBase + 2; y <= top + 1; y++) {
                    int yy = y;
                    perimeter(cx - 1, cz - 1, cx + 1, cz + 1, (x, z, corner) -> c.set(x, yy, z, corner ? p.frame() : p.walls()));
                }
                int peak = hip(c, p, cx - 2, cz - 2, cx + 2, cz + 2, top + 2, 1, p.roofStairs() == null);
                c.set(cx, peak + 1, cz, Blocks.LIGHTNING_ROD.defaultBlockState());
            }
            case JUNGLE -> {
                for (int x = 1; x <= c.width - 2; x += 2) {
                    c.fill(x, top + 1, cz, x, top + 2, cz, p.accent());
                }
            }
            default -> {
                int towerTop = top + 3;
                for (int y = roofBase; y <= towerTop; y++) {
                    int yy = y;
                    perimeter(cx - 1, cz - 1, cx + 1, cz + 1, (x, z, corner) -> c.set(x, yy, z, corner ? p.frame() : p.walls()));
                }
                // A clock face on each side.
                c.set(cx, towerTop - 1, cz - 1, p.accent());
                c.set(cx, towerTop - 1, cz + 1, p.accent());
                c.set(cx - 1, towerTop - 1, cz, p.accent());
                c.set(cx + 1, towerTop - 1, cz, p.accent());
                int peak = hip(c, p, cx - 2, cz - 2, cx + 2, cz + 2, towerTop + 1, 2, p.roofStairs() == null);
                c.set(cx, peak + 1, cz, Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        }
    }

    /** A hollow hemisphere of {@code state}; with {@code onion} the curve swells out before it narrows. */
    private static void dome(Canvas c, int cx, int cz, int baseY, int radius, BlockState state, boolean onion) {
        List<Integer> profile = new ArrayList<>();
        for (int dy = 0; dy <= radius; dy++) {
            profile.add((int) Math.round(Math.sqrt(radius * radius - dy * dy)));
        }
        drawProfile(c, cx, cz, baseY, profile, state);
        c.set(cx, baseY + profile.size(), cz, Blocks.LIGHTNING_ROD.defaultBlockState());
    }

    /** A Russian onion dome with a spire, {@code size} 1 (small) or 2 (big). */
    private static void onion(Canvas c, int cx, int cz, int baseY, int size, BlockState state) {
        List<Integer> profile = size >= 2 ? List.of(2, 3, 3, 2, 1, 0) : List.of(1, 2, 1, 0);
        drawProfile(c, cx, cz, baseY, profile, state);
        int spire = baseY + profile.size();
        c.set(cx, spire, cz, Blocks.CHAIN.defaultBlockState());
        c.set(cx, spire + 1, cz, Blocks.LIGHTNING_ROD.defaultBlockState());
    }

    /** Stacks discs of the given radii; only their rims are built, except the bottom and top discs. */
    private static void drawProfile(Canvas c, int cx, int cz, int baseY, List<Integer> radii, BlockState state) {
        for (int i = 0; i < radii.size(); i++) {
            int r = radii.get(i);
            boolean solid = i == 0 || i == radii.size() - 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!inDisc(dx, dz, r)) {
                        continue;
                    }
                    boolean rim = !inDisc(dx + 1, dz, r) || !inDisc(dx - 1, dz, r)
                            || !inDisc(dx, dz + 1, r) || !inDisc(dx, dz - 1, r);
                    if (solid || rim) {
                        c.set(cx + dx, baseY + i, cz + dz, state);
                    }
                }
            }
        }
    }

    private static boolean inDisc(int dx, int dz, int r) {
        return dx * dx + dz * dz <= r * r + r;
    }

    // ------------------------------------------------------------------ furniture

    /** Beds side by side along the back wall, heads to the wall. */
    private static void beds(Canvas c, Palette p, int y, int count) {
        for (int i = 0; i < count; i++) {
            int x = 2 + i;
            if (x > c.width - 4) {
                break;
            }
            c.bed(x, y, 3, p.bed(), Direction.NORTH);
        }
    }

    /** Ground floor of a house with bedrooms upstairs: a table, a workbench and a barrel. */
    private static void kitchen(Canvas c, Palette p) {
        int w = c.width;
        int d = c.depth;
        c.set(3, 1, 3, p.fence());
        c.set(3, 2, 3, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        c.set(4, 1, 3, stairs(p.wallStairs(), Direction.WEST));
        c.set(w - 3, 1, d - 3, Blocks.CRAFTING_TABLE.defaultBlockState());
        c.set(2, 1, 2, Blocks.BARREL.defaultBlockState());
    }

    /** A brick chimney in the back wall from a hearth to above the ridge, smoking. */
    private static void chimney(Canvas c, Palette p, int top) {
        if (!p.chimney()) {
            return;
        }
        int x = c.width - 3;
        BlockState brick = p.period() == Period.EARLY ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.BRICKS.defaultBlockState();
        c.fill(x, 1, 1, x, top + 1, 1, brick);
        c.set(x, top + 2, 1, Blocks.CAMPFIRE.defaultBlockState());
        c.set(x, 1, 2, facing(Blocks.FURNACE.defaultBlockState(), Direction.SOUTH));
    }

    /** Rows of benches facing the altar at the back. */
    private static void pews(Canvas c, Palette p, int fromZ, int toZ) {
        int cx = c.width / 2;
        for (int z = fromZ; z <= toZ; z += 2) {
            for (int x = 2; x <= c.width - 3; x++) {
                if (Math.abs(x - cx) > 1) {
                    c.set(x, 1, z, stairs(p.wallStairs(), Direction.SOUTH));
                }
            }
        }
    }

    /** The cleric's brewing stand beside an altar with a light on it. */
    private static void altar(Canvas c, Palette p, int cx) {
        c.set(cx, 1, 2, p.frame());
        c.set(cx, 2, 2, Blocks.LANTERN.defaultBlockState());
        c.set(cx - 2, 1, 2, Blocks.BREWING_STAND.defaultBlockState());
        c.set(cx + 2, 1, 2, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH));
    }

    private static Block[] canopy(ArchitectureStyle style) {
        return switch (style) {
            case PLAINS -> new Block[]{Blocks.RED_WOOL, Blocks.WHITE_WOOL};
            case DESERT -> new Block[]{Blocks.BLUE_WOOL, Blocks.WHITE_WOOL};
            case SAVANNA -> new Block[]{Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL};
            case SNOWY -> new Block[]{Blocks.LIGHT_BLUE_WOOL, Blocks.WHITE_WOOL};
            case TAIGA -> new Block[]{Blocks.RED_WOOL, Blocks.YELLOW_WOOL};
            case OTHER -> new Block[]{Blocks.GREEN_WOOL, Blocks.WHITE_WOOL};
            case CHERRY -> new Block[]{Blocks.PINK_WOOL, Blocks.WHITE_WOOL};
            case JUNGLE -> new Block[]{Blocks.LIME_WOOL, Blocks.YELLOW_WOOL};
        };
    }

    // ------------------------------------------------------------------ small helpers

    private interface CellVisitor {
        void visit(int x, int z, boolean corner);
    }

    private interface WallVisitor {
        void visit(int x, int z, int index, int length, Direction.Axis along, Direction outwards);
    }

    /** Every cell on the rectangle's edge once. */
    private static void perimeter(int x0, int z0, int x1, int z1, CellVisitor visitor) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x == x0 || x == x1 || z == z0 || z == z1) {
                    boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                    visitor.visit(x, z, corner);
                }
            }
        }
    }

    /** The four walls of a rectangle, each with its own numbering; corners are visited twice (index 0 or last). */
    private static void wallCells(int x0, int z0, int x1, int z1, WallVisitor visitor) {
        int lenX = x1 - x0 + 1;
        int lenZ = z1 - z0 + 1;
        for (int x = x0; x <= x1; x++) {
            visitor.visit(x, z0, x - x0, lenX, Direction.Axis.X, Direction.NORTH);
            visitor.visit(x, z1, x - x0, lenX, Direction.Axis.X, Direction.SOUTH);
        }
        for (int z = z0; z <= z1; z++) {
            visitor.visit(x0, z, z - z0, lenZ, Direction.Axis.Z, Direction.WEST);
            visitor.visit(x1, z, z - z0, lenZ, Direction.Axis.Z, Direction.EAST);
        }
    }

    private static void ring(Canvas c, int x0, int z0, int x1, int z1, int y, BlockState state) {
        perimeter(x0, z0, x1, z1, (x, z, corner) -> c.set(x, y, z, state));
    }

    private static BlockState slope(Palette p, Direction facing) {
        return p.roofStairs() == null ? p.roofBlock() : stairs(p.roofStairs(), facing);
    }

    private static BlockState stairs(Block block, Direction facing) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(StairBlock.FACING)) {
            state = state.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
        }
        return state;
    }

    private static BlockState gableBlock(Palette p, Direction.Axis along) {
        return p.logWalls() ? axis(p.walls(), along) : p.walls();
    }

    /** Lays a log-like block along {@code along}; other blocks are returned unchanged. */
    private static BlockState axis(BlockState state, Direction.Axis along) {
        return state.hasProperty(BlockStateProperties.AXIS) ? state.setValue(BlockStateProperties.AXIS, along) : state;
    }

    private static BlockState facing(BlockState state, Direction direction) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, direction) : state;
    }

    private static BlockState wallTorch(Direction facing) {
        return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing);
    }

    private static BlockState grindstone() {
        return Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    private static BlockState workstation(int variant) {
        int index = Math.floorMod(variant, workstationVariants());
        Block block;
        if (index < WORKSTATIONS.length) {
            block = WORKSTATIONS[index];
        } else {
            block = switch (index - WORKSTATIONS.length) {
                case 0 -> CitiesBlocks.BUILDER_BENCH.get();
                case 1 -> CitiesBlocks.SCHOLAR_DESK.get();
                default -> CitiesBlocks.CRAFT_BENCH.get();
            };
        }
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            state = state.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        }
        return facing(state, Direction.SOUTH);
    }

    // ------------------------------------------------------------------ canvas

    /**
     * A blueprint being drawn. Its height grows with what is placed; anything outside the plot's width and depth
     * is quietly left out, so decorations can never break a building.
     */
    static final class Canvas {
        final int width;
        final int depth;
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final Map<BlockPos, BlockPos> partners = new HashMap<>();
        private final List<BlockPos> storage = new ArrayList<>();
        private BlockPos entrance;
        private int top;

        Canvas(int width, int depth) {
            this.width = width;
            this.depth = depth;
        }

        void set(int x, int y, int z, BlockState state) {
            if (x < 0 || z < 0 || y < 0 || x >= width || z >= depth) {
                return;
            }
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos half = partners.remove(pos);
            if (half != null) {
                blocks.remove(half); // overwriting a door or bed removes its other half too
            }
            blocks.put(pos, state);
            top = Math.max(top, y);
        }

        BlockState get(int x, int y, int z) {
            return blocks.getOrDefault(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                        set(x, y, z, state);
                    }
                }
            }
        }

        void door(int x, int y, int z, Block door, Direction facing) {
            BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, facing)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            set(x, y, z, lower);
            set(x, y + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            partners.put(new BlockPos(x, y, z), new BlockPos(x, y + 1, z));
        }

        /** A bed whose foot is at (x, y, z) and whose head points towards {@code facing}. */
        void bed(int x, int y, int z, Block bed, Direction facing) {
            BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT);
            BlockPos footPos = new BlockPos(x, y, z);
            BlockPos headPos = footPos.relative(facing);
            set(x, y, z, foot);
            set(headPos.getX(), headPos.getY(), headPos.getZ(), foot.setValue(BedBlock.PART, BedPart.HEAD));
            partners.put(footPos, headPos);
        }

        void storage(int x, int y, int z, BlockState container) {
            set(x, y, z, container);
            storage.add(new BlockPos(x, y, z));
        }

        void entrance(int x, int z) {
            entrance = new BlockPos(x, 0, z);
        }

        Blueprint build() {
            Blueprint.Builder builder = new Blueprint.Builder(width, top + 1, depth);
            blocks.forEach((pos, state) -> builder.set(pos.getX(), pos.getY(), pos.getZ(), state));
            partners.forEach((first, second) -> {
                if (blocks.containsKey(first) && blocks.containsKey(second)) {
                    builder.partner(first, second);
                }
            });
            for (BlockPos pos : storage) {
                if (blocks.get(pos) != null && blocks.get(pos).getBlock() instanceof ChestBlock) {
                    builder.storage(pos.getX(), pos.getY(), pos.getZ(), blocks.get(pos));
                }
            }
            if (entrance != null) {
                builder.entrance(entrance.getX(), entrance.getZ());
            }
            return builder.build();
        }
    }
}
