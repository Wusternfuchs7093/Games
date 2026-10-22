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
 * Server → client: everything the city screen shows. {@code pages} holds one list of lines per tab, in the order
 * of {@link #TABS}. An empty {@code name} means the player is not in or near a settlement.
 */
public record CityViewPayload(String name, boolean mayor, List<List<Component>> pages) implements CustomPacketPayload {
    public static final List<String> TABS = List.of("info", "mood", "people", "buildings", "trade", "relations",
            "laws", "research", "board", "history");

    public static final Type<CityViewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitiesMod.MODID, "city_view"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<List<Component>>> PAGES_CODEC =
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()).apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, CityViewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CityViewPayload::name,
            ByteBufCodecs.BOOL, CityViewPayload::mayor,
            PAGES_CODEC, CityViewPayload::pages,
            CityViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
