package com.wusternfuchs.cities.network;

import com.wusternfuchs.cities.client.ClientHooks;
import com.wusternfuchs.cities.quest.QuestManager;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CitiesNetwork {
    private static final String VERSION = "2";
    private static final double NEARBY = 256;

    private CitiesNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(CitiesNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(RequestCityPayload.TYPE, RequestCityPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        openFor(player);
                    }
                }));
        registrar.playToServer(RequestBoardPayload.TYPE, RequestBoardPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        openBoard(player, player.blockPosition());
                    }
                }));
        // The handlers only ever run on the client, so client classes are only touched there.
        registrar.playToClient(CityViewPayload.TYPE, CityViewPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientHooks.showCity(payload)));
        registrar.playToClient(BoardPayload.TYPE, BoardPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientHooks.showBoard(payload)));
    }

    /** Sends the player a fresh view of the city they are in; their screen opens or refreshes. */
    public static void openFor(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, CityView.build(player));
    }

    /** Opens (or refreshes) the job board of the settlement at {@code pos}, or the nearest one. */
    public static void openBoard(ServerPlayer player, BlockPos pos) {
        SettlementData data = SettlementData.get(player.serverLevel());
        Settlement s = data.at(pos);
        if (s == null) {
            s = data.nearest(pos, NEARBY);
        }
        PacketDistributor.sendToPlayer(player, QuestManager.boardPayload(player, s));
    }
}
