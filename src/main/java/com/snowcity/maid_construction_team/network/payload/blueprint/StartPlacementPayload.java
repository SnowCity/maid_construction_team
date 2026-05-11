package com.snowcity.maid_construction_team.network.payload.blueprint;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

public record StartPlacementPayload(ItemStack blueprintStack, BlockPos anchor, Rotation rotation) implements CustomPacketPayload {

    public static final Type<StartPlacementPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "start_placement"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartPlacementPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ItemStack.STREAM_CODEC.encode(buf, payload.blueprintStack());
                        BlockPos.STREAM_CODEC.encode(buf, payload.anchor());
                        buf.writeEnum(payload.rotation());
                    },
                    buf -> {
                        ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
                        BlockPos anchor = BlockPos.STREAM_CODEC.decode(buf);
                        Rotation rotation = buf.readEnum(Rotation.class);
                        return new StartPlacementPayload(stack, anchor, rotation);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}