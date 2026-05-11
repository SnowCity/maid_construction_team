package com.snowcity.maid_construction_team.network.payload.session;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import com.snowcity.maid_construction_team.core.manager.SessionStateMachine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * 服务端主动通知客户端：某个会话的状态已变更。
 */
public record SessionStateChangedPayload(UUID sessionId, SessionStateMachine.State newState) implements CustomPacketPayload {

    public static final Type<SessionStateChangedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "session_state_changed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SessionStateChangedPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.sessionId());
                        buf.writeEnum(payload.newState());
                    },
                    buf -> new SessionStateChangedPayload(buf.readUUID(), buf.readEnum(SessionStateMachine.State.class))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}