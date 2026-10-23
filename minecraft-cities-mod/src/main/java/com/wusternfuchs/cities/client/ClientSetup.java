package com.wusternfuchs.cities.client;

import com.wusternfuchs.cities.CitiesMod;
import com.wusternfuchs.cities.entity.CitiesEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = CitiesMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CitiesEntities.GUARD.get(), GuardRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ClientEvents.OPEN_CITY);
        event.register(ClientEvents.OPEN_BOARD);
        event.register(ClientEvents.DONATE);
    }
}
