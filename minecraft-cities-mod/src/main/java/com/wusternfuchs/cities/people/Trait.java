package com.wusternfuchs.cities.people;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** A villager's temperament. Counted per settlement each census. */
public enum Trait {
    HARDWORKING,
    LAZY,
    CHEERFUL,
    GRUMPY,
    BRAVE,
    GREEDY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("cities.trait." + id());
    }

    public static Trait byId(String id) {
        for (Trait trait : values()) {
            if (trait.id().equals(id)) {
                return trait;
            }
        }
        return null;
    }
}
