package com.wusternfuchs.cities.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wusternfuchs.cities.CitiesMod;
import com.wusternfuchs.cities.settlement.ArchitectureStyle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Name lists from datapacks, one file per culture: {@code data/<namespace>/cities/names/<culture>.json}
 * <pre>
 * { "town_starts": ["Green", "Oak"], "town_ends": ["field", "ford"], "people": ["Alden", "Bree"] }
 * </pre>
 * Any list left out keeps the built-in names.
 */
public final class CultureNames extends SimpleJsonResourceReloadListener {
    public record Names(List<String> townStarts, List<String> townEnds, List<String> people) {
    }

    private static volatile Map<ArchitectureStyle, Names> names = Map.of();

    public CultureNames() {
        super(new Gson(), "cities/names");
    }

    @Nullable
    public static Names get(ArchitectureStyle culture) {
        return names.get(culture);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ArchitectureStyle, Names> result = new EnumMap<>(ArchitectureStyle.class);
        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            try {
                String path = file.getKey().getPath();
                ArchitectureStyle culture = ArchitectureStyle.byName(path.substring(path.lastIndexOf('/') + 1));
                JsonObject json = GsonHelper.convertToJsonObject(file.getValue(), "names");
                result.put(culture, new Names(list(json, "town_starts"), list(json, "town_ends"), list(json, "people")));
            } catch (Exception e) {
                CitiesMod.LOGGER.warn("Skipping name list {}: {}", file.getKey(), e.getMessage());
            }
        }
        names = result;
    }

    private static List<String> list(JsonObject json, String key) {
        List<String> values = new ArrayList<>();
        if (json.has(key)) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) {
                values.add(element.getAsString());
            }
        }
        return values;
    }
}
