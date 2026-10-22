package com.wusternfuchs.cities.war;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.diplomacy.DiplomacyManager;
import com.wusternfuchs.cities.diplomacy.Relation;
import com.wusternfuchs.cities.entity.CitiesEntities;
import com.wusternfuchs.cities.entity.Guard;
import com.wusternfuchs.cities.people.Trait;
import com.wusternfuchs.cities.research.Tech;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.SettlementTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Wars between settlements. The attacker sends an army every few censuses; if the defender is loaded the army is
 * made of real soldiers marching on its center, otherwise the battle is decided by strength and luck. Three won
 * battles make the loser surrender: a defeated defender becomes a vassal paying tribute.
 */
public final class WarManager {
    public static final int WAR_RANGE = 512;
    private static final int BATTLE_INTERVAL_TICKS = 6000;
    private static final int BATTLE_DURATION_TICKS = 6000;
    private static final int SCORE_PER_BATTLE = 35;
    private static final int SOLDIER_COST = 16;
    private static final int MAX_SOLDIERS = 10;
    private static final int TRUCE_DAYS = 10;
    private static final int REPUTATION_PER_SOLDIER = 3;
    private static final int WAR_WEARINESS = -5;

    private WarManager() {
    }

    // ------------------------------------------------------------------ queries

    public static boolean atWar(SettlementData data, UUID a, UUID b) {
        for (War war : data.wars()) {
            if (war.between(a, b)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static War warBetween(SettlementData data, UUID a, UUID b) {
        for (War war : data.wars()) {
            if (war.between(a, b)) {
                return war;
            }
        }
        return null;
    }

    public static List<War> warsOf(SettlementData data, Settlement s) {
        List<War> result = new ArrayList<>();
        for (War war : data.wars()) {
            if (war.involves(s.id())) {
                result.add(war);
            }
        }
        return result;
    }

    public static int vassals(SettlementData data, Settlement s) {
        int count = 0;
        for (Settlement other : data.all()) {
            if (s.id().equals(other.overlord())) {
                count++;
            }
        }
        return count;
    }

    /** Rough military might: guards, golems, size, walls and the right technologies. */
    public static int strength(Settlement s, boolean defending) {
        int strength = s.guards() * 3 + s.golems() * 2 + s.tier().ordinal() * 5
                + s.count(Trait.BRAVE);
        if (s.hasTech(Tech.METALLURGY)) {
            strength += 10;
        }
        if (defending) {
            strength += s.count(BuildingType.WALL) * 10 + s.count(BuildingType.WATCHTOWER) * 3;
            if (s.hasTech(Tech.ENGINEERING)) {
                strength += 10;
            }
        }
        return Math.max(1, strength);
    }

    /** Whether {@code attacker} may start a war against {@code defender} right now. */
    public static boolean canDeclare(ServerLevel level, SettlementData data, Settlement attacker, Settlement defender) {
        if (attacker == defender || atWar(data, attacker.id(), defender.id())
                || attacker.truceUntil(defender.id()) >= SettlementManager.day(level)
                || attacker.id().equals(defender.overlord())
                || attacker.center().distSqr(defender.center()) > (double) WAR_RANGE * WAR_RANGE) {
            return false;
        }
        // Only a player-led vassal dares to rise against its overlord.
        return !defender.id().equals(attacker.overlord()) || attacker.mayor() != null;
    }

    // ------------------------------------------------------------------ census

    /** Tribute, the council's war and peace decisions, and scheduling the attacker's next battle. */
    public static void update(ServerLevel level, SettlementData data, Settlement s) {
        payTribute(data, s);
        if (!warsOf(data, s).isEmpty()) {
            s.setHappiness(s.happiness() + WAR_WEARINESS);
        }
        if (s.mayor() == null && CitiesConfig.WARS_ENABLED.get()) {
            councilDecides(level, data, s);
        }
    }

    private static void payTribute(SettlementData data, Settlement s) {
        Settlement lord = data.byId(s.overlord());
        if (s.overlord() != null && lord == null) {
            s.setOverlord(null); // the overlord is gone
            return;
        }
        if (lord != null) {
            int tribute = Math.min(50, s.wealth() / 10);
            s.addWealth(-tribute);
            lord.addWealth(tribute);
        }
    }

    private static void councilDecides(ServerLevel level, SettlementData data, Settlement s) {
        RandomSource random = level.getRandom();
        long day = SettlementManager.day(level);
        for (War war : warsOf(data, s)) {
            boolean attacker = war.attacker().equals(s.id());
            boolean losing = attacker ? war.score() < 0 : war.score() > 0;
            boolean tired = day - war.startDay() >= 3;
            if ((losing || tired) && random.nextDouble() < 0.1) {
                makePeace(level, data, war, false);
                return;
            }
        }
        if (s.overlord() != null || s.tier().ordinal() < SettlementTier.TOWNSHIP.ordinal() || !warsOf(data, s).isEmpty()) {
            return;
        }
        for (Settlement enemy : DiplomacyManager.withRelation(data, s, Relation.HOSTILE)) {
            if (canDeclare(level, data, s, enemy) && strength(s, false) > strength(enemy, true) * 1.1
                    && random.nextDouble() < 0.05) {
                declare(level, data, s, enemy);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ war and peace

    public static War declare(ServerLevel level, SettlementData data, Settlement attacker, Settlement defender) {
        War war = new War(attacker.id(), defender.id(), SettlementManager.day(level),
                level.getGameTime() + BATTLE_INTERVAL_TICKS / 2);
        data.wars().add(war);
        attacker.setRelation(defender.id(), -100);
        defender.setRelation(attacker.id(), -100);
        long day = SettlementManager.day(level);
        attacker.record(day, "war_declared", defender.name());
        defender.record(day, "war_declared_on", attacker.name());
        data.setDirty();
        broadcast(level, Component.translatable("cities.war.declared", attacker.name(), defender.name())
                .withStyle(ChatFormatting.DARK_RED));
        return war;
    }

    /** Ends a war. With {@code surrender} the losing side (by score) pays: vassalage or reparations. */
    public static void makePeace(ServerLevel level, SettlementData data, War war, boolean surrender) {
        Settlement attacker = data.byId(war.attacker());
        Settlement defender = data.byId(war.defender());
        disband(level, war);
        data.wars().remove(war);
        data.setDirty();
        if (attacker == null || defender == null) {
            return;
        }
        long day = SettlementManager.day(level);
        attacker.setTruce(defender.id(), day + TRUCE_DAYS);
        defender.setTruce(attacker.id(), day + TRUCE_DAYS);
        attacker.setRelation(defender.id(), -30);
        defender.setRelation(attacker.id(), -30);

        if (surrender && war.score() >= 100) {
            if (defender.id().equals(attacker.overlord())) {
                attacker.setOverlord(null);
                attacker.record(day, "independence", defender.name());
                defender.record(day, "lost_vassal", attacker.name());
                broadcast(level, Component.translatable("cities.war.independence", attacker.name(), defender.name())
                        .withStyle(ChatFormatting.GOLD));
            } else {
                defender.setOverlord(attacker.id());
                attacker.setRelation(defender.id(), 60);
                defender.setRelation(attacker.id(), 60);
                attacker.record(day, "conquered", defender.name());
                defender.record(day, "surrendered", attacker.name());
                broadcast(level, Component.translatable("cities.war.vassal", defender.name(), attacker.name())
                        .withStyle(ChatFormatting.GOLD));
            }
        } else if (surrender && war.score() <= -100) {
            int reparations = attacker.wealth() / 4;
            attacker.addWealth(-reparations);
            defender.addWealth(reparations);
            attacker.record(day, "war_lost", defender.name());
            defender.record(day, "war_won", attacker.name());
            broadcast(level, Component.translatable("cities.war.repelled", defender.name(), attacker.name(), reparations)
                    .withStyle(ChatFormatting.GOLD));
        } else {
            attacker.record(day, "peace", defender.name());
            defender.record(day, "peace", attacker.name());
            broadcast(level, Component.translatable("cities.war.peace", attacker.name(), defender.name())
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    // ------------------------------------------------------------------ battles

    /** Starts battles that are due and follows the ones in progress. Called a few times a second. */
    public static void tick(ServerLevel level, SettlementData data) {
        if (data.wars().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (War war : new ArrayList<>(data.wars())) {
            Settlement attacker = data.byId(war.attacker());
            Settlement defender = data.byId(war.defender());
            if (attacker == null || defender == null) {
                data.wars().remove(war);
                data.setDirty();
                continue;
            }
            if (war.battleInProgress()) {
                followBattle(level, data, war, attacker, defender);
            } else if (now >= war.nextBattleTick()) {
                launchBattle(level, data, war, attacker, defender);
            }
        }
    }

    public static void launchBattle(ServerLevel level, SettlementData data, War war, Settlement attacker,
                                    Settlement defender) {
        war.setNextBattleTick(level.getGameTime() + BATTLE_INTERVAL_TICKS);
        data.setDirty();
        if (!level.isPositionEntityTicking(defender.center())) {
            double odds = (double) strength(attacker, false) / (strength(attacker, false) + strength(defender, true));
            resolveBattle(level, data, war, attacker, defender, level.getRandom().nextDouble() < odds);
            return;
        }

        int size = Math.min(MAX_SOLDIERS, 2 + attacker.guards());
        size = Math.max(2, Math.min(size, attacker.wealth() / SOLDIER_COST));
        BlockPos camp = campToward(level, defender, attacker);
        List<UUID> army = new ArrayList<>();
        RandomSource random = level.getRandom();
        SettlementTier gear = attacker.hasTech(Tech.METALLURGY) && attacker.tier().next() != null
                ? attacker.tier().next() : attacker.tier();
        for (int i = 0; i < size; i++) {
            Guard soldier = CitiesEntities.GUARD.get().create(level);
            if (soldier == null) {
                continue;
            }
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    camp.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
            soldier.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            soldier.enlistSoldier(attacker, defender.center(), gear);
            if (level.addFreshEntity(soldier)) {
                army.add(soldier.getUUID());
            }
        }
        attacker.addWealth(-army.size() * SOLDIER_COST);
        war.startBattle(army, level.getGameTime());

        Component title = Component.translatable("cities.war.battle_title").withStyle(ChatFormatting.DARK_RED);
        Component subtitle = Component.translatable("cities.war.battle_subtitle", attacker.name(), army.size());
        for (ServerPlayer player : level.players()) {
            if (data.at(player.blockPosition()) == defender) {
                SettlementManager.showTitle(player, title, subtitle);
                level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE,
                        64.0f, 0.8f);
            }
        }
        broadcast(level, Component.translatable("cities.war.battle_started", attacker.name(), defender.name(),
                army.size()).withStyle(ChatFormatting.RED));
    }

    private static void followBattle(ServerLevel level, SettlementData data, War war, Settlement attacker,
                                     Settlement defender) {
        long elapsed = level.getGameTime() - war.battleStart();
        if (!level.isPositionEntityTicking(defender.center())) {
            // Nobody is watching any more: once the time is up, let strength decide.
            if (elapsed > BATTLE_DURATION_TICKS) {
                disband(level, war);
                double odds = (double) strength(attacker, false) / (strength(attacker, false) + strength(defender, true));
                resolveBattle(level, data, war, attacker, defender, level.getRandom().nextDouble() < odds);
            }
            return;
        }
        Iterator<UUID> it = war.soldiers().iterator();
        while (it.hasNext()) {
            Entity soldier = level.getEntity(it.next());
            if (!(soldier instanceof Guard guard) || !guard.isAlive()) {
                it.remove();
            }
        }
        if (war.soldiers().isEmpty()) {
            resolveBattle(level, data, war, attacker, defender, false);
        } else if (elapsed > BATTLE_DURATION_TICKS) {
            boolean attackerWins = war.soldiers().size() * 2 >= war.battleSize();
            disband(level, war);
            resolveBattle(level, data, war, attacker, defender, attackerWins);
        }
    }

    private static void resolveBattle(ServerLevel level, SettlementData data, War war, Settlement attacker,
                                      Settlement defender, boolean attackerWins) {
        war.endBattle();
        long day = SettlementManager.day(level);
        if (attackerWins) {
            war.addScore(SCORE_PER_BATTLE);
            int loot = defender.wealth() / 5;
            defender.addWealth(-loot);
            attacker.addWealth(loot);
            defender.setHappiness(defender.happiness() - 10);
            defender.record(day, "battle_lost", attacker.name());
            attacker.record(day, "battle_won", defender.name());
            broadcast(level, Component.translatable("cities.war.battle_won", attacker.name(), defender.name(), loot)
                    .withStyle(ChatFormatting.RED));
        } else {
            war.addScore(-SCORE_PER_BATTLE);
            attacker.addWealth(-attacker.wealth() / 10);
            defender.setHappiness(defender.happiness() + 5);
            defender.record(day, "battle_won", attacker.name());
            attacker.record(day, "battle_lost", defender.name());
            broadcast(level, Component.translatable("cities.war.battle_held", defender.name(), attacker.name())
                    .withStyle(ChatFormatting.GOLD));
        }
        data.setDirty();
        if (Math.abs(war.score()) >= 100) {
            makePeace(level, data, war, true);
        }
    }

    /** Soldiers still on the field go home. */
    private static void disband(ServerLevel level, War war) {
        for (UUID id : war.soldiers()) {
            Entity soldier = level.getEntity(id);
            if (soldier != null) {
                soldier.discard();
            }
        }
        war.soldiers().clear();
    }

    public static void onSoldierDeath(ServerLevel level, Guard soldier, @Nullable ServerPlayer killer) {
        if (killer == null || !soldier.isSoldier()) {
            return;
        }
        SettlementData data = SettlementData.get(level);
        for (War war : data.wars()) {
            if (war.soldiers().contains(soldier.getUUID())) {
                Settlement defender = data.byId(war.defender());
                if (defender != null) {
                    defender.changeReputation(killer.getUUID(), REPUTATION_PER_SOLDIER);
                    data.setDirty();
                }
                return;
            }
        }
    }

    private static BlockPos campToward(ServerLevel level, Settlement defender, Settlement attacker) {
        double dx = attacker.center().getX() - defender.center().getX();
        double dz = attacker.center().getZ() - defender.center().getZ();
        double length = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        int distance = defender.tier().radiusChunks() * 16 + 8;
        BlockPos column = defender.center().offset((int) (dx / length * distance), 0, (int) (dz / length * distance));
        if (!level.isLoaded(column)) {
            column = defender.center().offset((int) (dx / length * 24), 0, (int) (dz / length * 24));
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }
}
