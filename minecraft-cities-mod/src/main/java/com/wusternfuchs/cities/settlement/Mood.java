package com.wusternfuchs.cities.settlement;

import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.governance.Law;
import com.wusternfuchs.cities.people.SocialClass;
import com.wusternfuchs.cities.people.Trait;
import com.wusternfuchs.cities.research.Tech;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Everything that makes a settlement happy or unhappy, as named factors around a neutral 50. */
public final class Mood {
    public static final int BASE = 50;

    /** One reason for the mood, e.g. "Housing: +9". */
    public record Factor(String key, int value) {
        public Component describe() {
            ChatFormatting color = value > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
            return Component.literal(String.format("  %+d ", value)).withStyle(color)
                    .append(Component.translatable("cities.mood." + key).withStyle(ChatFormatting.GRAY));
        }
    }

    private Mood() {
    }

    public static List<Factor> factors(ServerLevel level, Settlement s) {
        List<Factor> list = new ArrayList<>();
        int freeBeds = s.beds() - s.population();
        add(list, "housing", freeBeds >= 0 ? Math.min(15, freeBeds * 3) : Math.max(-30, freeBeds * 5));
        if (s.adults() > 0) {
            add(list, "work", (int) Math.round(15.0 * s.employed() / s.adults()) - 5);
        }
        if (s.food() <= 0) {
            add(list, "hunger", -25);
        } else if (s.food() >= s.population() * 20) {
            add(list, "plenty", 10);
        }
        add(list, "golems", Math.min(10, s.golems() * 5));
        add(list, "guards", Math.min(15, s.guards() * 3));
        add(list, "watchtowers", Math.min(9, s.count(BuildingType.WATCHTOWER) * 3));
        add(list, "markets", Math.min(10, s.count(BuildingType.MARKET) * 5));
        add(list, "well", s.count(BuildingType.WELL) > 0 ? 5 : 0);
        add(list, "medicine", s.hasTech(Tech.MEDICINE) ? 5 : 0);

        // What each class of society expects from its town.
        if (s.count(SocialClass.ARTISAN) > 0) {
            add(list, "artisans", s.count(BuildingType.MARKET) > 0 ? 3 : -5);
        }
        if (s.count(SocialClass.SCHOLAR) > 0) {
            add(list, "scholars", s.count(BuildingType.LIBRARY) > 0 ? 3 : -5);
        }
        int elite = s.count(SocialClass.ELITE);
        if (elite > 0) {
            add(list, "elite", s.count(BuildingType.MANOR) * 2 >= elite ? 5 : -8);
        }
        add(list, "temperament", Mth.clamp((s.count(Trait.CHEERFUL) - s.count(Trait.GRUMPY)) * 2, -10, 10));
        if (s.deathsTotal() >= 3) {
            add(list, "cemetery", s.count(BuildingType.CEMETERY) > 0 ? 2 : -5);
        }

        add(list, "taxes", s.tax().happiness());
        int laws = 0;
        for (Law law : s.laws()) {
            laws += s.hasTech(Tech.CIVIC_CODE) ? law.happiness() / 2 : law.happiness();
        }
        add(list, "laws", laws);
        add(list, "festival", s.festivalCensuses() > 0 ? 15 : 0);

        add(list, "raid", level.isRaided(s.center()) || s.siege() != null ? -30 : 0);
        add(list, "deaths", -Math.min(30, s.recentDeaths() * 10));
        add(list, "epidemic", s.epidemic() > 0 ? -Math.min(20, 5 + s.sick() * 3) : 0);
        add(list, "drought", s.drought() > 0 ? -5 : 0);
        add(list, "fire", s.recentFire() > 0 ? -10 : 0);
        return list;
    }

    private static void add(List<Factor> list, String key, int value) {
        if (value != 0) {
            list.add(new Factor(key, value));
        }
    }

    public static int target(List<Factor> factors) {
        int target = BASE;
        for (Factor factor : factors) {
            target += factor.value();
        }
        return Mth.clamp(target, 0, 100);
    }
}
