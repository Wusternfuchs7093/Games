package com.wusternfuchs.cities.trade;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.diplomacy.DiplomacyManager;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.Stock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Caravans carry surplus food and materials to settlements that lack them. The buyer pays its local price in wealth
 * when the goods arrive; robbers may take everything on the way.
 */
public final class TradeManager {
    private static final List<Stock> TRADED = List.of(Stock.FOOD, Stock.MATERIALS);
    private static final double TRAVEL_SPEED = 0.1; // blocks per tick, about a walking llama
    private static final int MIN_TRAVEL_TICKS = 600;
    private static final int MIN_SHIPMENT = 32;
    private static final int BASE_LOAD = 128;
    private static final int RELATION_PER_TRADE = 3;
    private static final int MERCHANT_STAY_TICKS = 6000;

    private TradeManager() {
    }

    // ------------------------------------------------------------------ prices

    public static int amount(Settlement s, Stock stock) {
        return switch (stock) {
            case FOOD -> s.food();
            case MATERIALS -> s.materials();
            case WEALTH -> s.wealth();
        };
    }

    /** How much of a stock the settlement wants to keep. */
    public static int target(Settlement s, Stock stock) {
        return switch (stock) {
            case FOOD -> s.population() * 20 + 50;
            case MATERIALS -> 200 + (s.project() != null ? 100 : 0);
            case WEALTH -> 0;
        };
    }

    /** Wealth paid per point: scarce goods are expensive, plentiful ones cheap. */
    public static double price(Settlement s, Stock stock) {
        double ratio = (double) target(s, stock) / (amount(s, stock) + 1);
        return Mth.clamp(Math.sqrt(ratio), 0.25, 4.0);
    }

    public static boolean canTrade(Settlement s) {
        return !s.abandoned() && s.count(BuildingType.WAREHOUSE) + s.count(BuildingType.MARKET) > 0;
    }

    public static int maxCaravans(Settlement s) {
        return 1 + s.count(BuildingType.MARKET);
    }

