package com.wusternfuchs.cities.trade;

import com.wusternfuchs.cities.settlement.Stock;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

/** Goods on their way from one settlement to another. The buyer pays {@code price} on arrival. */
public record Caravan(UUID from, UUID to, Stock stock, int amount, int price, long departTick, long arriveTick) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("from", from);
        tag.putUUID("to", to);
        tag.putString("stock", stock.name().toLowerCase(Locale.ROOT));
        tag.putInt("amount", amount);
        tag.putInt("price", price);
        tag.putLong("depart", departTick);
        tag.putLong("arrive", arriveTick);
        return tag;
    }

    @Nullable
    public static Caravan load(CompoundTag tag) {
        if (!tag.hasUUID("from") || !tag.hasUUID("to")) {
            return null;
        }
        Stock stock;
        try {
            stock = Stock.valueOf(tag.getString("stock").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new Caravan(tag.getUUID("from"), tag.getUUID("to"), stock, tag.getInt("amount"), tag.getInt("price"),
                tag.getLong("depart"), tag.getLong("arrive"));
    }
}
