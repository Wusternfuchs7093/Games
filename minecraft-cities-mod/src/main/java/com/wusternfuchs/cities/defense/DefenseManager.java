package com.wusternfuchs.cities.defense;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.api.CitiesApi;
import com.wusternfuchs.cities.api.SettlementEvent;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.diplomacy.DiplomacyManager;
import com.wusternfuchs.cities.diplomacy.Relation;
import com.wusternfuchs.cities.entity.CitiesEntities;
import com.wusternfuchs.cities.entity.Guard;
import com.wusternfuchs.cities.governance.Law;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Guards, raids and everything else that keeps a settlement alive at night. */
public final class DefenseManager {
    private static final int[] GUARDS_PER_TIER = {0, 1, 2, 4, 6, 8, 12};
    private static final int GUARD_COST = 48;
    private static final int GUARD_WAGE = 1;
    private static final int SIEGE_COOLDOWN_DAYS = 3;
    private static final int SIEGE_DURATION_TICKS = 6000;
    private static final int ALERT_RADIUS = 32;
    private static final int REPUTATION_PER_RAIDER = 3;
    private static final int REPUTATION_HURT_VILLAGER = -2;
    private static final int MAX_REINFORCEMENTS = 3;

    private DefenseManager() {
    }

    // ------------------------------------------------------------------ garrison

    public static int wantedGuards(Settlement s) {
        int towers = s.count(BuildingType.WATCHTOWER) * (s.hasTech(Tech.ENGINEERING) ? 2 : 1);
        int wanted = GUARDS_PER_TIER[s.tier().ordinal()] + towers;
        return s.hasLaw(Law.CONSCRIPTION) ? wanted * 2 : wanted;
    }

    private static int patrolRadius(Settlement s) {
        return s.radiusChunks() * 16;
    }

    public static List<Guard> guardsOf(ServerLevel level, Settlement s) {
        int radius = (s.radiusChunks() + 2) * 16;
        return level.getEntitiesOfClass(Guard.class, new AABB(s.center()).inflate(radius, 64, radius),
                guard -> guard.isAlive() && !guard.isSoldier() && !guard.isCompanion()
                        && s.id().equals(guard.settlementId()));
    }

    /** Counts the guards, pays their wages and hires one more if the town needs and can afford it. */
    public static void updateGarrison(ServerLevel level, SettlementData data, Settlement s) {
        List<Guard> guards = guardsOf(level, s);
        s.setGuards(guards.size());
        s.addWealth(-guards.size() * GUARD_WAGE);
        if (CitiesConfig.GUARDS_ENABLED.get() && guards.size() < wantedGuards(s) && s.wealth() >= GUARD_COST
                && s.adults() > guards.size()) {
            if (hireGuard(level, s) != null) {
                s.addWealth(-GUARD_COST);
                s.setGuards(guards.size() + 1);
                s.record(SettlementManager.day(level), "guard_hired", "");
            }
        }
    }

    @Nullable
    public static Guard hireGuard(ServerLevel level, Settlement s) {
        Guard guard = CitiesEntities.GUARD.get().create(level);
        if (guard == null) {
            return null;
        }
        RandomSource random = level.getRandom();
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                s.center().offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
        guard.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        guard.enlist(s, s.center(), patrolRadius(s), gear(s));
        return level.addFreshEntity(guard) ? guard : null;
    }

    /** Better gear and a wider patrol after the town grows. */
    public static void refitGuards(ServerLevel level, Settlement s) {
        for (Guard guard : guardsOf(level, s)) {
            guard.enlist(s, s.center(), patrolRadius(s), gear(s));
        }
    }

    /** Metallurgy arms guards like those of a town one size bigger. */
    private static SettlementTier gear(Settlement s) {
        SettlementTier next = s.tier().next();
        return s.hasTech(Tech.METALLURGY) && next != null ? next : s.tier();
    }

