package com.wusternfuchs.cities.settlement;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Persistent state of one village, town or city. */
public final class Settlement {
    public static final int MAX_HISTORY = 64;
    public static final int MIN_REPUTATION = -100;
    public static final int MAX_REPUTATION = 100;

    private final UUID id;
    private final BlockPos center;
    private String name;
    private ArchitectureStyle style;
    private SettlementTier tier = SettlementTier.HAMLET;
    private int potential;
    private long foundedDay;

    // Last census
    private int adults;
    private int children;
    private int beds;
    private int workstations;
    private int employed;
    private int foodProducers;
    private int golems;
    private boolean censusDone;

    // Economy and mood
    private int food;
    private int wealth;
    private int happiness = 50;
    private int recentDeaths;

    // Trend counters
    private int decline;
    private int emptyCensuses;
    private boolean abandoned;

    private final LongSet chunks = new LongOpenHashSet();
    private final Object2IntMap<UUID> reputation = new Object2IntOpenHashMap<>();
    private final List<HistoryEntry> history = new ArrayList<>();

    public Settlement(UUID id, BlockPos center, String name, ArchitectureStyle style, int potential, long foundedDay) {
        this.id = id;
        this.center = center.immutable();
        this.name = name;
        this.style = style;
        this.potential = potential;
        this.foundedDay = foundedDay;
    }

    public UUID id() {
        return id;
    }

    public BlockPos center() {
        return center;
    }

    public ChunkPos centerChunk() {
        return new ChunkPos(center);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArchitectureStyle style() {
        return style;
    }

    public SettlementTier tier() {
        return tier;
    }

    public void setTier(SettlementTier tier) {
        this.tier = tier;
    }

    public int potential() {
        return potential;
    }

    /** The highest tier this settlement's land can support. */
    public SettlementTier maxTier() {
        if (potential < 25) return SettlementTier.VILLAGE;
        if (potential < 45) return SettlementTier.SMALL_TOWN;
        if (potential < 65) return SettlementTier.CITY;
        if (potential < 85) return SettlementTier.METROPOLIS;
        return SettlementTier.CAPITAL;
    }

    public long foundedDay() {
        return foundedDay;
    }

    public int population() {
        return adults + children;
    }

    public int adults() {
        return adults;
    }

    public int children() {
        return children;
    }

    public int beds() {
        return beds;
    }

    public int workstations() {
        return workstations;
    }

    public int employed() {
        return employed;
    }

    public int foodProducers() {
        return foodProducers;
    }

    public int golems() {
        return golems;
    }

    public boolean censusDone() {
        return censusDone;
    }

    public void applyCensus(int adults, int children, int beds, int workstations, int employed,
                            int foodProducers, int golems) {
        this.adults = adults;
        this.children = children;
        this.beds = beds;
        this.workstations = workstations;
        this.employed = employed;
        this.foodProducers = foodProducers;
        this.golems = golems;
        this.censusDone = true;
    }

    public int food() {
        return food;
    }

    public void addFood(int amount) {
        food = Math.max(0, food + amount);
    }

    public int wealth() {
        return wealth;
    }

    public void addWealth(int amount) {
        wealth = Math.max(0, wealth + amount);
    }

    public int happiness() {
        return happiness;
    }

    public void setHappiness(int happiness) {
        this.happiness = Mth.clamp(happiness, 0, 100);
    }

    public int recentDeaths() {
        return recentDeaths;
    }

    public void addRecentDeath() {
        recentDeaths++;
    }

    public void decayRecentDeaths() {
        recentDeaths = Math.max(0, recentDeaths - 1);
    }

    public int decline() {
        return decline;
    }

    public void setDecline(int decline) {
        this.decline = decline;
    }

    public int emptyCensuses() {
        return emptyCensuses;
    }

    public void setEmptyCensuses(int emptyCensuses) {
        this.emptyCensuses = emptyCensuses;
    }

    public boolean abandoned() {
        return abandoned;
    }

    public void setAbandoned(boolean abandoned) {
        this.abandoned = abandoned;
    }

    public LongSet chunks() {
        return chunks;
    }

    public int reputation(UUID player) {
        return reputation.getOrDefault(player, 0);
    }

    public void changeReputation(UUID player, int delta) {
        reputation.put(player, Mth.clamp(reputation(player) + delta, MIN_REPUTATION, MAX_REPUTATION));
    }

    public List<HistoryEntry> history() {
        return Collections.unmodifiableList(history);
    }

    public void record(long day, String key, String argument) {
        history.add(new HistoryEntry(day, key, argument));
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.put("center", NbtUtils.writeBlockPos(center));
        tag.putString("name", name);
        tag.putString("style", style.name());
        tag.putString("tier", tier.id());
        tag.putInt("potential", potential);
        tag.putLong("founded", foundedDay);

        tag.putInt("adults", adults);
        tag.putInt("children", children);
        tag.putInt("beds", beds);
        tag.putInt("workstations", workstations);
        tag.putInt("employed", employed);
        tag.putInt("foodProducers", foodProducers);
        tag.putInt("golems", golems);
        tag.putBoolean("censusDone", censusDone);

        tag.putInt("food", food);
        tag.putInt("wealth", wealth);
        tag.putInt("happiness", happiness);
        tag.putInt("recentDeaths", recentDeaths);
        tag.putInt("decline", decline);
        tag.putInt("emptyCensuses", emptyCensuses);
        tag.putBoolean("abandoned", abandoned);

        tag.putLongArray("chunks", chunks.toLongArray());

        CompoundTag rep = new CompoundTag();
        reputation.forEach((player, value) -> rep.putInt(player.toString(), value));
        tag.put("reputation", rep);

        ListTag log = new ListTag();
        history.forEach(entry -> log.add(entry.save()));
        tag.put("history", log);
        return tag;
    }

    public static Settlement load(CompoundTag tag) {
        BlockPos center = NbtUtils.readBlockPos(tag, "center").orElse(BlockPos.ZERO);
        Settlement s = new Settlement(tag.getUUID("id"), center, tag.getString("name"),
                ArchitectureStyle.byName(tag.getString("style")), tag.getInt("potential"), tag.getLong("founded"));
        SettlementTier tier = SettlementTier.byId(tag.getString("tier"));
        s.tier = tier == null ? SettlementTier.HAMLET : tier;

        s.adults = tag.getInt("adults");
        s.children = tag.getInt("children");
        s.beds = tag.getInt("beds");
        s.workstations = tag.getInt("workstations");
        s.employed = tag.getInt("employed");
        s.foodProducers = tag.getInt("foodProducers");
        s.golems = tag.getInt("golems");
        s.censusDone = tag.getBoolean("censusDone");

        s.food = tag.getInt("food");
        s.wealth = tag.getInt("wealth");
        s.happiness = tag.getInt("happiness");
        s.recentDeaths = tag.getInt("recentDeaths");
        s.decline = tag.getInt("decline");
        s.emptyCensuses = tag.getInt("emptyCensuses");
        s.abandoned = tag.getBoolean("abandoned");

        for (long chunk : tag.getLongArray("chunks")) {
            s.chunks.add(chunk);
        }

        CompoundTag rep = tag.getCompound("reputation");
        for (String key : rep.getAllKeys()) {
            try {
                s.reputation.put(UUID.fromString(key), rep.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // Corrupted entry: skip it rather than losing the whole settlement.
            }
        }

        ListTag log = tag.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < log.size(); i++) {
            s.history.add(HistoryEntry.load(log.getCompound(i)));
        }
        return s;
    }
}
