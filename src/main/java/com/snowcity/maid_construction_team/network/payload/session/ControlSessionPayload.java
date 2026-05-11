package com.snowcity.maid_construction_team.network.payload.session;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import com.snowcity.maid_construction_team.core.schematic.MaterialShortageStrategy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record ControlSessionPayload(UUID sessionId, Action action, MaterialShortageStrategy strategy) implements CustomPacketPayload {

    public enum Action {
        PAUSE, RESUME, CANCEL, SET_STRATEGY
    }

    public static final Type<ControlSessionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "control_session"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ControlSessionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.sessionId());
                        buf.writeEnum(payload.action());
                        if (payload.action() == Action.SET_STRATEGY) {
                            buf.writeEnum(payload.strategy());
                        }
                    },
                    buf -> {
                        UUID id = buf.readUUID();
                        Action action = buf.readEnum(Action.class);
                        MaterialShortageStrategy strategy = MaterialShortageStrategy.PAUSE; // 默认值
                        if (action == Action.SET_STRATEGY) {
                            strategy = buf.readEnum(MaterialShortageStrategy.class);
                        }
                        return new ControlSessionPayload(id, action, strategy);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}