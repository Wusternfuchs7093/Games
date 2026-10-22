package com.wusternfuchs.cities.governance;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Decrees a mayor can put in force. Each one helps with something and costs some happiness. */
public enum Law {
    /** Streets empty at night: raids are half as likely. */
    CURFEW(-5),
    /** Twice as many guards are wanted. */
    CONSCRIPTION(-8),
    /** Everybody eats half as much. */
    RATIONING(-10),
    /** Builders work twice as fast and everyone gathers more materials. */
    BUILDING_PROGRAM(-5),
    /** No newcomers, no caravans, fewer raids. */
    CLOSED_BORDERS(-5);

    private final int happiness;

    Law(int happiness) {
        this.happiness = happiness;
    }

    public int happiness() {
        return happiness;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("cities.law." + id());
    }

    public Component description() {
        return Component.translatable("cities.law." + id() + ".desc");
    }

    public static Law byId(String id) {
        for (Law law : values()) {
            if (law.id().equalsIgnoreCase(id)) {
                return law;
            }
        }
        return null;
    }
}
