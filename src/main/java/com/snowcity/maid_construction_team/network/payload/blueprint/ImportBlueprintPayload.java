package com.snowcity.maid_construction_team.network.payload.blueprint;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImportBlueprintPayload(int slot, CompoundTag schematicNbt, String fileName) implements CustomPacketPayload {

    public static final Type<ImportBlueprintPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "import_blueprint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImportBlueprintPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.slot());
                        buf.writeNbt(payload.schematicNbt()); // 原版方法
                        buf.writeUtf(payload.fileName());
                    },
                    buf -> new ImportBlueprintPayload(
                            buf.readVarInt(),
                            buf.readNbt(), // 原版方法
                            buf.readUtf()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}