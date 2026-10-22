package com.wusternfuchs.cities.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A war between two settlements. The score runs from -100 (the attacker gives up) to 100 (the defender
 * surrenders). A battle is in progress while {@link #soldiers()} is not empty.
 */
public final class War {
    private final UUID attacker;
    private final UUID defender;
    private final long startDay;
    private int score;
    private long nextBattleTick;
    private final List<UUID> soldiers = new ArrayList<>();
    private int battleSize;
    private long battleStart;

    public War(UUID attacker, UUID defender, long startDay, long nextBattleTick) {
        this.attacker = attacker;
        this.defender = defender;
        this.startDay = startDay;
        this.nextBattleTick = nextBattleTick;
    }

    public UUID attacker() {
        return attacker;
    }

    public UUID defender() {
        return defender;
    }

    public boolean involves(UUID settlement) {
        return attacker.equals(settlement) || defender.equals(settlement);
    }

    public boolean between(UUID a, UUID b) {
        return attacker.equals(a) && defender.equals(b) || attacker.equals(b) && defender.equals(a);
    }

    public long startDay() {
        return startDay;
    }

    public int score() {
        return score;
    }

    public void addScore(int delta) {
        score = Math.max(-100, Math.min(100, score + delta));
    }

    public long nextBattleTick() {
        return nextBattleTick;
    }

    public void setNextBattleTick(long tick) {
        this.nextBattleTick = tick;
    }

    public List<UUID> soldiers() {
        return soldiers;
    }

    public boolean battleInProgress() {
        return battleStart > 0;
    }

    public int battleSize() {
        return battleSize;
    }

    public long battleStart() {
        return battleStart;
    }

    public void startBattle(List<UUID> army, long tick) {
        soldiers.clear();
        soldiers.addAll(army);
        battleSize = army.size();
        battleStart = tick;
    }

    public void endBattle() {
        soldiers.clear();
        battleSize = 0;
        battleStart = 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("attacker", attacker);
        tag.putUUID("defender", defender);
        tag.putLong("startDay", startDay);
        tag.putInt("score", score);
        tag.putLong("nextBattle", nextBattleTick);
        ListTag army = new ListTag();
        soldiers.forEach(id -> army.add(NbtUtils.createUUID(id)));
        tag.put("soldiers", army);
        tag.putInt("battleSize", battleSize);
        tag.putLong("battleStart", battleStart);
        return tag;
    }

    @Nullable
    public static War load(CompoundTag tag) {
        if (!tag.hasUUID("attacker") || !tag.hasUUID("defender")) {
            return null;
        }
        War war = new War(tag.getUUID("attacker"), tag.getUUID("defender"), tag.getLong("startDay"),
                tag.getLong("nextBattle"));
        war.score = tag.getInt("score");
        for (Tag entry : tag.getList("soldiers", Tag.TAG_INT_ARRAY)) {
            war.soldiers.add(NbtUtils.loadUUID(entry));
        }
        war.battleSize = tag.getInt("battleSize");
        war.battleStart = tag.getLong("battleStart");
        return war;
    }
}
