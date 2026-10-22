package com.wusternfuchs.cities.defense;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A band of raiders currently attacking a settlement. */
public final class Siege {
    private final List<UUID> attackers;
    private final long startTick;
    private final int initialSize;

    public Siege(List<UUID> attackers, long startTick) {
        this(attackers, startTick, attackers.size());
    }

    private Siege(List<UUID> attackers, long startTick, int initialSize) {
        this.attackers = new ArrayList<>(attackers);
        this.startTick = startTick;
        this.initialSize = initialSize;
    }

    public List<UUID> attackers() {
        return attackers;
    }

    public long startTick() {
        return startTick;
    }

    public int initialSize() {
        return initialSize;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        attackers.forEach(id -> list.add(NbtUtils.createUUID(id)));
        tag.put("attackers", list);
        tag.putLong("start", startTick);
        tag.putInt("initial", initialSize);
        return tag;
    }

    public static Siege load(CompoundTag tag) {
        List<UUID> attackers = new ArrayList<>();
        ListTag list = tag.getList("attackers", Tag.TAG_INT_ARRAY);
        for (Tag entry : list) {
            attackers.add(NbtUtils.loadUUID(entry));
        }
        return new Siege(attackers, tag.getLong("start"), tag.getInt("initial"));
    }
}
