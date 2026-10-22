package com.wusternfuchs.cities.settlement;

import com.wusternfuchs.cities.building.Building;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.building.ConstructionProject;
import com.wusternfuchs.cities.defense.Siege;
import com.wusternfuchs.cities.governance.Law;
import com.wusternfuchs.cities.governance.TaxRate;
import com.wusternfuchs.cities.research.Era;
import com.wusternfuchs.cities.research.Tech;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private int guards;
    private int scholars;
    private boolean censusDone;

    // Economy and mood
    private int food;
    private int wealth;
    private int materials;
    private int happiness = 50;
    private int recentDeaths;

    // Trend counters
    private int decline;
    private int emptyCensuses;
    private boolean abandoned;

    private final LongSet chunks = new LongOpenHashSet();
    private final Object2IntMap<UUID> reputation = new Object2IntOpenHashMap<>();
    private final Object2IntMap<UUID> relations = new Object2IntOpenHashMap<>();
    private final List<HistoryEntry> history = new ArrayList<>();
    private final List<Building> buildings = new ArrayList<>();
    @Nullable
    private ConstructionProject project;
    @Nullable
    private Siege siege;
    private long lastSiegeDay = -100;

    // Government
    @Nullable
    private UUID mayor;
    private String mayorName = "";
    private final Set<Law> laws = EnumSet.noneOf(Law.class);
    private TaxRate tax = TaxRate.NORMAL;
    private long lastElectionDay;
    private final Map<UUID, String> candidates = new LinkedHashMap<>();
    private int festivalCensuses;
    private long lastFestivalDay = -100;

    // Research
    private final Set<Tech> techs = EnumSet.noneOf(Tech.class);
    @Nullable
    private Tech researching;
    private int researchProgress;

    public Settlement(UUID id, BlockPos center, String name, ArchitectureStyle style, int potential, long foundedDay) {
        this.id = id;
        this.center = center.immutable();
        this.name = name;
        this.style = style;
        this.potential = potential;
        this.foundedDay = foundedDay;
        this.lastElectionDay = foundedDay;
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

    public int guards() {
        return guards;
    }

    public void setGuards(int guards) {
        this.guards = guards;
    }

    @Nullable
    public Siege siege() {
        return siege;
    }

    public void setSiege(@Nullable Siege siege) {
        this.siege = siege;
    }

    public long lastSiegeDay() {
        return lastSiegeDay;
    }

    public void setLastSiegeDay(long day) {
        this.lastSiegeDay = day;
    }

    /** The player governing the settlement, or null when the council of elders rules. */
    @Nullable
    public UUID mayor() {
        return mayor;
    }

    public String mayorName() {
        return mayorName;
    }

    public void setMayor(@Nullable UUID mayor, String name) {
        this.mayor = mayor;
        this.mayorName = mayor == null ? "" : name;
    }

    public Set<Law> laws() {
        return laws;
    }

    public boolean hasLaw(Law law) {
        return laws.contains(law);
    }

    public TaxRate tax() {
        return tax;
    }

    public void setTax(TaxRate tax) {
        this.tax = tax;
    }

    public long lastElectionDay() {
        return lastElectionDay;
    }

    public void setLastElectionDay(long day) {
        this.lastElectionDay = day;
    }

    public Map<UUID, String> candidates() {
        return candidates;
    }

    public int festivalCensuses() {
        return festivalCensuses;
    }

    public void setFestivalCensuses(int censuses) {
        this.festivalCensuses = censuses;
    }

    public long lastFestivalDay() {
        return lastFestivalDay;
    }

    public void setLastFestivalDay(long day) {
        this.lastFestivalDay = day;
    }

    public int scholars() {
        return scholars;
    }

    public void setScholars(int scholars) {
        this.scholars = scholars;
    }

    public Set<Tech> techs() {
        return techs;
    }

    public boolean hasTech(Tech tech) {
        return techs.contains(tech);
    }

    /** The latest age reached: each needs enough technologies of the one before. */
    public Era era() {
        Era era = Era.FOUNDING;
        for (Era next : Era.values()) {
            if (next.ordinal() == 0) {
                continue;
            }
            Era previous = Era.values()[next.ordinal() - 1];
            long known = techs.stream().filter(t -> t.era() == previous).count();
            if (era != previous || known < Era.TECHS_TO_ADVANCE) {
                break;
            }
            era = next;
        }
        return era;
    }

    @Nullable
    public Tech researching() {
        return researching;
    }

    public void setResearching(@Nullable Tech tech) {
        if (tech != researching) {
            researchProgress = 0;
        }
        this.researching = tech;
    }

    public int researchProgress() {
        return researchProgress;
    }

    public void addResearchProgress(int points) {
        researchProgress += points;
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

    public int materials() {
        return materials;
    }

    public void addMaterials(int amount) {
        materials = Math.max(0, materials + amount);
    }

    public List<Building> buildings() {
        return Collections.unmodifiableList(buildings);
    }

    public int count(BuildingType type) {
        int count = 0;
        for (Building building : buildings) {
            if (building.type() == type) {
                count++;
            }
        }
        return count;
    }

    public void addBuilding(Building building) {
        buildings.add(building);
    }

    /** Replaces a building standing at the same spot (used when one is rebuilt in stone). */
    public void replaceBuilding(Building building) {
        buildings.removeIf(b -> b.type() == building.type() && b.origin().equals(building.origin()));
        buildings.add(building);
    }

    @Nullable
    public ConstructionProject project() {
        return project;
    }

    public void setProject(@Nullable ConstructionProject project) {
        this.project = project;
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

    /** Diplomatic standing towards another settlement, -100..100. */
    public int relation(UUID other) {
        return relations.getOrDefault(other, 0);
    }

    public void setRelation(UUID other, int value) {
        relations.put(other, Mth.clamp(value, -100, 100));
    }

    public void forgetRelation(UUID other) {
        relations.removeInt(other);
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
        tag.putInt("guards", guards);
        tag.putInt("scholars", scholars);
        tag.putBoolean("censusDone", censusDone);

        tag.putInt("food", food);
        tag.putInt("wealth", wealth);
        tag.putInt("materials", materials);
        tag.putInt("happiness", happiness);
        tag.putInt("recentDeaths", recentDeaths);
        tag.putInt("decline", decline);
        tag.putInt("emptyCensuses", emptyCensuses);
        tag.putBoolean("abandoned", abandoned);

        tag.putLongArray("chunks", chunks.toLongArray());

        CompoundTag rep = new CompoundTag();
        reputation.forEach((player, value) -> rep.putInt(player.toString(), value));
        tag.put("reputation", rep);

        CompoundTag rel = new CompoundTag();
        relations.forEach((other, value) -> rel.putInt(other.toString(), value));
        tag.put("relations", rel);

        ListTag log = new ListTag();
        history.forEach(entry -> log.add(entry.save()));
        tag.put("history", log);

        ListTag built = new ListTag();
        buildings.forEach(building -> built.add(building.save()));
        tag.put("buildings", built);
        if (project != null) {
            tag.put("project", project.save());
        }
        if (siege != null) {
            tag.put("siege", siege.save());
        }
        tag.putLong("lastSiegeDay", lastSiegeDay);

        if (mayor != null) {
            tag.putUUID("mayor", mayor);
            tag.putString("mayorName", mayorName);
        }
        ListTag lawList = new ListTag();
        laws.forEach(law -> lawList.add(StringTag.valueOf(law.id())));
        tag.put("laws", lawList);
        tag.putString("tax", tax.id());
        tag.putLong("lastElection", lastElectionDay);
        CompoundTag running = new CompoundTag();
        candidates.forEach((id, candidate) -> running.putString(id.toString(), candidate));
        tag.put("candidates", running);
        tag.putInt("festival", festivalCensuses);
        tag.putLong("lastFestival", lastFestivalDay);

        ListTag techList = new ListTag();
        techs.forEach(tech -> techList.add(StringTag.valueOf(tech.id())));
        tag.put("techs", techList);
        if (researching != null) {
            tag.putString("researching", researching.id());
        }
        tag.putInt("researchProgress", researchProgress);
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
        s.guards = tag.getInt("guards");
        s.scholars = tag.getInt("scholars");
        s.censusDone = tag.getBoolean("censusDone");

        s.food = tag.getInt("food");
        s.wealth = tag.getInt("wealth");
        s.materials = tag.getInt("materials");
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

        CompoundTag rel = tag.getCompound("relations");
        for (String key : rel.getAllKeys()) {
            try {
                s.relations.put(UUID.fromString(key), rel.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // Skip a corrupted entry.
            }
        }

        ListTag log = tag.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < log.size(); i++) {
            s.history.add(HistoryEntry.load(log.getCompound(i)));
        }

        ListTag built = tag.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < built.size(); i++) {
            Building building = Building.load(built.getCompound(i));
            if (building != null) {
                s.buildings.add(building);
            }
        }
        if (tag.contains("project", Tag.TAG_COMPOUND)) {
            s.project = ConstructionProject.load(tag.getCompound("project"));
        }
        if (tag.contains("siege", Tag.TAG_COMPOUND)) {
            s.siege = Siege.load(tag.getCompound("siege"));
        }
        if (tag.contains("lastSiegeDay")) {
            s.lastSiegeDay = tag.getLong("lastSiegeDay");
        }

        if (tag.hasUUID("mayor")) {
            s.setMayor(tag.getUUID("mayor"), tag.getString("mayorName"));
        }
        ListTag lawList = tag.getList("laws", Tag.TAG_STRING);
        for (int i = 0; i < lawList.size(); i++) {
            Law law = Law.byId(lawList.getString(i));
            if (law != null) {
                s.laws.add(law);
            }
        }
        TaxRate tax = TaxRate.byId(tag.getString("tax"));
        s.tax = tax == null ? TaxRate.NORMAL : tax;
        s.lastElectionDay = tag.contains("lastElection") ? tag.getLong("lastElection") : s.foundedDay;
        CompoundTag running = tag.getCompound("candidates");
        for (String key : running.getAllKeys()) {
            try {
                s.candidates.put(UUID.fromString(key), running.getString(key));
            } catch (IllegalArgumentException ignored) {
                // Skip a corrupted entry.
            }
        }
        ListTag techList = tag.getList("techs", Tag.TAG_STRING);
        for (int i = 0; i < techList.size(); i++) {
            Tech tech = Tech.byId(techList.getString(i));
            if (tech != null) {
                s.techs.add(tech);
            }
        }
        s.researching = Tech.byId(tag.getString("researching"));
        s.researchProgress = tag.getInt("researchProgress");
        s.festivalCensuses = tag.getInt("festival");
        if (tag.contains("lastFestival")) {
            s.lastFestivalDay = tag.getLong("lastFestival");
        }
        return s;
    }
}
