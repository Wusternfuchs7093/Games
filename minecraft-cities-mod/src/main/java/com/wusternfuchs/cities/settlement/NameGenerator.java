package com.wusternfuchs.cities.settlement;

import net.minecraft.util.RandomSource;

import java.util.function.Predicate;

/** Builds settlement names from culture-specific roots and endings. */
public final class NameGenerator {
    private static final String[][] PLAINS = {
            {"Green", "Oak", "Wheat", "Bright", "Mill", "Brook", "Fair", "Elm", "Honey", "Clover", "Ash", "Bell"},
            {"field", "ford", "ton", "bury", "stead", "vale", "wick", "ham", "brook", "dale", "meadow"}
    };
    private static final String[][] DESERT = {
            {"Sun", "Sand", "Dune", "Amber", "Saff", "Kar", "Zah", "Mir", "Qas", "Tal", "Ash", "Ory"},
            {"ara", "abad", "esh", "un", "ir", "ah", "rim", "sar", "oum", "et", "is"}
    };
    private static final String[][] SAVANNA = {
            {"Aca", "Ruma", "Ngo", "Tanu", "Kesh", "Zuri", "Oba", "Mala", "Sika", "Lion", "Red"},
            {"ni", "ya", "ru", "mba", "nda", "ka", "wa", "lo", "rock", "plain"}
    };
    private static final String[][] SNOWY = {
            {"Frost", "Ice", "Snow", "White", "Winter", "Rime", "Hail", "Cold", "Pale", "Bear"},
            {"hold", "hearth", "peak", "gard", "fell", "haven", "mark", "watch", "berg"}
    };
    private static final String[][] TAIGA = {
            {"Pine", "Spruce", "Fir", "Wolf", "Moss", "Elk", "Fern", "Raven", "Birch", "Timber"},
            {"grad", "holm", "wood", "lodge", "vik", "hollow", "gorod", "stav", "ridge"}
    };
    private static final String[][] OTHER = {
            {"Stone", "River", "High", "Low", "North", "South", "Crow", "Willow", "Iron", "Copper"},
            {"gate", "bridge", "port", "march", "crest", "wall", "moor", "cross", "end"}
    };

    private NameGenerator() {
    }

    public static String generate(ArchitectureStyle style, RandomSource random, Predicate<String> taken) {
        String[][] parts = switch (style) {
            case PLAINS -> PLAINS;
            case DESERT -> DESERT;
            case SAVANNA -> SAVANNA;
            case SNOWY -> SNOWY;
            case TAIGA -> TAIGA;
            case OTHER -> OTHER;
        };
        for (int attempt = 0; attempt < 32; attempt++) {
            String name = pick(parts[0], random) + pick(parts[1], random);
            if (!taken.test(name)) {
                return name;
            }
        }
        String base = pick(parts[0], random) + pick(parts[1], random);
        int suffix = 2;
        while (taken.test(base + " " + suffix)) {
            suffix++;
        }
        return base + " " + suffix;
    }

    private static String pick(String[] options, RandomSource random) {
        return options[random.nextInt(options.length)];
    }
}
