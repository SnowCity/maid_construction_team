package com.snowcity.maid_construction_team.network.payload.labor;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * 客户端→服务端：派遣劳动力到指定会话。
 */
public record DispatchLaborPayload(UUID laborId, String providerId, UUID sessionId) implements CustomPacketPayload {

    public static final Type<DispatchLaborPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "dispatch_labor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DispatchLaborPayload> STREAM_CODEC =
            StreamCodec.composite(
                    StreamCodec.of((buf, val) -> buf.writeUUID(val), buf -> buf.readUUID()),
                    DispatchLaborPayload::laborId,
                    StreamCodec.of((buf, val) -> buf.writeUtf(val), buf -> buf.readUtf()),
                    DispatchLaborPayload::providerId,
                    StreamCodec.of((buf, val) -> buf.writeUUID(val), buf -> buf.readUUID()),
                    DispatchLaborPayload::sessionId,
                    DispatchLaborPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}