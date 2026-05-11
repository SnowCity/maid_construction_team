package com.snowcity.maid_construction_team.network.payload.labor;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * 客户端→服务端：召回劳动力。
 */
public record RecallLaborPayload(UUID laborId, String providerId) implements CustomPacketPayload {

    public static final Type<RecallLaborPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "recall_labor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecallLaborPayload> STREAM_CODEC =
            StreamCodec.composite(
                    StreamCodec.of((buf, val) -> buf.writeUUID(val), buf -> buf.readUUID()),
                    RecallLaborPayload::laborId,
                    StreamCodec.of((buf, val) -> buf.writeUtf(val), buf -> buf.readUtf()),
                    RecallLaborPayload::providerId,
                    RecallLaborPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}