package com.wusternfuchs.cities.disaster;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.building.Building;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.governance.Law;
import com.wusternfuchs.cities.research.Tech;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.SettlementTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Fires, epidemics and droughts, and how a settlement copes with them. */
public final class DisasterManager {
    private static final int FIRE_SPARKS = 6;
    private static final int FIRE_MEMORY_CENSUSES = 3;
    private static final int FIRE_ABSTRACT_TICKS = 6000;
    private static final int FIRE_TIMEOUT_TICKS = 12000;
    private static final int FIREFIGHTING_REACH = 3;
    private static final int EPIDEMIC_CENSUSES = 8;
    private static final int DROUGHT_CENSUSES = 10;
    private static final int MAX_DEATHS_PER_CENSUS = 2;

    public enum Kind {
        FIRE, EPIDEMIC, DROUGHT
    }

    private DisasterManager() {
    }

    // ------------------------------------------------------------------ census

    public static void update(ServerLevel level, SettlementData data, Settlement s) {
        s.setRecentFire(s.recentFire() - 1);
        updateEpidemic(level, data, s);
        updateDrought(level, s);
        if (!CitiesConfig.DISASTERS_ENABLED.get() || s.abandoned()
                || s.tier().ordinal() < SettlementTier.VILLAGE.ordinal()) {
            return;
        }
        RandomSource random = level.getRandom();
        if (random.nextDouble() < fireChance(level, s)) {
            start(level, data, s, Kind.FIRE);
        } else if (random.nextDouble() < epidemicChance(s)) {
            start(level, data, s, Kind.EPIDEMIC);
        } else if (s.drought() == 0 && !level.isRaining() && random.nextDouble() < CitiesConfig.DROUGHT_CHANCE.get()) {
            start(level, data, s, Kind.DROUGHT);
        }
    }

    /** Starts a disaster now. Returns false if it cannot happen (nothing to burn, already going on...). */
    public static boolean start(ServerLevel level, SettlementData data, Settlement s, Kind kind) {
        return switch (kind) {
            case FIRE -> startFire(level, data, s);
            case EPIDEMIC -> startEpidemic(level, s);
            case DROUGHT -> startDrought(level, s);
        };
    }

    // ------------------------------------------------------------------ fire

    private static List<Building> wooden(Settlement s) {
        List<Building> result = new ArrayList<>();
        for (Building building : s.buildings()) {
            if (building.wooden() && building.type().upgradable()) {
                result.add(building);
            }
        }
        return result;
    }

    private static double fireChance(ServerLevel level, Settlement s) {
        if (s.fire() != null) {
            return 0;
        }
        int wood = Math.min(10, wooden(s).size());
        double chance = CitiesConfig.FIRE_CHANCE.get() * wood / (1.0 + s.count(BuildingType.WELL));
        return level.isThundering() ? chance * 3 : chance;
    }

    private static boolean startFire(ServerLevel level, SettlementData data, Settlement s) {
        List<Building> candidates = wooden(s);
        if (candidates.isEmpty() || s.fire() != null) {
            return false;
        }
        RandomSource random = level.getRandom();
        Building building = candidates.get(random.nextInt(candidates.size()));
        long day = SettlementManager.day(level);
        s.record(day, "fire", building.type().id());
        s.setRecentFire(FIRE_MEMORY_CENSUSES);
        data.setDirty();

        BoundingBox box = building.bounds(building.blueprint(s.style()));
        if (level.isPositionEntityTicking(s.center()) && level.isLoaded(box.getCenter())) {
            int lit = 0;
            for (int attempt = 0; attempt < FIRE_SPARKS * 4 && lit < FIRE_SPARKS; attempt++) {
                BlockPos pos = new BlockPos(
                        box.minX() + random.nextInt(box.getXSpan()),
                        box.minY() + random.nextInt(box.getYSpan()),
                        box.minZ() + random.nextInt(box.getZSpan()));
                BlockState state = level.getBlockState(pos);
                BlockPos above = pos.above();
                if (state.isFlammable(level, pos, Direction.UP) && level.getBlockState(above).isAir()) {
                    level.setBlock(above, BaseFireBlock.getState(level, above), Block.UPDATE_ALL);
                    lit++;
                }
            }
            if (lit > 0) {
                s.setFire(building, level.getGameTime());
                announce(level, data, s, Component.translatable("cities.disaster.fire", s.name(),
                        building.type().displayName()).withStyle(ChatFormatting.RED), true);
                return true;
            }
        }
        // Nobody saw it: the town just loses some materials and repairs the building.
        s.addMaterials(-s.materials() / 10);
        markDamaged(s, building);
        announce(level, data, s, Component.translatable("cities.disaster.fire_far", s.name()).withStyle(ChatFormatting.RED), false);
        return true;
    }