    /** Guards nearby rush to defend a villager that was attacked. */
    public static void onVillagerAttacked(ServerLevel level, LivingEntity victim, @Nullable Entity attacker) {
        if (!(attacker instanceof LivingEntity enemy) || attacker instanceof Guard) {
            return;
        }
        if (enemy instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return;
            }
            Settlement s = SettlementData.get(level).at(victim.blockPosition());
            if (s != null) {
                s.changeReputation(player.getUUID(), REPUTATION_HURT_VILLAGER);
                SettlementData.get(level).setDirty();
            }
        }
        for (Guard guard : level.getEntitiesOfClass(Guard.class, victim.getBoundingBox().inflate(ALERT_RADIUS))) {
            if (guard.getTarget() == null) {
                guard.setTarget(enemy);
            }
        }
    }

    // ------------------------------------------------------------------ sieges

    /** Rolls for a night raid. {@code force} skips every condition except having somewhere to spawn. */
    public static boolean maybeStartSiege(ServerLevel level, SettlementData data, Settlement s, boolean force) {
        if (s.siege() != null) {
            return false;
        }
        long day = SettlementManager.day(level);
        if (!force) {
            // Hostile neighbours pay bandits to harass the town.
            int enemies = DiplomacyManager.withRelation(data, s, Relation.HOSTILE).size();
            double chance = CitiesConfig.SIEGE_CHANCE.get() * s.tier().ordinal() * (1 + enemies);
            if (s.hasLaw(Law.CURFEW)) {
                chance *= 0.5;
            }
            if (s.hasLaw(Law.CLOSED_BORDERS)) {
                chance *= 0.75;
            }
            if (!CitiesConfig.SIEGES_ENABLED.get() || s.abandoned()
                    || s.tier().ordinal() < SettlementTier.VILLAGE.ordinal() || level.isDay()
                    || day - s.lastSiegeDay() < SIEGE_COOLDOWN_DAYS || !playerInside(level, data, s)
                    || level.getRandom().nextDouble() >= chance) {
                return false;
            }
        }
        BlockPos camp = findCamp(level, s);
        if (camp == null) {
            return false;
        }

        List<UUID> attackers = new ArrayList<>();
        int size = 2 + s.tier().ordinal() * 2;
        for (int i = 0; i < size; i++) {
            Mob raider = i % 3 == 2
                    ? spawnRaider(level, EntityType.VINDICATOR, camp)
                    : spawnRaider(level, EntityType.PILLAGER, camp);
            if (raider != null) {
                attackers.add(raider.getUUID());
            }
        }
        if (s.tier().ordinal() >= SettlementTier.METROPOLIS.ordinal()) {
            Mob ravager = spawnRaider(level, EntityType.RAVAGER, camp);
            if (ravager != null) {
                attackers.add(ravager.getUUID());
            }
        }
        if (attackers.isEmpty()) {
            return false;
        }

        s.setSiege(new Siege(attackers, level.getGameTime()));
        s.setLastSiegeDay(day);
        s.record(day, "siege", String.valueOf(attackers.size()));
        data.setDirty();

        Component title = Component.translatable("cities.siege.title").withStyle(ChatFormatting.DARK_RED);
        Component subtitle = Component.translatable("cities.siege.subtitle", s.name(), attackers.size());
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.translatable("cities.siege.started", s.name(), attackers.size())
                    .withStyle(ChatFormatting.RED));
            if (data.at(player.blockPosition()) == s) {
                SettlementManager.showTitle(player, title, subtitle);
                level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE,
                        64.0f, 1.0f);
            }
        }
        callAllies(level, data, s);
        CitiesApi.post(new SettlementEvent.SiegeStarted(level, s));
        return true;
    }

    /** Allies with guards and money to spare each send one guard, who stays on in the town afterwards. */
    private static void callAllies(ServerLevel level, SettlementData data, Settlement s) {
        int sent = 0;
        for (Settlement ally : DiplomacyManager.withRelation(data, s, Relation.ALLIED)) {
            if (sent >= MAX_REINFORCEMENTS || ally.guards() < 2 || ally.wealth() < GUARD_COST) {
                continue;
            }
            if (hireGuard(level, s) != null) {
                ally.addWealth(-GUARD_COST);
                s.setGuards(s.guards() + 1);
                s.record(SettlementManager.day(level), "reinforcements", ally.name());
                Component message = Component.translatable("cities.siege.reinforcements", ally.name(), s.name())
                        .withStyle(ChatFormatting.AQUA);
                for (ServerPlayer player : level.players()) {
                    player.sendSystemMessage(message);
                }
                sent++;
            }
        }
    }

    /** Keeps raiders marching on the town and decides how the siege ends. */
    public static void tickSiege(ServerLevel level, SettlementData data, Settlement s) {
        Siege siege = s.siege();
        if (siege == null) {
            return;
        }
        BlockPos center = s.center();
        Iterator<UUID> it = siege.attackers().iterator();
        while (it.hasNext()) {
            Entity entity = level.getEntity(it.next());
            if (!(entity instanceof Mob raider) || !raider.isAlive()) {
                it.remove();
                continue;
            }
            if (raider.getTarget() == null && raider.getNavigation().isDone()) {
                raider.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.0);
            }
        }

        if (siege.attackers().isEmpty()) {
            endSiege(level, data, s, true);
        } else if (level.getGameTime() - siege.startTick() > SIEGE_DURATION_TICKS) {
            for (UUID id : siege.attackers()) {
                Entity raider = level.getEntity(id);
                if (raider != null) {
                    raider.discard();
                }
            }
            endSiege(level, data, s, false);
        }
    }

    private static void endSiege(ServerLevel level, SettlementData data, Settlement s, boolean victory) {
        s.setSiege(null);
        long day = SettlementManager.day(level);
        Component message;
        if (victory) {
            s.setHappiness(s.happiness() + 10);
            s.record(day, "siege_repelled", "");
            message = Component.translatable("cities.siege.repelled", s.name()).withStyle(ChatFormatting.GOLD);
        } else {
            s.addWealth(-s.wealth() / 4);
            s.addFood(-s.food() / 4);
            s.addMaterials(-s.materials() / 4);
            s.setHappiness(s.happiness() - 15);
            s.record(day, "plundered", "");
            message = Component.translatable("cities.siege.plundered", s.name()).withStyle(ChatFormatting.DARK_RED);
        }
        data.setDirty();
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }

    /** Thanks the player who killed a raider attacking a settlement. */
    public static void onRaiderDeath(ServerLevel level, LivingEntity dead, @Nullable ServerPlayer killer) {
        if (killer == null) {
            return;
        }
        SettlementData data = SettlementData.get(level);
        for (Settlement s : data.all()) {
            if (s.siege() != null && s.siege().attackers().contains(dead.getUUID())) {
                s.changeReputation(killer.getUUID(), REPUTATION_PER_RAIDER);
                data.setDirty();
                return;
            }
        }
    }

    private static boolean playerInside(ServerLevel level, SettlementData data, Settlement s) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && data.at(player.blockPosition()) == s) {
                return true;
            }
        }
        return false;
    }

    /** A dry, loaded spot just outside the territory where the raiders gather. */
    @Nullable
    private static BlockPos findCamp(ServerLevel level, Settlement s) {
        RandomSource random = level.getRandom();
        int distance = s.radiusChunks() * 16 + 12;
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            BlockPos column = s.center().offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
            if (!level.isLoaded(column)) {
                continue;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (level.getFluidState(top.below()).isEmpty() && level.getFluidState(top).isEmpty()) {
                return top;
            }
        }
        return null;
    }

    @Nullable
    private static Mob spawnRaider(ServerLevel level, EntityType<? extends Mob> type, BlockPos camp) {
        Mob raider = type.create(level);
        if (raider == null) {
            return null;
        }
        RandomSource random = level.getRandom();
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                camp.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
        raider.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        raider.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        raider.setPersistenceRequired();
        return level.addFreshEntity(raider) ? raider : null;
    }
}
