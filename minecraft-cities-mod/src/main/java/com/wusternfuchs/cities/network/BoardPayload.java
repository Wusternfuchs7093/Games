package com.wusternfuchs.cities.network;

import com.wusternfuchs.cities.CitiesMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server → client: the job board of a settlement, one entry per job or wish. An empty {@code name} means there is
 * no settlement around.
 */
public record BoardPayload(String name, List<Entry> entries, List<Component> notes) implements CustomPacketPayload {
    public static final int DELIVER = 0;
    public static final int HUNT = 1;
    public static final int WISH = 2;

    public static final int FREE = 0;
    public static final int MINE = 1;
    public static final int TAKEN = 2;

    /**
     * One line of the board. {@code kind} is {@link #DELIVER}, {@link #HUNT} or {@link #WISH}; {@code state} says
     * who took a hunt; {@code have} is how many of the wanted items the player carries.
     */
    public record Entry(int id, Component text, int kind, int state, int have) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::id,
                ComponentSerialization.STREAM_CODEC, Entry::text,
                ByteBufCodecs.VAR_INT, Entry::kind,
                ByteBufCodecs.VAR_INT, Entry::state,
                ByteBufCodecs.VAR_INT, Entry::have,
                Entry::new);
    }

    public static final Type<BoardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitiesMod.MODID, "board"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BoardPayload::name,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), BoardPayload::entries,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), BoardPayload::notes,
            BoardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
