package com.wusternfuchs.cities.settlement;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** The three pools a settlement keeps, and how items convert into them. */
public enum Stock {
    FOOD,
    MATERIALS,
    WEALTH;

    public record Value(Stock stock, int points) {
    }

    public static Value of(ItemStack stack) {
        int count = stack.getCount();
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            return new Value(FOOD, Math.max(1, food.nutrition()) * count);
        }
        if (stack.is(Items.EMERALD_BLOCK)) return new Value(WEALTH, 81 * count);
        if (stack.is(Items.EMERALD)) return new Value(WEALTH, 9 * count);
        if (stack.is(Items.DIAMOND)) return new Value(WEALTH, 20 * count);
        if (stack.is(Items.GOLD_INGOT)) return new Value(WEALTH, 4 * count);
        if (stack.is(Items.IRON_INGOT)) return new Value(WEALTH, 3 * count);
        if (stack.is(ItemTags.LOGS)) return new Value(MATERIALS, 4 * count);
        if (stack.getItem() instanceof BlockItem) return new Value(MATERIALS, count);
        return new Value(WEALTH, count);
    }

    public void add(Settlement settlement, int points) {
        switch (this) {
            case FOOD -> settlement.addFood(points);
            case MATERIALS -> settlement.addMaterials(points);
            case WEALTH -> settlement.addWealth(points);
        }
    }

    /** Capitalised name for headings and tables. */
    public Component title() {
        return Component.translatable("cities.stock." + name().toLowerCase(Locale.ROOT) + ".title");
    }

    public Component displayName() {
        return Component.translatable("cities.stock." + name().toLowerCase(Locale.ROOT));
    }
}
