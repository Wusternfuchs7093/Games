package com.wusternfuchs.cities.client;

import com.wusternfuchs.cities.network.BoardPayload;
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

    public static void showBoard(BoardPayload board) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof JobBoardScreen screen) {
            screen.update(board);
        } else {
            minecraft.setScreen(new JobBoardScreen(board));
        }
    }
}
