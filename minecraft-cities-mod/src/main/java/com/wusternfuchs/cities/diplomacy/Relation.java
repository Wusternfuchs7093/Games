package com.wusternfuchs.cities.diplomacy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** How two settlements feel about each other. */
public enum Relation {
    HOSTILE(Integer.MIN_VALUE, ChatFormatting.DARK_RED),
    RIVAL(-49, ChatFormatting.RED),
    NEUTRAL(-14, ChatFormatting.GRAY),
    FRIENDLY(15, ChatFormatting.GREEN),
    ALLIED(50, ChatFormatting.AQUA);

    private final int from;
    private final ChatFormatting color;

    Relation(int from, ChatFormatting color) {
        this.from = from;
        this.color = color;
    }

    public static Relation of(int value) {
        Relation result = HOSTILE;
        for (Relation relation : values()) {
            if (value >= relation.from) {
                result = relation;
            }
        }
        return result;
    }

    /** Whether caravans may travel between the two. */
    public boolean allowsTrade() {
        return this != HOSTILE && this != RIVAL;
    }

    public Component displayName() {
        return Component.translatable("cities.relation." + name().toLowerCase(Locale.ROOT)).withStyle(color);
    }
}
