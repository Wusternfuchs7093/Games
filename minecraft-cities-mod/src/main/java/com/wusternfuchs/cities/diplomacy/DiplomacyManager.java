package com.wusternfuchs.cities.diplomacy;

import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.SettlementTier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Relations between settlements. Each census they drift towards a natural attitude: kin cultures like each other,
 * close neighbours of the same size become rivals. Trade pulls them closer.
 */
public final class DiplomacyManager {
    /** Settlements farther apart than this ignore each other. */
    public static final int RANGE = 1024;
    private static final int RIVALRY_DISTANCE = 320;

    private DiplomacyManager() {
    }

    public static Relation relation(Settlement a, Settlement b) {
        return Relation.of(a.relation(b.id()));
    }

    public static boolean inRange(Settlement a, Settlement b) {
        return a.center().distSqr(b.center()) <= (double) RANGE * RANGE;
    }

    public static void change(Settlement a, Settlement b, int delta) {
        a.setRelation(b.id(), a.relation(b.id()) + delta);
        b.setRelation(a.id(), b.relation(a.id()) + delta);
    }

    static int naturalAttitude(Settlement a, Settlement b) {
        int attitude = a.style() == b.style() ? 20 : -5;
        boolean close = a.center().distSqr(b.center()) <= (double) RIVALRY_DISTANCE * RIVALRY_DISTANCE;
        boolean peers = Math.abs(a.tier().ordinal() - b.tier().ordinal()) <= 1;
        boolean ambitious = Math.min(a.tier().ordinal(), b.tier().ordinal()) >= SettlementTier.TOWNSHIP.ordinal();
        if (close && peers && ambitious) {
            attitude -= 35;
        }
        return attitude;
    }

    public static void drift(ServerLevel level, SettlementData data, Settlement s) {
        for (Settlement other : data.all()) {
            if (other == s || !inRange(s, other)) {
                continue;
            }
            int current = s.relation(other.id());
            int target = naturalAttitude(s, other);
            if (current == target) {
                continue;
            }
            int next = current + Integer.signum(target - current);
            Relation before = Relation.of(current);
            Relation after = Relation.of(next);
            s.setRelation(other.id(), next);
            other.setRelation(s.id(), next);
            if (before != after) {
                announce(level, s, other, after);
            }
        }
    }

    public static void announce(ServerLevel level, Settlement a, Settlement b, Relation relation) {
        String key = "relation_" + relation.name().toLowerCase(Locale.ROOT);
        long day = SettlementManager.day(level);
        a.record(day, key, b.name());
        b.record(day, key, a.name());
        Component message = Component.translatable("cities.diplomacy.changed", a.name(), b.name(), relation.displayName());
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }

    public static List<Settlement> withRelation(SettlementData data, Settlement s, Relation relation) {
        List<Settlement> result = new ArrayList<>();
        for (Settlement other : data.all()) {
            if (other != s && inRange(s, other) && relation(s, other) == relation) {
                result.add(other);
            }
        }
        return result;
    }
}
