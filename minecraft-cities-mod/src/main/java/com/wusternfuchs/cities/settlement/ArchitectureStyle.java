package com.wusternfuchs.cities.settlement;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.Locale;

/**
 * Regional culture of a settlement: its architecture, names, strengths, and how it gets along with others.
 */
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

    /** Plains folk are farmers, savanna folk hunt and herd. */
    public int foodBonusPercent() {
        return switch (this) {
            case PLAINS -> 20;
            case SAVANNA -> 10;
            default -> 0;
        };
    }

    /** Northerners get by on less. */
    public int consumptionPercent() {
        return this == SNOWY ? 75 : 100;
    }

    /** Taiga woodcutters. */
    public int materialsBonusPercent() {
        return this == TAIGA ? 30 : 0;
    }

    /** Desert merchants earn more from every caravan they sell. */
    public int tradeBonusPercent() {
        return this == DESERT ? 25 : 0;
    }

    /** Borderland people, mixed from everywhere, are curious. */
    public int researchBonusPercent() {
        return this == OTHER ? 25 : 0;
    }

    /** Military strength added by the culture's hardiness or courage. */
    public int defenseBonus() {
        return switch (this) {
            case SNOWY -> 8;
            case SAVANNA -> 5;
            default -> 0;
        };
    }

    /** How much two cultures like each other besides being the same: kin of the north, desert traders and farmers. */
    public int affinity(ArchitectureStyle other) {
        if (this == other) {
            return 20;
        }
        return switch (pair(this, other)) {
            case "SNOWY+TAIGA" -> 10;
            case "PLAINS+DESERT" -> 10;
            case "DESERT+SAVANNA" -> 5;
            case "PLAINS+TAIGA" -> 5;
            case "DESERT+SNOWY" -> -15;
            case "SAVANNA+SNOWY" -> -10;
            default -> -5;
        };
    }

    /** The two names in declaration order, e.g. "PLAINS+DESERT". */
    private static String pair(ArchitectureStyle a, ArchitectureStyle b) {
        return a.ordinal() < b.ordinal() ? a.name() + "+" + b.name() : b.name() + "+" + a.name();
    }

    public Component cultureName() {
        return Component.translatable("cities.culture." + name().toLowerCase(Locale.ROOT));
    }

    public Component cultureDescription() {
        return Component.translatable("cities.culture." + name().toLowerCase(Locale.ROOT) + ".desc");
    }
}
