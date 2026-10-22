package com.wusternfuchs.cities;

import com.wusternfuchs.cities.command.CityCommands;
import com.wusternfuchs.cities.defense.DefenseManager;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.trade.VillagerTrading;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class GameEvents {
    private GameEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SettlementManager.tick(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SettlementManager.trackPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SettlementManager.forgetPlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        if (event.getEntity() instanceof Villager villager) {
            SettlementManager.onVillagerDeath(level, villager, killer);
        } else if (event.getEntity() instanceof Raider raider) {
            DefenseManager.onRaiderDeath(level, raider, killer);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof AbstractVillager villager && villager.level() instanceof ServerLevel level) {
            DefenseManager.onVillagerAttacked(level, villager, event.getSource().getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        VillagerTrading.onInteract(event);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CityCommands.register(event.getDispatcher());
    }
}
