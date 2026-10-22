package com.wusternfuchs.cities.settlement;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.CitiesMod;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.building.ConstructionManager;
import com.wusternfuchs.cities.building.TownPlanner;
import com.wusternfuchs.cities.defense.DefenseManager;
import com.wusternfuchs.cities.diplomacy.DiplomacyManager;
import com.wusternfuchs.cities.trade.TradeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-side simulation: finds villages, counts their people and lets them grow or decline. */
public final class SettlementManager {
    private static final int CENSUS_HEIGHT = 48;
    private static final int ABANDON_AFTER_EMPTY_CENSUSES = 10;
    private static final int DEMOTE_AFTER_DECLINING_CENSUSES = 3;
    private static final int REPUTATION_KILL_VILLAGER = -25;
    private static final int STARTING_FOOD = 50;
    private static final int STARTING_MATERIALS = 64;
    private static final int SIEGE_TICK_INTERVAL = 40;
    private static final int CARAVAN_TICK_INTERVAL = 100;

    /** Settlement each online player stood in last time we checked, for enter/leave titles. */
    private static final Map<UUID, UUID> PLAYER_LOCATIONS = new HashMap<>();

    private SettlementManager() {
    }

    public static long day(ServerLevel level) {
        return level.getDayTime() / 24000L;
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(ServerLevel level) {
        long time = level.getGameTime();
        if (time % CitiesConfig.DETECTION_INTERVAL.get() == 0) {
            detectVillages(level);
        }
        if (CitiesConfig.CONSTRUCTION_ENABLED.get() && time % CitiesConfig.BUILD_INTERVAL.get() == 0) {
            SettlementData data = SettlementData.get(level);
            for (Settlement settlement : new ArrayList<>(data.all())) {
                if (settlement.project() != null && level.isPositionEntityTicking(settlement.center())) {
                    ConstructionManager.step(level, data, settlement);
                }
            }
        }
        if (time % SIEGE_TICK_INTERVAL == 0) {
            SettlementData data = SettlementData.get(level);
            for (Settlement settlement : new ArrayList<>(data.all())) {
                if (settlement.siege() != null && level.isPositionEntityTicking(settlement.center())) {
                    DefenseManager.tickSiege(level, data, settlement);
                }
            }
        }
        if (time % CARAVAN_TICK_INTERVAL == 0) {
            TradeManager.resolveArrivals(level, SettlementData.get(level));
        }
        if (time % CitiesConfig.CENSUS_INTERVAL.get() == 0) {
            SettlementData data = SettlementData.get(level);
            for (Settlement settlement : new ArrayList<>(data.all())) {
                if (level.isPositionEntityTicking(settlement.center())) {
                    simulate(level, data, settlement);
                }
            }
        }
    }

    // ------------------------------------------------------------------ detection

    private static void detectVillages(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;
        }
        SettlementData data = SettlementData.get(level);
        PoiManager poi = level.getPoiManager();
        int radius = CitiesConfig.DETECTION_RADIUS.get();
        List<BlockPos> bells = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            poi.findAll(type -> type.is(PoiTypes.MEETING), pos -> true, player.blockPosition(), radius,
                    PoiManager.Occupancy.ANY).forEach(pos -> bells.add(pos.immutable()));
        }
        for (BlockPos bell : bells) {
            if (canFoundAt(level, data, bell)
                    && poi.getInRange(type -> type.is(PoiTypes.HOME), bell, 48, PoiManager.Occupancy.ANY).findAny().isPresent()) {
                found(level, data, bell, null);
            }
        }
    }

    public static boolean canFoundAt(ServerLevel level, SettlementData data, BlockPos pos) {
        return data.at(pos) == null && data.nearest(pos, CitiesConfig.MIN_DISTANCE.get()) == null;
    }

    /** Registers a new settlement at {@code center}. A null name means "generate one". */
    public static Settlement found(ServerLevel level, SettlementData data, BlockPos center, @Nullable String name) {
        ArchitectureStyle style = ArchitectureStyle.fromBiome(level.getBiome(center));
        RandomSource random = level.getRandom();
        String finalName = name != null ? name : NameGenerator.generate(style, random, data::isNameTaken);
        Settlement settlement = new Settlement(UUID.randomUUID(), center, finalName, style,
                rollPotential(level, center, style, random), day(level));
        settlement.record(day(level), "founded", finalName);
        settlement.addFood(STARTING_FOOD);
        settlement.addMaterials(STARTING_MATERIALS);
        data.add(settlement);
        census(level, data, settlement);
        CitiesMod.LOGGER.info("Registered settlement {} at {} (potential {})", finalName, center, settlement.potential());
        return settlement;
    }

    /**
     * Hidden growth potential (0..100): water nearby, flat land and a friendly biome make a future city.
     * Only already-loaded chunks are sampled so detection never forces chunk generation.
     */
    private static int rollPotential(ServerLevel level, BlockPos center, ArchitectureStyle style, RandomSource random) {
        int potential = 30 + style.potentialBonus();

        boolean water = false;
        List<Integer> heights = new ArrayList<>();
        for (int dx = -48; dx <= 48; dx += 12) {
            for (int dz = -48; dz <= 48; dz += 12) {
                BlockPos column = center.offset(dx, 0, dz);
                if (!level.isLoaded(column)) {
                    continue;
                }
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
                heights.add(surface);
                if (!water && level.getFluidState(new BlockPos(column.getX(), surface - 1, column.getZ())).is(FluidTags.WATER)) {
                    water = true;
                }
            }
        }
        if (water) {
            potential += 20;
        }
        if (heights.size() > 1) {
            double mean = heights.stream().mapToInt(Integer::intValue).average().orElse(0);
            double variance = heights.stream().mapToDouble(h -> (h - mean) * (h - mean)).average().orElse(0);
            double flatness = 1.0 - Math.min(1.0, Math.sqrt(variance) / 12.0);
            potential += (int) Math.round(25 * flatness);
        }
        potential += random.nextInt(21) - 10;
        return Mth.clamp(potential, 0, 100);
    }

    // ------------------------------------------------------------------ simulation

    public static void simulate(ServerLevel level, SettlementData data, Settlement s) {
        census(level, data, s);
        ConstructionManager.collectStorage(level, s);
        runEconomy(s);
        updateHappiness(level, s);
        migrate(level, data, s);
        DefenseManager.updateGarrison(level, data, s);
        updateTier(level, data, s);
        TownPlanner.plan(level, data, s);
        DiplomacyManager.drift(level, data, s);
        TradeManager.plan(level, data, s);
        DefenseManager.maybeStartSiege(level, data, s, false);
        data.setDirty();
    }

    /** Counts villagers, golems, beds and workstations inside the settlement. */
    public static void census(ServerLevel level, SettlementData data, Settlement s) {
        BlockPos c = s.center();
        int radius = (s.tier().radiusChunks() + 1) * 16;
        AABB box = new AABB(c).inflate(radius, CENSUS_HEIGHT, radius);

        int adults = 0;
        int children = 0;
        int employed = 0;
        int foodProducers = 0;
        for (Villager villager : level.getEntitiesOfClass(Villager.class, box, Villager::isAlive)) {
            if (data.at(villager.blockPosition()) != s) {
                continue;
            }
            if (villager.isBaby()) {
                children++;
                continue;
            }
            adults++;
            VillagerProfession profession = villager.getVillagerData().getProfession();
            if (profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT) {
                employed++;
            }
            if (profession == VillagerProfession.FARMER || profession == VillagerProfession.FISHERMAN) {
                foodProducers++;
            }
        }
        int golems = level.getEntitiesOfClass(IronGolem.class, box, golem -> golem.isAlive()
                && data.at(golem.blockPosition()) == s).size();

        PoiManager poi = level.getPoiManager();
        int beds = (int) poi.getInRange(type -> type.is(PoiTypes.HOME), c, radius, PoiManager.Occupancy.ANY).count();
        int workstations = (int) poi.getInRange(VillagerProfession.ALL_ACQUIRABLE_JOBS, c, radius,
                PoiManager.Occupancy.ANY).count();

        s.applyCensus(adults, children, beds, workstations, employed, foodProducers, golems);
    }

    /**
     * Workers produce, everybody eats. Farmers and fishermen feed the town, other adults forage a little.
     * Craftsmen earn wealth; everyone gathers wood and stone, idle hands more so.
     */
    private static void runEconomy(Settlement s) {
        int produced = s.foodProducers() * 6 + s.adults() / 2;
        s.addFood(produced - s.population() * CitiesConfig.FOOD_PER_VILLAGER.get());
        s.addWealth(s.employed());
        int idle = s.adults() - s.employed();
        s.addMaterials(s.employed() + idle * 2);
    }

    private static void updateHappiness(ServerLevel level, Settlement s) {
        int target = 50;
        int freeBeds = s.beds() - s.population();
        target += freeBeds >= 0 ? Math.min(15, freeBeds * 3) : Math.max(-30, freeBeds * 5);
        if (s.adults() > 0) {
            target += (int) Math.round(15.0 * s.employed() / s.adults()) - 5;
        }
        target += Math.min(10, s.golems() * 5);
        target += Math.min(15, s.guards() * 3);
        target += Math.min(9, s.count(BuildingType.WATCHTOWER) * 3);
        target += Math.min(10, s.count(BuildingType.MARKET) * 5);
        target += s.count(BuildingType.WELL) > 0 ? 5 : 0;
        if (s.food() <= 0) {
            target -= 25;
        } else if (s.food() >= s.population() * 20) {
            target += 10;
        }
        if (level.isRaided(s.center())) {
            target -= 30;
        }
        target -= Math.min(30, s.recentDeaths() * 10);
        s.decayRecentDeaths();

        s.setHappiness((int) Math.round(s.happiness() * 0.6 + Mth.clamp(target, 0, 100) * 0.4));
    }

    /** Prosperous towns attract newcomers; miserable ones lose people. */
    private static void migrate(ServerLevel level, SettlementData data, Settlement s) {
        RandomSource random = level.getRandom();
        boolean prosperous = s.happiness() >= 60 && s.beds() > s.population() && s.food() >= s.population() * 5;
        if (prosperous && s.tier().ordinal() >= SettlementTier.VILLAGE.ordinal()
                && random.nextDouble() < CitiesConfig.IMMIGRATION_CHANCE.get()) {
            if (spawnImmigrant(level, s)) {
                s.record(day(level), "immigrant", "");
            }
        } else if (CitiesConfig.ALLOW_EMIGRATION.get() && s.happiness() < 15 && s.adults() > 2
                && random.nextDouble() < 0.1) {
            BlockPos c = s.center();
            int radius = s.tier().radiusChunks() * 16;
            List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(c).inflate(radius, CENSUS_HEIGHT, radius),
                    v -> v.isAlive() && !v.isBaby() && data.at(v.blockPosition()) == s);
            if (!villagers.isEmpty()) {
                villagers.get(random.nextInt(villagers.size())).discard();
                s.record(day(level), "emigrant", "");
            }
        }
    }

    private static boolean spawnImmigrant(ServerLevel level, Settlement s) {
        RandomSource random = level.getRandom();
        BlockPos near = s.center().offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near);
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return false;
        }
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        return level.addFreshEntity(villager);
    }

    private static void updateTier(ServerLevel level, SettlementData data, Settlement s) {
        if (!s.censusDone()) {
            return;
        }
        if (s.population() == 0) {
            s.setEmptyCensuses(s.emptyCensuses() + 1);
            if (!s.abandoned() && s.emptyCensuses() >= ABANDON_AFTER_EMPTY_CENSUSES) {
                s.setAbandoned(true);
                s.record(day(level), "abandoned", "");
                announce(level, s, Component.translatable("cities.announce.abandoned", s.name()).withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        s.setEmptyCensuses(0);
        if (s.abandoned()) {
            s.setAbandoned(false);
            s.record(day(level), "resettled", "");
        }

        SettlementTier next = s.tier().next();
        if (next != null && canReach(s, next)) {
            changeTier(level, data, s, next);
            return;
        }

        double scale = CitiesConfig.POPULATION_SCALE.get();
        SettlementTier previous = s.tier().previous();
        if (previous != null && s.population() < s.tier().minPopulation(scale) / 2) {
            s.setDecline(s.decline() + 1);
            if (s.decline() >= DEMOTE_AFTER_DECLINING_CENSUSES) {
                changeTier(level, data, s, previous);
            }
        } else {
            s.setDecline(0);
        }
    }

    public static boolean canReach(Settlement s, SettlementTier tier) {
        if (!CitiesConfig.IGNORE_POTENTIAL.get() && tier.ordinal() > s.maxTier().ordinal()) {
            return false;
        }
        for (Requirement requirement : Requirement.forTier(s, tier)) {
            if (!requirement.met()) {
                return false;
            }
        }
        return true;
    }

    public static void changeTier(ServerLevel level, SettlementData data, Settlement s, SettlementTier tier) {
        boolean promotion = tier.ordinal() > s.tier().ordinal();
        s.setTier(tier);
        s.setDecline(0);
        data.updateTerritory(s);
        DefenseManager.refitGuards(level, s);
        s.record(day(level), promotion ? "promoted" : "demoted", tier.id());

        Component message = Component.translatable(promotion ? "cities.announce.promoted" : "cities.announce.demoted",
                s.name(), tier.displayName()).withStyle(promotion ? ChatFormatting.GOLD : ChatFormatting.RED);
        announce(level, s, message);
        if (promotion) {
            level.playSound(null, s.center(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0f, 1.0f);
            for (ServerPlayer player : level.players()) {
                if (data.at(player.blockPosition()) == s) {
                    showTitle(player, Component.literal(s.name()).withStyle(ChatFormatting.GOLD), message);
                }
            }
        }
    }

    private static void announce(ServerLevel level, Settlement s, Component message) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }

    // ------------------------------------------------------------------ players

    public static void trackPlayer(ServerPlayer player) {
        if (player.tickCount % 20 != 0 || !CitiesConfig.ENTER_TITLES.get()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        SettlementData data = SettlementData.get(level);
        Settlement current = data.at(new ChunkPos(player.blockPosition()));
        UUID currentId = current == null ? null : current.id();
        UUID previousId = PLAYER_LOCATIONS.get(player.getUUID());
        if (PLAYER_LOCATIONS.containsKey(player.getUUID()) && Objects.equals(currentId, previousId)) {
            return;
        }
        boolean firstCheck = !PLAYER_LOCATIONS.containsKey(player.getUUID());
        PLAYER_LOCATIONS.put(player.getUUID(), currentId);
        if (firstCheck) {
            return;
        }

        if (current != null) {
            Component subtitle = current.abandoned()
                    ? Component.translatable("cities.title.abandoned")
                    : Component.translatable("cities.title.subtitle", current.tier().displayName(), current.population());
            showTitle(player, Component.literal(current.name()).withStyle(ChatFormatting.GOLD), subtitle);
        } else {
            Settlement left = data.byId(previousId);
            if (left != null) {
                player.displayClientMessage(Component.translatable("cities.title.leaving", left.name()), true);
            }
        }
    }

    public static void forgetPlayer(Player player) {
        PLAYER_LOCATIONS.remove(player.getUUID());
    }

    public static void showTitle(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void onVillagerDeath(ServerLevel level, Villager villager, @Nullable ServerPlayer killer) {
        SettlementData data = SettlementData.get(level);
        Settlement s = data.at(villager.blockPosition());
        if (s == null) {
            return;
        }
        s.addRecentDeath();
        if (killer != null) {
            s.changeReputation(killer.getUUID(), REPUTATION_KILL_VILLAGER);
            s.record(day(level), "murder", killer.getGameProfile().getName());
            killer.displayClientMessage(Component.translatable("cities.reputation.murder", s.name())
                    .withStyle(ChatFormatting.RED), true);
        }
        data.setDirty();
    }
}
