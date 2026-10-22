package com.wusternfuchs.cities.settlement;

import com.wusternfuchs.cities.trade.Caravan;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All settlements of one dimension plus a chunk ownership index. */
public final class SettlementData extends SavedData {
    private static final String FILE_NAME = "cities_settlements";
    private static final Factory<SettlementData> FACTORY = new Factory<>(SettlementData::new, SettlementData::load, null);

    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();
    private final Long2ObjectMap<UUID> chunkOwners = new Long2ObjectOpenHashMap<>();
    private final List<Caravan> caravans = new ArrayList<>();

    public static SettlementData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public Collection<Settlement> all() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    @Nullable
    public Settlement byId(@Nullable UUID id) {
        return id == null ? null : settlements.get(id);
    }

    @Nullable
    public Settlement byName(String name) {
        for (Settlement s : settlements.values()) {
            if (s.name().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    public boolean isNameTaken(String name) {
        return byName(name) != null;
    }

    @Nullable
    public Settlement at(ChunkPos chunk) {
        return byId(chunkOwners.get(chunk.toLong()));
    }

    @Nullable
    public Settlement at(BlockPos pos) {
        return at(new ChunkPos(pos));
    }

    @Nullable
    public Settlement nearest(BlockPos pos, double maxDistance) {
        Settlement best = null;
        double bestDist = maxDistance * maxDistance;
        for (Settlement s : settlements.values()) {
            double dist = s.center().distSqr(pos);
            if (dist <= bestDist) {
                best = s;
                bestDist = dist;
            }
        }
        return best;
    }

    public void add(Settlement settlement) {
        settlements.put(settlement.id(), settlement);
        updateTerritory(settlement);
        setDirty();
    }

    public void remove(Settlement settlement) {
        releaseTerritory(settlement);
        settlements.remove(settlement.id());
        caravans.removeIf(c -> c.from().equals(settlement.id()) || c.to().equals(settlement.id()));
        for (Settlement other : settlements.values()) {
            other.forgetRelation(settlement.id());
        }
        setDirty();
    }

    /** Caravans currently on the road in this dimension. */
    public List<Caravan> caravans() {
        return caravans;
    }

    /**
     * Claims a roughly circular area of chunks around the center, sized by tier.
     * Chunks already owned by a neighbour are left to the neighbour.
     */
    public void updateTerritory(Settlement settlement) {
        releaseTerritory(settlement);
        ChunkPos center = settlement.centerChunk();
        int r = settlement.tier().radiusChunks();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + r) {
                    continue;
                }
                long key = ChunkPos.asLong(center.x + dx, center.z + dz);
                if (!chunkOwners.containsKey(key)) {
                    chunkOwners.put(key, settlement.id());
                    settlement.chunks().add(key);
                }
            }
        }
        setDirty();
    }

    private void releaseTerritory(Settlement settlement) {
        for (long key : settlement.chunks()) {
            if (settlement.id().equals(chunkOwners.get(key))) {
                chunkOwners.remove(key);
            }
        }
        settlement.chunks().clear();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        settlements.values().forEach(s -> list.add(s.save()));
        tag.put("settlements", list);
        ListTag roads = new ListTag();
        caravans.forEach(c -> roads.add(c.save()));
        tag.put("caravans", roads);
        return tag;
    }

    private static SettlementData load(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementData data = new SettlementData();
        ListTag list = tag.getList("settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Settlement s = Settlement.load(list.getCompound(i));
            data.settlements.put(s.id(), s);
            for (long chunk : s.chunks()) {
                data.chunkOwners.putIfAbsent(chunk, s.id());
            }
        }
        ListTag roads = tag.getList("caravans", Tag.TAG_COMPOUND);
        for (int i = 0; i < roads.size(); i++) {
            Caravan caravan = Caravan.load(roads.getCompound(i));
            if (caravan != null) {
                data.caravans.add(caravan);
            }
        }
        return data;
    }
}
