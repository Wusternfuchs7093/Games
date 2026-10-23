package com.wusternfuchs.cities.building;

import com.wusternfuchs.cities.research.Era;

/** Architectural period: what the builders of an age know how to make. Several eras can share one. */
public enum Period {
    EARLY,
    MEDIEVAL,
    RENAISSANCE,
    INDUSTRIAL,
    MODERN;

    public static Period of(Era era) {
        return switch (era) {
            case FOUNDING, IRON -> EARLY;
            case MEDIEVAL -> MEDIEVAL;
            case RENAISSANCE -> RENAISSANCE;
            case INDUSTRIAL -> INDUSTRIAL;
            case MODERN -> MODERN;
        };
    }
}
