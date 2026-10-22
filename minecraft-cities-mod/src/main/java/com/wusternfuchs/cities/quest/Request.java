package com.wusternfuchs.cities.quest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** A personal wish of one villager: "Ivar would love a cake". */
public record Request(UUID villager, String name, String item, int count, long expires) {

    public Item resolveItem() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(item));
    }

    public Component describe() {
        return Component.translatable("cities.board.request", name, count, new ItemStack(resolveItem()).getHoverName());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("villager", villager);
        tag.putString("name", name);
        tag.putString("item", item);
        tag.putInt("count", count);
        tag.putLong("expires", expires);
        return tag;
    }

    public static Request load(CompoundTag tag) {
        return new Request(tag.getUUID("villager"), tag.getString("name"), tag.getString("item"),
                tag.getInt("count"), tag.getLong("expires"));
    }
}