    public static List<Caravan> outgoing(SettlementData data, Settlement s) {
        List<Caravan> result = new ArrayList<>();
        for (Caravan caravan : data.caravans()) {
            if (caravan.from().equals(s.id())) {
                result.add(caravan);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ sending

    /** Looks for a buyer for this settlement's surplus and sends a caravan if one is found. */
    public static void plan(ServerLevel level, SettlementData data, Settlement s) {
        if (!CitiesConfig.TRADE_ENABLED.get() || !canTrade(s) || outgoing(data, s).size() >= maxCaravans(s)) {
            return;
        }
        for (Stock stock : TRADED) {
            int surplus = amount(s, stock) - target(s, stock);
            if (surplus < MIN_SHIPMENT) {
                continue;
            }
            Settlement best = null;
            double bestScore = 0;
            int bestAmount = 0;
            int bestPrice = 0;
            for (Settlement other : data.all()) {
                if (other == s || other.abandoned() || !DiplomacyManager.inRange(s, other)
                        || !DiplomacyManager.relation(s, other).allowsTrade() || isExpecting(data, other, stock)) {
                    continue;
                }
                int deficit = target(other, stock) - amount(other, stock);
                double unitPrice = price(other, stock);
                int affordable = (int) (other.wealth() / unitPrice);
                int load = Math.min(Math.min(surplus, deficit), Math.min(BASE_LOAD * maxCaravans(s), affordable));
                if (load < MIN_SHIPMENT) {
                    continue;
                }
                double distance = Math.sqrt(s.center().distSqr(other.center()));
                double score = load * unitPrice / (1 + distance / 100);
                if (score > bestScore) {
                    best = other;
                    bestScore = score;
                    bestAmount = load;
                    bestPrice = (int) Math.round(load * unitPrice);
                }
            }
            if (best != null) {
                send(level, data, s, best, stock, bestAmount, bestPrice);
                return;
            }
        }
    }

    private static boolean isExpecting(SettlementData data, Settlement s, Stock stock) {
        for (Caravan caravan : data.caravans()) {
            if (caravan.to().equals(s.id()) && caravan.stock() == stock) {
                return true;
            }
        }
        return false;
    }

    private static void send(ServerLevel level, SettlementData data, Settlement from, Settlement to, Stock stock,
                             int amount, int price) {
        stock.add(from, -amount);
        long now = level.getGameTime();
        double distance = Math.sqrt(from.center().distSqr(to.center()));
        long travel = Math.max(MIN_TRAVEL_TICKS, (long) (distance / TRAVEL_SPEED));
        data.caravans().add(new Caravan(from.id(), to.id(), stock, amount, price, now, now + travel));
        from.record(SettlementManager.day(level), "caravan_sent", to.name());
        data.setDirty();
    }

    // ------------------------------------------------------------------ arriving

    public static void resolveArrivals(ServerLevel level, SettlementData data) {
        if (data.caravans().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<Caravan> arrived = new ArrayList<>();
        Iterator<Caravan> it = data.caravans().iterator();
        while (it.hasNext()) {
            Caravan caravan = it.next();
            if (caravan.arriveTick() <= now) {
                it.remove();
                arrived.add(caravan);
            }
        }
        for (Caravan caravan : arrived) {
            arrive(level, data, caravan);
        }
        if (!arrived.isEmpty()) {
            data.setDirty();
        }
    }

    private static void arrive(ServerLevel level, SettlementData data, Caravan caravan) {
        Settlement from = data.byId(caravan.from());
        Settlement to = data.byId(caravan.to());
        long day = SettlementManager.day(level);
        if (to == null) {
            if (from != null) {
                caravan.stock().add(from, caravan.amount());
            }
            return;
        }

        double robbery = CitiesConfig.ROBBERY_CHANCE.get();
        double chance = Mth.clamp(robbery - 0.03 * (from == null ? 0 : from.guards()), Math.min(0.02, robbery), robbery);
        if (level.getRandom().nextDouble() < chance) {
            String origin = from == null ? "?" : from.name();
            to.record(day, "caravan_robbed", origin);
            if (from != null) {
                from.record(day, "caravan_robbed", origin);
            }
            broadcast(level, Component.translatable("cities.trade.robbed", origin, to.name())
                    .withStyle(ChatFormatting.RED));
            return;
        }

        caravan.stock().add(to, caravan.amount());
        int paid = Math.min(to.wealth(), caravan.price());
        to.addWealth(-paid);
        to.record(day, "caravan_arrived", from == null ? "?" : from.name());
        if (from != null) {
            from.addWealth(paid);
            from.record(day, "caravan_sold", to.name());
            DiplomacyManager.change(from, to, RELATION_PER_TRADE);
        }
        spawnMerchant(level, data, to, from);
    }

    /** If anyone is around to see it, the caravan shows up as a wandering trader with llamas. */
    private static void spawnMerchant(ServerLevel level, SettlementData data, Settlement to, @Nullable Settlement from) {
        if (!level.isPositionEntityTicking(to.center())) {
            return;
        }
        BlockPos column = to.center();
        if (from != null) {
            double dx = from.center().getX() - to.center().getX();
            double dz = from.center().getZ() - to.center().getZ();
            double length = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            int edge = to.tier().radiusChunks() * 16 - 8;
            BlockPos gate = to.center().offset((int) (dx / length * edge), 0, (int) (dz / length * edge));
            if (level.isLoaded(gate)) {
                column = gate;
            }
        }
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        WanderingTrader trader = EntityType.WANDERING_TRADER.spawn(level, pos, MobSpawnType.EVENT);
        if (trader == null) {
            return;
        }
        trader.setDespawnDelay(MERCHANT_STAY_TICKS);
        trader.setWanderTarget(to.center());
        for (int i = 0; i < 2; i++) {
            TraderLlama llama = EntityType.TRADER_LLAMA.spawn(level, pos.offset(i * 2 - 1, 0, 1), MobSpawnType.EVENT);
            if (llama != null) {
                llama.setLeashedTo(trader, true);
            }
        }
        Component message = Component.translatable("cities.trade.arrived",
                from == null ? "?" : from.name(), to.name()).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : level.players()) {
            if (data.at(player.blockPosition()) == to) {
                player.displayClientMessage(message, true);
            }
        }
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }
}
