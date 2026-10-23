package com.wusternfuchs.cities.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wusternfuchs.cities.CitiesMod;
import com.wusternfuchs.cities.network.RequestBoardPayload;
import com.wusternfuchs.cities.network.RequestCityPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
    public static final KeyMapping OPEN_BOARD = new KeyMapping("key.cities.board", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, "key.categories.cities");
    /** Gives the town what the player holds; with Shift, every stack of that item in the inventory. */
    public static final KeyMapping DONATE = new KeyMapping("key.cities.donate", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.cities");

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_CITY.consumeClick()) {
            if (minecraft.player != null) {
                PacketDistributor.sendToServer(new RequestCityPayload());
            }
        }
        while (OPEN_BOARD.consumeClick()) {
            if (minecraft.player != null) {
                PacketDistributor.sendToServer(new RequestBoardPayload());
            }
        }
        while (DONATE.consumeClick()) {
            if (minecraft.player != null) {
                minecraft.player.connection.sendCommand(Screen.hasShiftDown() ? "city donate all" : "city donate");
            }
        }
    }
}
