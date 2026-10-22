package com.wusternfuchs.cities;

import com.wusternfuchs.cities.command.CityCommands;
import com.wusternfuchs.cities.data.CultureNames;
import com.wusternfuchs.cities.data.CustomBlueprints;
import com.wusternfuchs.cities.defense.DefenseManager;
import com.wusternfuchs.cities.entity.Guard;
import com.wusternfuchs.cities.people.ResidentManager;
import com.wusternfuchs.cities.quest.QuestManager;
import com.wusternfuchs.cities.quest.TownServices;
import com.wusternfuchs.cities.settlement.BorderDisplay;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.trade.VillagerTrading;
import com.wusternfuchs.cities.war.WarManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
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
            BorderDisplay.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SettlementManager.forgetPlayer(event.getEntity());
        BorderDisplay.forget(event.getEntity().getUUID());
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
        } else if (event.getEntity() instanceof Guard guard) {
            WarManager.onSoldierDeath(level, guard, killer);
        }
        if (killer != null && event.getEntity() instanceof Enemy) {
            QuestManager.onMonsterKilled(level, event.getEntity(), killer);
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
        if (event.getEntity().isShiftKeyDown() && event.getHand() == InteractionHand.MAIN_HAND
                && event.getTarget() instanceof Villager villager && villager.level() instanceof ServerLevel level
                && event.getEntity() instanceof ServerPlayer player) {
            if (!QuestManager.tryFulfil(level, player, villager)) {
                ResidentManager.describe(level, player, villager);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        VillagerTrading.onInteract(event);
    }

    /** Shift-right-clicking a bell in a settlement shows its job board. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown() && event.getHand() == InteractionHand.MAIN_HAND
                && event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player
                && level.getBlockState(event.getPos()).is(Blocks.BELL)) {
            Settlement s = SettlementData.get(level).at(event.getPos());
            if (s != null) {
                QuestManager.showBoard(player::sendSystemMessage, player.getUUID(), s, SettlementManager.day(level));
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && !TownServices.canEdit(level, event.getPlayer(), event.getPos())) {
            event.getPlayer().displayClientMessage(Component.translatable("cities.house.protected"), true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Player player
                && !TownServices.canEdit(level, player, event.getPos())) {
            player.displayClientMessage(Component.translatable("cities.house.protected"), true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CustomBlueprints());
        event.addListener(new CultureNames());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CityCommands.register(event.getDispatcher());
    }
}
