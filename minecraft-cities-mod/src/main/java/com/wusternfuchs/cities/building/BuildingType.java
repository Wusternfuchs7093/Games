package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.data.CustomBlueprints;
import com.wusternfuchs.cities.research.Era;
import com.wusternfuchs.cities.settlement.ArchitectureStyle;
import com.wusternfuchs.cities.settlement.SettlementTier;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

public enum BuildingType {
    HOUSE(Blueprints.houseVariants(), true, Blueprints::house),
    FARM(4, true, Blueprints::farm),
    WAREHOUSE(1, true, Blueprints::warehouse),
    WORKSHOP(Blueprints.workstationVariants(), true, Blueprints::workshop),
    WELL(1, true, Blueprints::well),
    MARKET(4, true, Blueprints::market),
    TOWN_HALL(1, true, Blueprints::townHall),
    WATCHTOWER(1, true, Blueprints::watchtower),
    LIBRARY(1, true, Blueprints::library),
    HOSPITAL(1, true, Blueprints::hospital),
    MANOR(1, true, Blueprints::manor),
    CEMETERY(1, true, Blueprints::cemetery),
    TAVERN(1, true, Blueprints::tavern),
    LAMP(1, false, Blueprints::lamp),
    /** Ring wall around the town; laid out from the terrain, its variant is the radius. */
    WALL(1, false, (palette, variant) -> new Blueprint.Builder(1, 1, 1).build()),
    /** Road to a friendly neighbour; laid out from the terrain, its variant packs the offset to the neighbour. */
    HIGHWAY(1, false, (palette, variant) -> new Blueprint.Builder(1, 1, 1).build()),
    /** Church, mosque, pagoda or pyramid, as the culture builds them; where clerics serve. */
    TEMPLE(1, true, Blueprints::temple),
    /** The culture's own trade, worked by artisans. */
    CRAFTHOUSE(1, true, Blueprints::craftHouse);

    private record Key(BuildingType type, ArchitectureStyle style, int variant, boolean masonry, Period period) {
    }

    private static final Map<Key, Blueprint> CACHE = new HashMap<>();

    private final int variants;
    private final boolean hasRoad;
    private final BiFunction<Palette, Integer, Blueprint> generator;

    BuildingType(int variants, boolean hasRoad, BiFunction<Palette, Integer, Blueprint> generator) {
        this.variants = variants;
        this.hasRoad = hasRoad;
        this.generator = generator;
    }

    public int variants() {
        return variants;
    }

    public boolean hasRoad() {
        return hasRoad;
    }

    public Blueprint blueprint(ArchitectureStyle style, int variant) {
        return blueprint(style, variant, false, Era.FOUNDING);
    }

    /** Built-in variants plus any datapack buildings of this type for the culture. */
    public int variantCount(ArchitectureStyle style) {
        return variants + CustomBlueprints.forType(this, style).size();
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * The blueprint of this type for a culture in an era. {@code masonry} only matters in the early period, when
     * towns that know masonry put their houses on a stone ground floor.
     */
    public Blueprint blueprint(ArchitectureStyle style, int variant, boolean masonry, Era era) {
        List<Blueprint> custom = CustomBlueprints.forType(this, style);
        if (!custom.isEmpty()) {
            int index = Math.floorMod(variant, variants + custom.size());
            if (index >= variants) {
                return custom.get(index - variants);
            }
        }
        int v = Math.floorMod(variant, variants);
        Period period = Period.of(era);
        boolean stone = masonry && period == Period.EARLY;
        return CACHE.computeIfAbsent(new Key(this, style, v, stone, period),
                key -> generator.apply(Palette.of(style, era, stone), v));
    }

    /** Buildings that are rebuilt in the style of each new age. */
    public boolean upgradable() {
        return this == HOUSE || this == WORKSHOP || this == WAREHOUSE || this == TOWN_HALL || this == LIBRARY
                || this == WATCHTOWER || this == HOSPITAL || this == MANOR || this == TAVERN || this == TEMPLE
                || this == CRAFTHOUSE || this == MARKET || this == WELL || this == LAMP;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("cities.building." + id());
    }

    public static BuildingType byId(String id) {
        for (BuildingType type : values()) {
            if (type.id().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    /** Public buildings a settlement must own before it may reach {@code tier}. */
    public static Map<BuildingType, Integer> requiredFor(SettlementTier tier) {
        Map<BuildingType, Integer> required = new EnumMap<>(BuildingType.class);
        int level = tier.ordinal();
        if (level >= SettlementTier.TOWNSHIP.ordinal()) {
            required.put(WAREHOUSE, 1);
            required.put(WELL, 1);
        }
        if (level >= SettlementTier.SMALL_TOWN.ordinal()) {
            required.put(TOWN_HALL, 1);
            required.put(MARKET, 1);
            required.put(LIBRARY, 1);
        }
        if (level >= SettlementTier.CITY.ordinal()) {
            required.put(MARKET, 2);
            required.put(WATCHTOWER, 2);
            required.put(WALL, 1);
            required.put(TEMPLE, 1);
        }
        if (level >= SettlementTier.METROPOLIS.ordinal()) {
            required.put(MARKET, 3);
            required.put(WATCHTOWER, 4);
            required.put(WELL, 2);
            required.put(WALL, 2);
            required.put(HOSPITAL, 1);
        }
        if (level >= SettlementTier.CAPITAL.ordinal()) {
            required.put(MARKET, 4);
            required.put(WATCHTOWER, 6);
            required.put(WAREHOUSE, 2);
        }
        return required;
    }
}
