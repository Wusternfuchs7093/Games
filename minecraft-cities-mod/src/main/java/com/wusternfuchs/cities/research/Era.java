package com.wusternfuchs.cities.research;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Ages of a settlement. Each age opens new technologies; the next one comes after two of them are known. */
public enum Era {
    FOUNDING,
    IRON,
    MEDIEVAL,
    RENAISSANCE;

    /** Technologies of an era needed to enter the next one. */
    public static final int TECHS_TO_ADVANCE = 2;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("cities.era." + id());
    }
}
