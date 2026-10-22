package com.wusternfuchs.cities.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wusternfuchs.cities.CitiesMod;
import com.wusternfuchs.cities.building.Blueprint;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.settlement.ArchitectureStyle;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Buildings added by datapacks. Each {@code data/<namespace>/cities/buildings/<name>.json} points to a structure
 * saved with a structure block ({@code data/<namespace>/structure/<path>.nbt}) and says what it is:
 * <pre>
 * {
 *   "building": "house",              // any building type id
 *   "structure": "mypack:fancy_house",
 *   "cultures": ["plains", "taiga"],  // optional, every culture if missing
 *   "entrance": [3, 7],               // optional local x, z of the spot in front of the door
 *   "storage": true                   // optional, chests become town storage (always on for warehouses)
 * }
 * </pre>
 * The structure's front must face south (+z), with the floor at its lowest layer.
 */
public final class CustomBlueprints extends SimplePreparableReloadListener<Map<BuildingType, List<CustomBlueprints.Entry>>> {
    private static final String FOLDER = "cities/buildings";
    private static final int MAX_SIZE = 48;

    public record Entry(ResourceLocation id, Set<ArchitectureStyle> cultures, Blueprint blueprint) {
    }

    private static volatile Map<BuildingType, List<Entry>> entries = Map.of();

    /** Datapack buildings of a type that fit a culture, in a stable order. */
    public static List<Blueprint> forType(BuildingType type, ArchitectureStyle culture) {
        List<Entry> list = entries.getOrDefault(type, List.of());
        if (list.isEmpty()) {
            return List.of();
        }
        List<Blueprint> result = new ArrayList<>();
        for (Entry entry : list) {
            if (entry.cultures().contains(culture)) {
                result.add(entry.blueprint());
            }
        }
        return result;
    }

    public static int count() {
        int count = 0;
        for (List<Entry> list : entries.values()) {
            count += list.size();
        }
        return count;
    }

    @Override
    protected Map<BuildingType, List<Entry>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<BuildingType, List<Entry>> result = new EnumMap<>(BuildingType.class);
        // Sorted by id so variant numbers stay the same between reloads.
        Map<ResourceLocation, Resource> files = new TreeMap<>(manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")));
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            try (Reader reader = file.getValue().openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                Entry entry = read(manager, file.getKey(), json);
                result.computeIfAbsent(typeOf(json), t -> new ArrayList<>()).add(entry);
            } catch (Exception e) {
                CitiesMod.LOGGER.warn("Skipping custom building {}: {}", file.getKey(), e.getMessage());
            }
        }
        result.replaceAll((type, list) -> Collections.unmodifiableList(list));
        return result;
    }

    @Override
    protected void apply(Map<BuildingType, List<Entry>> prepared, ResourceManager manager, ProfilerFiller profiler) {
        entries = prepared;
        BuildingType.clearCache();
        if (!prepared.isEmpty()) {
            CitiesMod.LOGGER.info("Loaded {} custom buildings from datapacks", count());
        }
    }

    private static BuildingType typeOf(JsonObject json) {
        BuildingType type = BuildingType.byId(GsonHelper.getAsString(json, "building"));
        if (type == null || type == BuildingType.WALL || type == BuildingType.HIGHWAY) {
            throw new IllegalArgumentException("unknown or unsupported building type");
        }
        return type;
    }

    private static Entry read(ResourceManager manager, ResourceLocation file, JsonObject json) throws Exception {
        BuildingType type = typeOf(json);
        ResourceLocation structure = ResourceLocation.parse(GsonHelper.getAsString(json, "structure"));
        ResourceLocation nbtFile = ResourceLocation.fromNamespaceAndPath(structure.getNamespace(),
                "structure/" + structure.getPath() + ".nbt");
        Optional<Resource> resource = manager.getResource(nbtFile);
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("structure file not found: " + nbtFile);
        }
        CompoundTag nbt;
        try (InputStream in = resource.get().open()) {
            nbt = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }

        Set<ArchitectureStyle> cultures = EnumSet.allOf(ArchitectureStyle.class);
        if (json.has("cultures")) {
            cultures = EnumSet.noneOf(ArchitectureStyle.class);
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "cultures")) {
                cultures.add(ArchitectureStyle.byName(element.getAsString()));
            }
        }
        boolean storage = type == BuildingType.WAREHOUSE || GsonHelper.getAsBoolean(json, "storage", false);
        JsonArray entrance = json.has("entrance") ? GsonHelper.getAsJsonArray(json, "entrance") : null;
        return new Entry(file, cultures, toBlueprint(nbt, storage, entrance));
    }

    /** Converts a structure-block NBT file into a blueprint. Structure voids and jigsaws become air. */
    private static Blueprint toBlueprint(CompoundTag nbt, boolean storage, JsonArray entrance) {
        ListTag size = nbt.getList("size", Tag.TAG_INT);
        int width = size.getInt(0);
        int height = size.getInt(1);
        int depth = size.getInt(2);
        if (width < 1 || height < 1 || depth < 1 || width > MAX_SIZE || height > MAX_SIZE || depth > MAX_SIZE) {
            throw new IllegalArgumentException("structure size must be 1.." + MAX_SIZE + " per side");
        }
        ListTag paletteTag = nbt.contains("palettes", Tag.TAG_LIST)
                ? nbt.getList("palettes", Tag.TAG_LIST).getList(0)
                : nbt.getList("palette", Tag.TAG_COMPOUND);
        HolderGetter<Block> blocks = BuiltInRegistries.BLOCK.asLookup();
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            BlockState state = NbtUtils.readBlockState(blocks, paletteTag.getCompound(i));
            if (state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_BLOCK)) {
                state = Blocks.AIR.defaultBlockState();
            }
            palette.add(state);
        }

        Blueprint.Builder builder = new Blueprint.Builder(width, height, depth);
        ListTag placed = nbt.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < placed.size(); i++) {
            CompoundTag block = placed.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            int index = block.getInt("state");
            if (index < 0 || index >= palette.size()) {
                continue;
            }
            BlockState state = palette.get(index);
            int x = pos.getInt(0);
            int y = pos.getInt(1);
            int z = pos.getInt(2);
            if (storage && state.getBlock() instanceof ChestBlock) {
                builder.storage(x, y, z, state);
            } else {
                builder.set(x, y, z, state);
            }
        }
        builder.autoPartners();
        if (entrance != null && entrance.size() == 2) {
            builder.entrance(entrance.get(0).getAsInt(), entrance.get(1).getAsInt());
        } else {
            builder.entrance(width / 2, depth);
        }
        return builder.build();
    }
}
