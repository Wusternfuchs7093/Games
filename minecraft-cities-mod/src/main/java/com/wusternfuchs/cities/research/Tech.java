package com.wusternfuchs.cities.research;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Technologies a settlement can research. Effects are applied where the matching mechanic lives. */
public enum Tech {
    AGRICULTURE(Era.FOUNDING),
    MASONRY(Era.FOUNDING),
    CARPENTRY(Era.FOUNDING),
    METALLURGY(Era.IRON),
    CURRENCY(Era.IRON),
    ARCHITECTURE(Era.IRON),
    ENGINEERING(Era.MEDIEVAL),
    MEDICINE(Era.MEDIEVAL),
    BANKING(Era.MEDIEVAL),
    PRINTING(Era.RENAISSANCE),
    NAVIGATION(Era.RENAISSANCE),
    CIVIC_CODE(Era.RENAISSANCE);

    private static final int[] COST_BY_ERA = {60, 150, 350, 700};

    private final Era era;

    Tech(Era era) {
        this.era = era;
    }

    public Era era() {
        return era;
    }

    public int cost() {
        return COST_BY_ERA[era.ordinal()];
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("cities.tech." + id());
    }

    public Component description() {
        return Component.translatable("cities.tech." + id() + ".desc");
    }

    public static Tech byId(String id) {
        for (Tech tech : values()) {
            if (tech.id().equalsIgnoreCase(id)) {
                return tech;
            }
        }
        return null;
    }
}
