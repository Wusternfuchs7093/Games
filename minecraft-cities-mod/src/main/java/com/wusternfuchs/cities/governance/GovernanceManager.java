package com.wusternfuchs.cities.governance;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.diplomacy.DiplomacyManager;
import com.wusternfuchs.cities.diplomacy.Relation;
import com.wusternfuchs.cities.settlement.ReputationRank;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.SettlementTier;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mayors, elections, laws, taxes and festivals. */
public final class GovernanceManager {
    public static final int FESTIVAL_WEALTH = 32;
    public static final int FESTIVAL_FOOD_PER_VILLAGER = 3;
    private static final int FESTIVAL_CENSUSES = 3;
    private static final int FESTIVAL_HAPPINESS = 15;
    private static final int FESTIVAL_COOLDOWN_DAYS = 3;
    private static final int FESTIVAL_ROCKETS = 24;
    private static final int COUNCIL_SUPPORT = 40;
    public static final int WEALTH_PER_EMERALD = 9;

    /** Rockets still to be launched over each celebrating settlement. Not saved: a reload ends the show. */
    private static final Map<UUID, Integer> SHOWS = new HashMap<>();

    private GovernanceManager() {
    }

    public static boolean isMayor(Settlement s, Player player) {
        return player.getUUID().equals(s.mayor());
    }

    public static Component mayorName(Settlement s) {
        return s.mayor() == null
                ? Component.translatable("cities.mayor.council")
                : Component.literal(s.mayorName());
    }

    /** Happiness from laws, taxes and festivities. */
    public static int happinessModifier(Settlement s) {
        int modifier = s.tax().happiness();
        for (Law law : s.laws()) {
            modifier += law.happiness();
        }
        if (s.festivalCensuses() > 0) {
            modifier += FESTIVAL_HAPPINESS;
        }
        return modifier;
    }

    /** Runs once per census: impeachment, the council's policy, elections and festival countdown. */
    public static void govern(ServerLevel level, SettlementData data, Settlement s) {
        long day = SettlementManager.day(level);
        if (s.festivalCensuses() > 0) {
            s.setFestivalCensuses(s.festivalCensuses() - 1);
        }

        if (s.mayor() != null
                && ReputationRank.of(s.reputation(s.mayor())).ordinal() <= ReputationRank.UNWELCOME.ordinal()) {
            s.record(day, "impeached", s.mayorName());
            announce(level, Component.translatable("cities.mayor.impeached", s.mayorName(), s.name())
                    .withStyle(ChatFormatting.RED));
            s.setMayor(null, "");
        }

        if (s.mayor() == null) {
            councilPolicy(level, data, s, day);
        }

        int interval = CitiesConfig.ELECTION_INTERVAL_DAYS.get();
        if (s.tier().ordinal() >= SettlementTier.VILLAGE.ordinal() && day - s.lastElectionDay() >= interval) {
            holdElection(level, data, s);
        }
    }

    /** What the elders do when no player rules the town. */
    private static void councilPolicy(ServerLevel level, SettlementData data, Settlement s, long day) {
        boolean recentlyAttacked = s.siege() != null || day - s.lastSiegeDay() <= 1;
        boolean enemies = !DiplomacyManager.withRelation(data, s, Relation.HOSTILE).isEmpty();
        setLaw(level, s, Law.RATIONING, s.food() < s.population() * 5);
        setLaw(level, s, Law.CONSCRIPTION, enemies);
        setLaw(level, s, Law.CURFEW, recentlyAttacked);
        setLaw(level, s, Law.BUILDING_PROGRAM, s.project() != null && s.materials() > 400);
        setLaw(level, s, Law.CLOSED_BORDERS, false);

        if (s.happiness() < 40) {
            s.setTax(TaxRate.LOW);
        } else if (s.wealth() < 64 && s.happiness() >= 60) {
            s.setTax(TaxRate.HIGH);
        } else {
            s.setTax(TaxRate.NORMAL);
        }

        if (s.happiness() < 45 && s.food() >= s.population() * 25 && canAffordFestival(s)
                && day - s.lastFestivalDay() >= FESTIVAL_COOLDOWN_DAYS) {
            startFestival(level, s);
        }
    }

    public static boolean setLaw(ServerLevel level, Settlement s, Law law, boolean active) {
        boolean changed = active ? s.laws().add(law) : s.laws().remove(law);
        if (changed) {
            s.record(SettlementManager.day(level), active ? "law_enacted" : "law_repealed", law.id());
        }
        return changed;
    }