    /** Townsfolk put the fire out bucket by bucket: more wells and guards mean more hands. */
    public static void tickFires(ServerLevel level, SettlementData data) {
        long now = level.getGameTime();
        for (Settlement s : data.all()) {
            Building building = s.fire();
            if (building == null) {
                continue;
            }
            long burning = now - s.fireStart();
            if (!level.isPositionEntityTicking(s.center())) {
                if (burning > FIRE_ABSTRACT_TICKS) {
                    endFire(level, data, s, building);
                }
                continue;
            }
            BoundingBox box = building.bounds(building.blueprint(s.style())).inflatedBy(FIREFIGHTING_REACH);
            int crew = 1 + s.count(BuildingType.WELL) + s.guards() / 2;
            int remaining = 0;
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (!level.getBlockState(pos).is(BlockTags.FIRE)) {
                    continue;
                }
                if (crew > 0) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.0f);
                    crew--;
                } else {
                    remaining++;
                }
            }
            if (remaining == 0 || burning > FIRE_TIMEOUT_TICKS) {
                endFire(level, data, s, building);
            }
        }
    }

    private static void endFire(ServerLevel level, SettlementData data, Settlement s, Building building) {
        s.setFire(null, 0);
        markDamaged(s, building);
        s.record(SettlementManager.day(level), "fire_out", building.type().id());
        data.setDirty();
        announce(level, data, s, Component.translatable("cities.disaster.fire_out", s.name())
                .withStyle(ChatFormatting.GREEN), false);
    }

    private static void markDamaged(Settlement s, Building building) {
        for (Building known : s.damaged()) {
            if (known.origin().equals(building.origin())) {
                return;
            }
        }
        s.damaged().add(building);
    }

    // ------------------------------------------------------------------ epidemic

    private static double epidemicChance(Settlement s) {
        if (s.epidemic() > 0) {
            return 0;
        }
        double chance = CitiesConfig.DISEASE_CHANCE.get();
        if (s.population() > s.beds()) chance *= 2;
        if (s.count(BuildingType.WELL) == 0) chance *= 2;
        if (s.food() < s.population() * 5) chance *= 1.5;
        if (s.hasTech(Tech.MEDICINE)) chance *= 0.5;
        if (s.count(BuildingType.HOSPITAL) > 0) chance *= 0.5;
        if (s.hasLaw(Law.CLOSED_BORDERS)) chance *= 0.5;
        return chance;
    }

    private static boolean startEpidemic(ServerLevel level, Settlement s) {
        if (s.epidemic() > 0 || s.population() == 0) {
            return false;
        }
        s.setEpidemic(EPIDEMIC_CENSUSES);
        s.setSick(Math.max(1, s.population() / 5));
        s.record(SettlementManager.day(level), "epidemic", "");
        announce(level, SettlementData.get(level), s, Component.translatable("cities.disaster.epidemic", s.name())
                .withStyle(ChatFormatting.DARK_GREEN), true);
        return true;
    }

    private static void updateEpidemic(ServerLevel level, SettlementData data, Settlement s) {
        if (s.epidemic() <= 0) {
            return;
        }
        int hospitals = s.count(BuildingType.HOSPITAL);
        int cured = 1 + hospitals * 2 + s.clerics() / 2 + (s.hasTech(Tech.MEDICINE) ? 1 : 0);
        int spread = s.sick() / (s.hasLaw(Law.CLOSED_BORDERS) ? 6 : 3);
        if (hospitals > 0) {
            spread /= 2;
        }
        s.setSick(Math.min(s.population(), s.sick() + spread - cured));
        s.setEpidemic(s.epidemic() - 1);

        if (level.isPositionEntityTicking(s.center()) && s.sick() > 0) {
            afflictVillagers(level, data, s);
        }
        if (s.sick() == 0 || s.epidemic() == 0) {
            s.setEpidemic(0);
            s.setSick(0);
            s.record(SettlementManager.day(level), "epidemic_over", "");
            announce(level, data, s, Component.translatable("cities.disaster.epidemic_over", s.name())
                    .withStyle(ChatFormatting.GREEN), false);
        }
    }

    /** The sick look and move feebly; a few of them may not make it. */
    private static void afflictVillagers(ServerLevel level, SettlementData data, Settlement s) {
        int radius = (s.radiusChunks() + 1) * 16;
        List<Villager> villagers = new ArrayList<>(level.getEntitiesOfClass(Villager.class,
                new AABB(s.center()).inflate(radius, 48, radius),
                v -> v.isAlive() && data.at(v.blockPosition()) == s));
        RandomSource random = level.getRandom();
        int duration = CitiesConfig.CENSUS_INTERVAL.get() + 100;
        double deathChance = s.hasTech(Tech.MEDICINE) ? 0.02 : 0.05;
        int deaths = 0;
        int afflicted = Math.min(s.sick(), villagers.size());
        for (int i = 0; i < afflicted; i++) {
            Villager villager = villagers.remove(random.nextInt(villagers.size()));
            villager.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0));
            villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0));
            if (deaths < MAX_DEATHS_PER_CENSUS && random.nextDouble() < deathChance) {
                villager.hurt(level.damageSources().magic(), Float.MAX_VALUE);
                deaths++;
            }
        }
    }

    // ------------------------------------------------------------------ drought

    private static boolean startDrought(ServerLevel level, Settlement s) {
        if (s.drought() > 0) {
            return false;
        }
        s.setDrought(DROUGHT_CENSUSES);
        s.record(SettlementManager.day(level), "drought", "");
        announce(level, SettlementData.get(level), s, Component.translatable("cities.disaster.drought", s.name())
                .withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    private static void updateDrought(ServerLevel level, Settlement s) {
        if (s.drought() <= 0) {
            return;
        }
        s.setDrought(level.isRaining() ? 0 : s.drought() - 1);
        if (s.drought() == 0) {
            s.record(SettlementManager.day(level), "drought_over", "");
        }
    }

    /** Food lost to drought: half the harvest, or a quarter with a well to water the fields. */
    public static int droughtLoss(Settlement s, int produced) {
        if (s.drought() <= 0) {
            return 0;
        }
        return s.count(BuildingType.WELL) > 0 ? produced / 4 : produced / 2;
    }

    // ------------------------------------------------------------------ helpers

    private static void announce(ServerLevel level, SettlementData data, Settlement s, Component message,
                                 boolean title) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
            if (title && data.at(player.blockPosition()) == s) {
                SettlementManager.showTitle(player, Component.literal(s.name()).withStyle(ChatFormatting.RED), message);
            }
        }
    }

    @Nullable
    public static Kind kindById(String id) {
        for (Kind kind : Kind.values()) {
            if (kind.name().equalsIgnoreCase(id)) {
                return kind;
            }
        }
        return null;
    }
}
