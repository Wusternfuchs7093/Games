package com.wusternfuchs.cities.network;

import com.wusternfuchs.cities.CitiesMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: "show me the city I am in". */
public record RequestCityPayload() implements CustomPacketPayload {
    public static final Type<RequestCityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitiesMod.MODID, "request_city"));
    public static final StreamCodec<ByteBuf, RequestCityPayload> STREAM_CODEC = StreamCodec.unit(new RequestCityPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
