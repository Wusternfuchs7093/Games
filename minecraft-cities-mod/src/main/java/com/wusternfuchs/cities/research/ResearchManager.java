package com.wusternfuchs.cities.research;

import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.SettlementTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Scholars and libraries produce research points; finished technologies unlock effects and new ages. */
public final class ResearchManager {
    private ResearchManager() {
    }

    public static int pointsPerCensus(Settlement s) {
        int points = 1 + s.scholars() * 2 + s.count(BuildingType.LIBRARY) * 4
                + (s.count(BuildingType.TOWN_HALL) > 0 ? 2 : 0);
        if (s.hasTech(Tech.PRINTING)) {
            points += points / 2;
        }
        points += points * s.style().researchBonusPercent() / 100;
        return points;
    }

    /** The age a settlement must have reached before it can grow into {@code tier}. */
    public static Era requiredEra(SettlementTier tier) {
        return switch (tier) {
            case CITY -> Era.IRON;
            case METROPOLIS -> Era.MEDIEVAL;
            case CAPITAL -> Era.RENAISSANCE;
            default -> Era.FOUNDING;
        };
    }

    public static boolean isAvailable(Settlement s, Tech tech) {
        return !s.hasTech(tech) && tech.era().ordinal() <= s.era().ordinal();
    }

    public static List<Tech> available(Settlement s) {
        List<Tech> result = new ArrayList<>();
        for (Tech tech : Tech.values()) {
            if (isAvailable(s, tech)) {
                result.add(tech);
            }
        }
        return result;
    }

    /** The council studies whatever the town needs most, otherwise the first open topic. */
    @Nullable
    static Tech choose(Settlement s) {
        List<Tech> open = available(s);
        if (open.isEmpty()) {
            return null;
        }
        if (open.contains(Tech.AGRICULTURE) && s.food() < s.population() * 10) {
            return Tech.AGRICULTURE;
        }
        if (open.contains(Tech.METALLURGY) && s.siege() != null) {
            return Tech.METALLURGY;
        }
        return open.get(0);
    }

    public static void research(ServerLevel level, SettlementData data, Settlement s) {
        Tech current = s.researching();
        if (current == null || !isAvailable(s, current)) {
            current = choose(s);
            s.setResearching(current);
        }
        if (current == null) {
            return;
        }
        s.addResearchProgress(pointsPerCensus(s));
        if (s.researchProgress() < current.cost()) {
            return;
        }

        Era before = s.era();
        s.techs().add(current);
        s.setResearching(null);
        long day = SettlementManager.day(level);
        s.record(day, "tech", current.id());
        announce(level, Component.translatable("cities.research.done", s.name(), current.displayName())
                .withStyle(ChatFormatting.AQUA));

        Era after = s.era();
        if (after != before) {
            s.record(day, "era", after.id());
            level.playSound(null, s.center(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 2.0f, 1.0f);
            Component message = Component.translatable("cities.research.era", s.name(), after.displayName())
                    .withStyle(ChatFormatting.GOLD);
            announce(level, message);
            for (ServerPlayer player : level.players()) {
                if (data.at(player.blockPosition()) == s) {
                    SettlementManager.showTitle(player, after.displayName().copy().withStyle(ChatFormatting.GOLD),
                            Component.literal(s.name()));
                }
            }
        }
        s.setResearching(choose(s));
    }

    private static void announce(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }
}
