package com.wusternfuchs.cities.settlement;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.CitiesMod;
import com.wusternfuchs.cities.api.CitiesApi;
import com.wusternfuchs.cities.api.SettlementEvent;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.building.ConstructionManager;
import com.wusternfuchs.cities.building.TownPlanner;
import com.wusternfuchs.cities.defense.DefenseManager;
import com.wusternfuchs.cities.disaster.DisasterManager;
import com.wusternfuchs.cities.disaster.RuinManager;
import com.wusternfuchs.cities.diplomacy.DiplomacyManager;
import com.wusternfuchs.cities.governance.GovernanceManager;
import com.wusternfuchs.cities.governance.Law;
import com.wusternfuchs.cities.people.ResidentManager;
import com.wusternfuchs.cities.quest.QuestManager;
import com.wusternfuchs.cities.people.Trait;
import com.wusternfuchs.cities.research.ResearchManager;
import com.wusternfuchs.cities.research.Tech;
import com.wusternfuchs.cities.trade.TradeManager;
import com.wusternfuchs.cities.war.WarManager;
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
    private static final int FESTIVAL_TICK_INTERVAL = 10;

    /** How long the last census of a dimension took and how many settlements it simulated, for /city admin stats. */
    private static long lastCensusMicros;
    private static int lastCensusCount;

    public static long lastCensusMicros() {
        return lastCensusMicros;
    }

    public static int lastCensusCount() {
        return lastCensusCount;
    }

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
                    if (settlement.hasLaw(Law.BUILDING_PROGRAM) && settlement.project() != null) {
                        ConstructionManager.step(level, data, settlement);
                    }
                    // Architecture: one extra block every other round.
                    boolean evenRound = (time / CitiesConfig.BUILD_INTERVAL.get()) % 2 == 0;
                    if (settlement.hasTech(Tech.ARCHITECTURE) && evenRound && settlement.project() != null) {
                        ConstructionManager.step(level, data, settlement);
                    }
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
            if (CitiesConfig.WARS_ENABLED.get()) {
                WarManager.tick(level, data);
            }
            DisasterManager.tickFires(level, data);
        }
        if (time % FESTIVAL_TICK_INTERVAL == 0) {
            GovernanceManager.tickFestivals(level, SettlementData.get(level));
        }
        if (time % CARAVAN_TICK_INTERVAL == 0) {
            TradeManager.resolveArrivals(level, SettlementData.get(level));
        }
        if (time % CitiesConfig.CENSUS_INTERVAL.get() == 0) {
            SettlementData data = SettlementData.get(level);
            long started = System.nanoTime();
            int simulated = 0;
            for (Settlement settlement : new ArrayList<>(data.all())) {
                if (level.isPositionEntityTicking(settlement.center())) {
                    simulate(level, data, settlement);
                    simulated++;
                }
            }
            lastCensusMicros = (System.nanoTime() - started) / 1000;
            lastCensusCount = simulated;
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
        // Some villages are older than the player's arrival: they start rich and build up quickly.
        if (name == null && settlement.potential() >= 45 && random.nextDouble() < CitiesConfig.OLD_TOWN_CHANCE.get()) {
            settlement.addMaterials(600);
            settlement.addWealth(300);
            settlement.addFood(300);
            settlement.record(day(level), "old_town", "");
        }
        data.add(settlement);
        census(level, data, settlement);
        CitiesApi.post(new SettlementEvent.Founded(level, settlement));
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
        GovernanceManager.govern(level, data, s);
        ConstructionManager.collectStorage(level, s);
        DisasterManager.update(level, data, s);
        runEconomy(s);
        updateHappiness(level, s);
        migrate(level, data, s);
        DefenseManager.updateGarrison(level, data, s);
        s.setVassals(WarManager.vassals(data, s));
        WarManager.update(level, data, s);
        updateTier(level, data, s);
        ResearchManager.research(level, data, s);
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
        int scholars = 0;
        List<Villager> residents = new ArrayList<>();
        for (Villager villager : level.getEntitiesOfClass(Villager.class, box, Villager::isAlive)) {
            if (data.at(villager.blockPosition()) != s) {
                continue;
            }
            residents.add(villager);
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
            if (profession == VillagerProfession.LIBRARIAN || profession == VillagerProfession.CLERIC) {
                scholars++;
            }
        }
        int golems = level.getEntitiesOfClass(IronGolem.class, box, golem -> golem.isAlive()
                && data.at(golem.blockPosition()) == s).size();

        PoiManager poi = level.getPoiManager();
        int beds = (int) poi.getInRange(type -> type.is(PoiTypes.HOME), c, radius, PoiManager.Occupancy.ANY).count();
        int workstations = (int) poi.getInRange(VillagerProfession.ALL_ACQUIRABLE_JOBS, c, radius,
                PoiManager.Occupancy.ANY).count();

        s.applyCensus(adults, children, beds, workstations, employed, foodProducers, golems);
        s.setScholars(scholars);
        ResidentManager.process(level, s, residents);
        QuestManager.update(level, s, residents);
    }

    /**
     * Workers produce, everybody eats. Farmers and fishermen feed the town, other adults forage a little.
     * Craftsmen earn wealth; everyone gathers wood and stone, idle hands more so.
     */
    private static void runEconomy(Settlement s) {
        int produced = s.foodProducers() * 6 + s.adults() / 2;
        if (s.hasTech(Tech.AGRICULTURE)) {
            produced += produced / 4;
        }
        produced += produced * s.style().foodBonusPercent() / 100;
        produced -= DisasterManager.droughtLoss(s, produced);
        int eaten = s.population() * CitiesConfig.FOOD_PER_VILLAGER.get() * s.style().consumptionPercent() / 100;
        if (s.hasLaw(Law.RATIONING)) {
            eaten /= 2;
        }
        s.addFood(produced - eaten);
        // The sick stay home; the hardworking make up for the lazy.
        int working = s.adults() == 0 ? 0 : s.employed() * Math.max(0, s.adults() - s.sick()) / s.adults();
        working = Math.max(0, working + (s.count(Trait.HARDWORKING) - s.count(Trait.LAZY)) / 2);
        int taxes = Math.max(0, s.tax().collect(working) - s.count(Trait.GREEDY) / 2);
        if (s.hasTech(Tech.CURRENCY)) {
            taxes += taxes / 4;
        }
        s.addWealth(taxes);
        if (s.hasTech(Tech.BANKING)) {
            s.addWealth(Math.min(20, s.wealth() / 100));
        }
        int idle = s.adults() - s.employed();
        int gathered = s.employed() + idle * 2;
        gathered += gathered * s.style().materialsBonusPercent() / 100;
        if (s.hasLaw(Law.BUILDING_PROGRAM)) {
            gathered += gathered / 2;
            s.addWealth(-2);
        }
        s.addMaterials(gathered);
    }

    /** Happiness moves 40% of the way towards what the current {@link Mood} factors add up to. */
    private static void updateHappiness(ServerLevel level, Settlement s) {
        int target = Mood.target(Mood.factors(level, s));
        s.decayRecentDeaths();
        s.setHappiness((int) Math.round(s.happiness() * 0.6 + target * 0.4));
    }

    /** Prosperous towns attract newcomers; miserable ones lose people. */
    private static void migrate(ServerLevel level, SettlementData data, Settlement s) {
        if (s.hasLaw(Law.CLOSED_BORDERS)) {
            return;
        }
        RandomSource random = level.getRandom();
        boolean prosperous = s.happiness() >= 60 && s.beds() > s.population() && s.food() >= s.population() * 5;
        if (prosperous && s.tier().ordinal() >= SettlementTier.VILLAGE.ordinal()
                && random.nextDouble() < CitiesConfig.IMMIGRATION_CHANCE.get() * (s.hasTech(Tech.MEDICINE) ? 1.5 : 1.0)) {
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
                RuinManager.decay(level, s);
                announce(level, s, Component.translatable("cities.announce.abandoned", s.name()).withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        s.setEmptyCensuses(0);
        if (s.abandoned()) {
            s.setAbandoned(false);
            s.record(day(level), "resettled", "");
            RuinManager.markForRebuilding(s);
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
        SettlementTier previous = s.tier();
        s.setTier(tier);
        s.setDecline(0);
        data.updateTerritory(s);
        DefenseManager.refitGuards(level, s);
        CitiesApi.post(new SettlementEvent.TierChanged(level, s, previous));
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
        s.addDeath();
        String name = ResidentManager.nameOf(villager);
        if (ResidentManager.diedOfOldAge(villager)) {
            s.record(day(level), "old_age", name + " (" + ResidentManager.age(level, villager) + ")");
        } else if (killer == null && !name.isEmpty()) {
            s.record(day(level), "died", name);
        }
        if (killer != null) {
            s.changeReputation(killer.getUUID(), REPUTATION_KILL_VILLAGER);
            s.record(day(level), "murder", killer.getGameProfile().getName());
            killer.displayClientMessage(Component.translatable("cities.reputation.murder", s.name())
                    .withStyle(ChatFormatting.RED), true);
        }
        data.setDirty();
    }
}
