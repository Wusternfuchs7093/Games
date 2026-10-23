package com.wusternfuchs.cities.settlement;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Locale;

/**
 * Regional culture of a settlement: its architecture, names, craft, strengths, and how it gets along with others.
 * The enum names are the biome families they come from; the cultures themselves are named in the lang files
 * (plains: German, desert: Arabic, savanna: Sahel, snowy: Nordic, taiga: Russian, other: British,
 * cherry: Japanese, jungle: Maya).
 */
public enum ArchitectureStyle {
    PLAINS,
    DESERT,
    SAVANNA,
    SNOWY,
    TAIGA,
    OTHER,
    CHERRY,
    JUNGLE;

    /** The trade a culture is known for. Its craft house produces it and its artisans sell it. */
    public enum Craft {
        CLOCKMAKING,
        CARPETS,
        POTTERY,
        SMOKEHOUSE,
        HONEY,
        WOOL,
        SWORDSMITH,
        CHOCOLATE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("cities.craft." + id());
        }

        public Component description() {
            return Component.translatable("cities.craft." + id() + ".desc");
        }
    }

    public static ArchitectureStyle fromBiome(Holder<Biome> biome) {
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) return DESERT;
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) return SAVANNA;
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) return SNOWY;
        if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) return TAIGA;
        if (biome.is(BiomeTags.HAS_VILLAGE_PLAINS)) return PLAINS;
        if (biome.is(Biomes.CHERRY_GROVE)) return CHERRY;
        if (biome.is(BiomeTags.IS_JUNGLE)) return JUNGLE;
        return OTHER;
    }

    public static ArchitectureStyle byName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Craft craft() {
        return switch (this) {
            case PLAINS -> Craft.CLOCKMAKING;
            case DESERT -> Craft.CARPETS;
            case SAVANNA -> Craft.POTTERY;
            case SNOWY -> Craft.SMOKEHOUSE;
            case TAIGA -> Craft.HONEY;
            case OTHER -> Craft.WOOL;
            case CHERRY -> Craft.SWORDSMITH;
            case JUNGLE -> Craft.CHOCOLATE;
        };
    }

    /** Flat bonus to the hidden growth potential: some lands are friendlier to big cities. */
    public int potentialBonus() {
        return switch (this) {
            case PLAINS -> 10;
            case SAVANNA, TAIGA, CHERRY -> 5;
            case DESERT, OTHER -> 0;
            case SNOWY, JUNGLE -> -5;
        };
    }

    /** Plains folk are farmers, savanna folk hunt and herd, the Maya grow maize everywhere. */
    public int foodBonusPercent() {
        return switch (this) {
            case PLAINS -> 20;
            case SAVANNA, JUNGLE -> 10;
            default -> 0;
        };
    }

    /** Northerners get by on less. */
    public int consumptionPercent() {
        return this == SNOWY ? 75 : 100;
    }

    /** Russian woodcutters. */
    public int materialsBonusPercent() {
        return this == TAIGA ? 30 : 0;
    }

    /** Arab merchants earn more from every caravan they sell. */
    public int tradeBonusPercent() {
        return this == DESERT ? 25 : 0;
    }

    /** Borderland people, mixed from everywhere, are curious; so are the Maya astronomers. */
    public int researchBonusPercent() {
        return switch (this) {
            case OTHER -> 25;
            case JUNGLE -> 15;
            default -> 0;
        };
    }

    /** Military strength added by the culture's hardiness or courage. */
    public int defenseBonus() {
        return switch (this) {
            case SNOWY, CHERRY -> 8;
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
            case "PLAINS+OTHER" -> 5;
            case "SAVANNA+JUNGLE" -> 5;
            case "OTHER+CHERRY" -> 5;
            case "DESERT+SNOWY" -> -15;
            case "SAVANNA+SNOWY" -> -10;
            case "SNOWY+JUNGLE" -> -10;
            default -> -5;
        };
    }

    /** The two names in declaration order, e.g. "PLAINS+DESERT". */
    private static String pair(ArchitectureStyle a, ArchitectureStyle b) {
        return a.ordinal() < b.ordinal() ? a.name() + "+" + b.name() : b.name() + "+" + a.name();
    }

    public Component cultureName() {
        return Component.translatable("cities.culture." + id());
    }

    public Component cultureDescription() {
        return Component.translatable("cities.culture." + id() + ".desc");
    }
}
