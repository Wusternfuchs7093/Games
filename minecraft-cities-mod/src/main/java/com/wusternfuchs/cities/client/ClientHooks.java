package com.wusternfuchs.cities.client;

import com.wusternfuchs.cities.network.CityViewPayload;
import net.minecraft.client.Minecraft;

/** Client-only entry points called from network handlers. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void showCity(CityViewPayload view) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CityScreen screen) {
            screen.update(view);
        } else {
            minecraft.setScreen(new CityScreen(view));
        }
    }
}
