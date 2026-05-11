package com.snowcity.maid_construction_team.network.payload.session;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;

public record RequestSessionsPayload(Optional<UUID> sessionId) implements CustomPacketPayload {

    public static final Type<RequestSessionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "request_sessions"));

    // 手动编解码，先写一个 boolean 表示是否有 UUID，再写入或读取
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSessionsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        if (payload.sessionId().isPresent()) {
                            buf.writeBoolean(true);
                            buf.writeUUID(payload.sessionId().get());
                        } else {
                            buf.writeBoolean(false);
                        }
                    },
                    buf -> {
                        boolean hasId = buf.readBoolean();
                        if (hasId) {
                            return new RequestSessionsPayload(Optional.of(buf.readUUID()));
                        } else {
                            return new RequestSessionsPayload(Optional.empty());
                        }
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}