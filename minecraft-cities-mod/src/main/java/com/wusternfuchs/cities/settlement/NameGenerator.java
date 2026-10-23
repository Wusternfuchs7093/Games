package com.wusternfuchs.cities.settlement;

import com.wusternfuchs.cities.data.CultureNames;
import net.minecraft.util.RandomSource;

import java.util.function.Predicate;

/** Builds settlement names from culture-specific roots and endings. */
public final class NameGenerator {
    private static final String[][] PLAINS = {
            {"Rot", "Schön", "Linden", "Eichen", "Wald", "Stein", "Hohen", "Neu", "Alt", "Kirch", "Mühl", "Rosen", "Fried"},
            {"burg", "dorf", "heim", "feld", "bach", "hausen", "stadt", "au", "furt", "berg", "tal"}
    };
    private static final String[][] DESERT = {
            {"Al-", "Dar ", "Qasr ", "Wadi ", "Bir ", "Ain ", "Ras ", "Bab "},
            {"Safa", "Rumah", "Nahr", "Sahra", "Zahir", "Qamar", "Najm", "Tamr", "Layl", "Bahr", "Hilal", "Sabah"}
    };
    private static final String[][] SAVANNA = {
            {"Tom", "Ga", "Ka", "Djen", "Mo", "Ti", "Ba", "Se", "Zan", "Ki", "Ma", "Su"},
            {"buktu", "o", "ne", "bara", "ssi", "mbu", "ndo", "lwa", "nga", "rare", "lima"}
    };
    private static final String[][] SNOWY = {
            {"Bjorn", "Fjell", "Is", "Frost", "Ulf", "Sval", "Hvit", "Kald", "Rav", "Stor", "Nord", "Ask"},
            {"heim", "vik", "fjord", "gard", "by", "dal", "holm", "stad", "berg", "nes"}
    };
    private static final String[][] TAIGA = {
            {"Belo", "Novo", "Zlato", "Kras", "Yaro", "Vla", "Sveto", "Bogo", "Vysh", "Lesno", "Mosko", "Rosto"},
            {"grad", "gorod", "sk", "vo", "slavl", "pol", "mir", "dar", "zersk", "gorsk"}
    };
    private static final String[][] OTHER = {
            {"Ash", "Brad", "Chester", "Dun", "Elm", "Glou", "Hart", "Kings", "Lan", "Mel", "Ox", "Wor"},
            {"ford", "bury", "ton", "wich", "ley", "field", "by", "mouth", "bridge", "wick"}
    };
    private static final String[][] CHERRY = {
            {"Saku", "Kawa", "Yama", "Ishi", "Matsu", "Taka", "Kiyo", "Fuji", "Mori", "Shira", "Haru", "Aki"},
            {"mura", "machi", "saki", "hara", "no", "shima", "zawa", "oka", "ta", "yama", "mizu"}
    };
    private static final String[][] JUNGLE = {
            {"Ix", "Chi", "Uxa", "Tik", "Pa", "Co", "Ka", "Yax", "Bal", "Tu", "Na", "Zac"},
            {"chen", "lenque", "pan", "mal", "kal", "ba", "tun", "ha", "lum", "nal"}
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
            case CHERRY -> CHERRY;
            case JUNGLE -> JUNGLE;
        };
        CultureNames.Names custom = CultureNames.get(style);
        if (custom != null && !custom.townStarts().isEmpty() && !custom.townEnds().isEmpty()) {
            parts = new String[][]{custom.townStarts().toArray(new String[0]), custom.townEnds().toArray(new String[0])};
        }
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
