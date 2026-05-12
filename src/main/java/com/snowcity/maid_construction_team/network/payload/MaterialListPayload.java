package com.snowcity.maid_construction_team.network.payload;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record MaterialListPayload(UUID sessionId, Map<Block, Integer> materials) implements CustomPacketPayload {
    public static final Type<MaterialListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "material_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialListPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.sessionId);
                Map<Block, Integer> map = payload.materials;
                buf.writeVarInt(map.size());
                for (var entry : map.entrySet()) {
                    buf.writeVarInt(BuiltInRegistries.BLOCK.getId(entry.getKey()));
                    buf.writeVarInt(entry.getValue());
                }
            },
            buf -> {
                UUID sessionId = buf.readUUID();
                int size = buf.readVarInt();
                Map<Block, Integer> map = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    Block block = BuiltInRegistries.BLOCK.byId(buf.readVarInt());
                    int count = buf.readVarInt();
                    map.put(block, count);
                }
                return new MaterialListPayload(sessionId, map);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}