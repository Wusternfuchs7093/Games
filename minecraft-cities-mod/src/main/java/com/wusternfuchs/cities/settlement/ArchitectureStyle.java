package com.wusternfuchs.cities.settlement;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.Locale;

/** Regional culture of a settlement. Drives names now and building styles later. */
public enum ArchitectureStyle {
    PLAINS,
    DESERT,
    SAVANNA,
    SNOWY,
    TAIGA,
    OTHER;

    public static ArchitectureStyle fromBiome(Holder<Biome> biome) {
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) return DESERT;
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) return SAVANNA;
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) return SNOWY;
        if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) return TAIGA;
        if (biome.is(BiomeTags.HAS_VILLAGE_PLAINS)) return PLAINS;
        return OTHER;
    }

    public static ArchitectureStyle byName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }

    /** Flat bonus to the hidden growth potential: some lands are friendlier to big cities. */
    public int potentialBonus() {
        return switch (this) {
            case PLAINS -> 10;
            case SAVANNA, TAIGA -> 5;
            case DESERT, OTHER -> 0;
            case SNOWY -> -5;
        };
    }
}
