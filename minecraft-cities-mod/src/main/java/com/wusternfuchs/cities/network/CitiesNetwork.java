package com.wusternfuchs.cities.network;

import com.wusternfuchs.cities.client.ClientHooks;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CitiesNetwork {
    private static final String VERSION = "1";

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
        // The handler only ever runs on the client, so client classes are only touched there.
        registrar.playToClient(CityViewPayload.TYPE, CityViewPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientHooks.showCity(payload)));
    }

    /** Sends the player a fresh view of the city they are in; their screen opens or refreshes. */
    public static void openFor(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, CityView.build(player));
    }
}
