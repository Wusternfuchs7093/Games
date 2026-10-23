package com.wusternfuchs.cities.network;

import com.wusternfuchs.cities.CitiesMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: "show me the job board of the town I am in". */
public record RequestBoardPayload() implements CustomPacketPayload {
    public static final Type<RequestBoardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitiesMod.MODID, "request_board"));
    public static final StreamCodec<ByteBuf, RequestBoardPayload> STREAM_CODEC = StreamCodec.unit(new RequestBoardPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
