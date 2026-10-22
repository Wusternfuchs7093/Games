package com.wusternfuchs.cities.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wusternfuchs.cities.CitiesMod;
import com.wusternfuchs.cities.network.RequestCityPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CitiesMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping OPEN_CITY = new KeyMapping("key.cities.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.cities");

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_CITY.consumeClick()) {
            if (Minecraft.getInstance().player != null) {
                PacketDistributor.sendToServer(new RequestCityPayload());
            }
        }
    }
}
