package com.wusternfuchs.cities.settlement;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** How a settlement sees a particular player. */
public enum ReputationRank {
    ENEMY(Integer.MIN_VALUE, ChatFormatting.DARK_RED),
    UNWELCOME(-50, ChatFormatting.RED),
    STRANGER(-10, ChatFormatting.GRAY),
    FRIEND(10, ChatFormatting.GREEN),
    HONORED(40, ChatFormatting.AQUA),
    HERO(75, ChatFormatting.GOLD);

    private final int from;
    private final ChatFormatting color;

    ReputationRank(int from, ChatFormatting color) {
        this.from = from;
        this.color = color;
    }

    public static ReputationRank of(int reputation) {
        ReputationRank result = ENEMY;
        for (ReputationRank rank : values()) {
            if (reputation >= rank.from) {
                result = rank;
            }
        }
        return result;
    }

    public Component displayName() {
        return Component.translatable("cities.reputation." + name().toLowerCase(Locale.ROOT)).withStyle(color);
    }
}
