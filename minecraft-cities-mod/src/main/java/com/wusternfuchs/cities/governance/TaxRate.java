package com.wusternfuchs.cities.governance;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum TaxRate {
    LOW(1, 2, 8),
    NORMAL(1, 1, 0),
    HIGH(2, 1, -12);

    private final int numerator;
    private final int denominator;
    private final int happiness;

    TaxRate(int numerator, int denominator, int happiness) {
        this.numerator = numerator;
        this.denominator = denominator;
        this.happiness = happiness;
    }

    /** Wealth collected from {@code workers} employed villagers in one census. */
    public int collect(int workers) {
        return workers * numerator / denominator;
    }

    public int happiness() {
        return happiness;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("cities.tax." + id());
    }

    public static TaxRate byId(String id) {
        for (TaxRate rate : values()) {
            if (rate.id().equalsIgnoreCase(id)) {
                return rate;
            }
        }
        return null;
    }
}
