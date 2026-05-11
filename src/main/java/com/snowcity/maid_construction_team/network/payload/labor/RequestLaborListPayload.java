package com.snowcity.maid_construction_team.network.payload.labor;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端→服务端：请求当前玩家的所有劳动力列表
 */
public record RequestLaborListPayload() implements CustomPacketPayload {

    public static final Type<RequestLaborListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "request_labor_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestLaborListPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {},
                    buf -> new RequestLaborListPayload()
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}