package com.wusternfuchs.cities.quest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/** A job posted on the settlement's board: bring items, or hunt monsters on its land. */
public final class Contract {
    public enum Kind {
        DELIVER, HUNT
    }

    private final int id;
    private final Kind kind;
    private final String item;
    private final int count;
    private final int reward;
    private final long expires;
    private int progress;
    @Nullable
    private UUID taker;

    public Contract(int id, Kind kind, String item, int count, int reward, long expires) {
        this.id = id;
        this.kind = kind;
        this.item = item;
        this.count = count;
        this.reward = reward;
        this.expires = expires;
    }

    public int id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public Item item() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(item));
    }

    public int count() {
        return count;
    }

    public int reward() {
        return reward;
    }

    public long expires() {
        return expires;
    }

    public int progress() {
        return progress;
    }

    public int remaining() {
        return Math.max(0, count - progress);
    }

    public void addProgress(int amount) {
        progress = Math.min(count, progress + amount);
    }

    public boolean done() {
        return progress >= count;
    }

    @Nullable
    public UUID taker() {
        return taker;
    }

    public void setTaker(@Nullable UUID taker) {
        this.taker = taker;
    }

    public Component describe() {
        return kind == Kind.HUNT
                ? Component.translatable("cities.board.hunt", count)
                : Component.translatable("cities.board.deliver", count, new ItemStack(item()).getHoverName());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("kind", kind.name());
        tag.putString("item", item);
        tag.putInt("count", count);
        tag.putInt("reward", reward);
        tag.putLong("expires", expires);
        tag.putInt("progress", progress);
        if (taker != null) {
            tag.putUUID("taker", taker);
        }
        return tag;
    }

    @Nullable
    public static Contract load(CompoundTag tag) {
        Kind kind;
        try {
            kind = Kind.valueOf(tag.getString("kind"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Contract contract = new Contract(tag.getInt("id"), kind, tag.getString("item"), tag.getInt("count"),
                tag.getInt("reward"), tag.getLong("expires"));
        contract.progress = tag.getInt("progress");
        if (tag.hasUUID("taker")) {
            contract.taker = tag.getUUID("taker");
        }
        return contract;
    }
}
