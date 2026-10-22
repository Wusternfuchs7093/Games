package com.wusternfuchs.cities.trade;

import com.wusternfuchs.cities.settlement.ReputationRank;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Villagers of a settlement price their goods by the player's reputation there. */
public final class VillagerTrading {
    private VillagerTrading() {
    }

    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getTarget() instanceof Villager villager)
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(villager.level() instanceof ServerLevel level)
                || villager.isBaby() || villager.isTrading()) {
            return;
        }
        Settlement s = SettlementData.get(level).at(villager.blockPosition());
        if (s == null) {
            return;
        }
        ReputationRank rank = ReputationRank.of(s.reputation(player.getUUID()));
        if (rank == ReputationRank.ENEMY) {
            villager.setUnhappyCounter(40);
            villager.playSound(SoundEvents.VILLAGER_NO, 1.0f, villager.getVoicePitch());
            player.displayClientMessage(Component.translatable("cities.trade.refused", s.name())
                    .withStyle(ChatFormatting.RED), true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        int diff = priceDiff(rank);
        // Vanilla adds its own gossip-based discount on top and resets everything when trading ends.
        for (MerchantOffer offer : villager.getOffers()) {
            offer.resetSpecialPriceDiff();
            offer.addToSpecialPriceDiff(diff);
        }
    }

    private static int priceDiff(ReputationRank rank) {
        return switch (rank) {
            case ENEMY, UNWELCOME -> 2;
            case STRANGER -> 0;
            case FRIEND -> -1;
            case HONORED -> -2;
            case HERO -> -3;
        };
    }
}