    // ------------------------------------------------------------------ elections

    public static void addCandidate(Settlement s, ServerPlayer player) {
        s.candidates().put(player.getUUID(), player.getGameProfile().getName());
    }

    /**
     * Every adult villager casts a vote. The council of elders always stands; players stand if they signed up or
     * already rule. Reputation wins votes, and the ruling side is judged by how happy the town is.
     */
    public static void holdElection(ServerLevel level, SettlementData data, Settlement s) {
        long day = SettlementManager.day(level);
        s.setLastElectionDay(day);
        Map<UUID, String> players = new HashMap<>(s.candidates());
        if (s.mayor() != null) {
            players.put(s.mayor(), s.mayorName());
        }
        s.candidates().clear();
        if (players.isEmpty()) {
            return;
        }

        List<UUID> ids = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        int mood = (s.happiness() - 50) / 2;
        ids.add(null);
        weights.add((double) Math.max(1, COUNCIL_SUPPORT + (s.mayor() == null ? mood : 0)));
        for (UUID id : players.keySet()) {
            ids.add(id);
            weights.add((double) Math.max(1, s.reputation(id) + (id.equals(s.mayor()) ? mood : 0)));
        }

        int[] votes = new int[ids.size()];
        RandomSource random = level.getRandom();
        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
        int voters = Math.max(1, s.adults());
        for (int v = 0; v < voters; v++) {
            double roll = random.nextDouble() * total;
            for (int i = 0; i < ids.size(); i++) {
                roll -= weights.get(i);
                if (roll <= 0 || i == ids.size() - 1) {
                    votes[i]++;
                    break;
                }
            }
        }
        int winner = 0;
        for (int i = 1; i < votes.length; i++) {
            if (votes[i] > votes[winner]) {
                winner = i;
            }
        }

        UUID winnerId = ids.get(winner);
        String winnerName = winnerId == null ? "" : players.get(winnerId);
        s.setMayor(winnerId, winnerName);
        Component name = mayorName(s);
        s.record(day, "elected", winnerId == null ? "-" : winnerName);
        data.setDirty();
        announce(level, Component.translatable("cities.election.result", s.name(), name, votes[winner], voters)
                .withStyle(ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------------ festivals

    public static boolean canAffordFestival(Settlement s) {
        return s.wealth() >= FESTIVAL_WEALTH && s.food() >= s.population() * FESTIVAL_FOOD_PER_VILLAGER;
    }

    public static void startFestival(ServerLevel level, Settlement s) {
        s.addWealth(-FESTIVAL_WEALTH);
        s.addFood(-s.population() * FESTIVAL_FOOD_PER_VILLAGER);
        s.setFestivalCensuses(FESTIVAL_CENSUSES);
        long day = SettlementManager.day(level);
        s.setLastFestivalDay(day);
        s.record(day, "festival", "");
        SHOWS.put(s.id(), FESTIVAL_ROCKETS);
        level.playSound(null, s.center(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0f, 1.2f);
        announce(level, Component.translatable("cities.festival.started", s.name()).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Fires the next few rockets of every running show. */
    public static void tickFestivals(ServerLevel level, SettlementData data) {
        Iterator<Map.Entry<UUID, Integer>> it = SHOWS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> show = it.next();
            Settlement s = data.byId(show.getKey());
            if (s == null) {
                // Belongs to another dimension, or the settlement is gone.
                continue;
            }
            if (show.getValue() <= 0 || !level.isPositionEntityTicking(s.center())) {
                it.remove();
                continue;
            }
            launchRocket(level, s.center(), level.getRandom());
            show.setValue(show.getValue() - 1);
        }
    }

    private static void launchRocket(ServerLevel level, BlockPos center, RandomSource random) {
        DyeColor[] dyes = DyeColor.values();
        IntList colors = IntList.of(dyes[random.nextInt(dyes.length)].getFireworkColor(),
                dyes[random.nextInt(dyes.length)].getFireworkColor());
        FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
        FireworkExplosion explosion = new FireworkExplosion(shapes[random.nextInt(shapes.length)], colors,
                IntList.of(), random.nextBoolean(), random.nextBoolean());
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + random.nextInt(2), List.of(explosion)));

        BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                center.offset(random.nextInt(13) - 6, 0, random.nextInt(13) - 6));
        level.addFreshEntity(new FireworkRocketEntity(level, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5,
                rocket));
    }

    private static void announce(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }
}
