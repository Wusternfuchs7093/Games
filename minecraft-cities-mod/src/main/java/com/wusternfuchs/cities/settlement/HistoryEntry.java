package com.wusternfuchs.cities.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** One line of a settlement's chronicle, e.g. "Day 112: grew into a Town". */
public record HistoryEntry(long day, String key, String argument) {
    public Component describe() {
        Object arg = key.equals("promoted") || key.equals("demoted")
                ? Component.translatable("cities.tier." + argument)
                : argument;
        return Component.translatable("cities.history.line", day,
                Component.translatable("cities.history." + key, arg));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("day", day);
        tag.putString("key", key);
        tag.putString("arg", argument);
        return tag;
    }

    public static HistoryEntry load(CompoundTag tag) {
        return new HistoryEntry(tag.getLong("day"), tag.getString("key"), tag.getString("arg"));
    }
}
