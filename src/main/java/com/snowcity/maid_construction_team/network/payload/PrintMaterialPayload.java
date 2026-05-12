package com.snowcity.maid_construction_team.network.payload;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record PrintMaterialPayload(UUID sessionId, int slot) implements CustomPacketPayload {
    public static final Type<PrintMaterialPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "print_material"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrintMaterialPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.sessionId);
                buf.writeInt(p.slot);
            },
            buf -> new PrintMaterialPayload(buf.readUUID(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}