package com.wusternfuchs.cities.settlement;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Shows the edge of a settlement's territory to players who asked for it, as a line of green sparkles. */
public final class BorderDisplay {
    private static final Set<UUID> VIEWERS = new HashSet<>();
    private static final int VIEW_CHUNKS = 4;
    private static final int[][] SIDES = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    private BorderDisplay() {
    }

    /** Turns the display on or off; returns the new state. */
    public static boolean toggle(ServerPlayer player) {
        if (VIEWERS.remove(player.getUUID())) {
            return false;
        }
        VIEWERS.add(player.getUUID());
        return true;
    }

    public static void forget(UUID player) {
        VIEWERS.remove(player);
    }

    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 0 || !VIEWERS.contains(player.getUUID())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        SettlementData data = SettlementData.get(level);
        Settlement s = data.at(player.blockPosition());
        if (s == null) {
            s = data.nearest(player.blockPosition(), 256);
        }
        if (s == null) {
            return;
        }
        ChunkPos here = player.chunkPosition();
        double y = player.getY() + 1.0;
        for (long key : s.chunks()) {
            ChunkPos chunk = new ChunkPos(key);
            if (Math.abs(chunk.x - here.x) > VIEW_CHUNKS || Math.abs(chunk.z - here.z) > VIEW_CHUNKS) {
                continue;
            }
            for (int[] side : SIDES) {
                if (s.chunks().contains(ChunkPos.asLong(chunk.x + side[0], chunk.z + side[1]))) {
                    continue;
                }
                for (int i = 0; i < 16; i += 2) {
                    double x;
                    double z;
                    if (side[0] == 0) {
                        x = chunk.getMinBlockX() + i + 0.5;
                        z = side[1] < 0 ? chunk.getMinBlockZ() : chunk.getMaxBlockZ() + 1;
                    } else {
                        x = side[0] < 0 ? chunk.getMinBlockX() : chunk.getMaxBlockX() + 1;
                        z = chunk.getMinBlockZ() + i + 0.5;
                    }
                    level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, x, y, z, 1, 0.0, 0.4, 0.0, 0.0);
                }
            }
        }
    }
}
