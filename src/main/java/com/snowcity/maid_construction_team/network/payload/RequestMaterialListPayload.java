package com.snowcity.maid_construction_team.network.payload;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record RequestMaterialListPayload(UUID sessionId) implements CustomPacketPayload {
    public static final Type<RequestMaterialListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "request_material_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMaterialListPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUUID(payload.sessionId),
            buf -> new RequestMaterialListPayload(buf.readUUID())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}