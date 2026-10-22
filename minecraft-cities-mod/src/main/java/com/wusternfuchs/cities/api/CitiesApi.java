package com.wusternfuchs.cities.api;

import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

/**
 * Read access to settlements for other mods. Call only on the server thread. The returned settlements are live
 * objects: read them freely, but change them only if you know what the simulation expects.
 */
public final class CitiesApi {
    private CitiesApi() {
    }

    /** The settlement whose territory contains {@code pos}, if any. */
    @Nullable
    public static Settlement settlementAt(ServerLevel level, BlockPos pos) {
        return SettlementData.get(level).at(pos);
    }

    @Nullable
    public static Settlement settlement(ServerLevel level, UUID id) {
        return SettlementData.get(level).byId(id);
    }

    /** Every settlement in the dimension. */
    public static Collection<Settlement> settlements(ServerLevel level) {
        return SettlementData.get(level).all();
    }

    public static int reputation(Settlement settlement, UUID player) {
        return settlement.reputation(player);
    }

    /** Fires an event of this mod on the NeoForge bus. */
    public static void post(Event event) {
        NeoForge.EVENT_BUS.post(event);
    }
}
