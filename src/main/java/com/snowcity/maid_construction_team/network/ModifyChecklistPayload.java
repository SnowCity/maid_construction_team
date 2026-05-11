// 包路径 com.snowcity.maid_construction_team.network.payload.MaterialslistPayload.java
package com.snowcity.maid_construction_team.network;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import com.snowcity.maid_construction_team.component.MaterialChecklistData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.network.payload.SyncHandItemPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

import java.util.Optional;

public record ModifyChecklistPayload(
        byte operation,    // 0 = REMOVE, 1 = RENAME
        BlockPos pos,
        Optional<String> newName,
        InteractionHand hand
) implements CustomPacketPayload {

    public static final Type<ModifyChecklistPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "modify_checklist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ModifyChecklistPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE, ModifyChecklistPayload::operation,
                    BlockPos.STREAM_CODEC, ModifyChecklistPayload::pos,
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), ModifyChecklistPayload::newName,
                    NeoForgeStreamCodecs.enumCodec(InteractionHand.class), ModifyChecklistPayload::hand,
                    ModifyChecklistPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // 服务端处理
    public static void handle(ModifyChecklistPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ItemStack stack = player.getItemInHand(payload.hand());
            MaterialChecklistData data = stack.getOrDefault(
                    ModDataComponents.MATERIAL_CHECKLIST.get(),
                    new MaterialChecklistData(new CompoundTag())
            );
            data = new MaterialChecklistData(data.ensureDataVersion());
            var list = data.getContainerList();
            int idx = -1;
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (entry.contains("x") && new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")).equals(payload.pos())) {
                    idx = i; break;
                }
            }
            if (idx == -1) return;

            if (payload.operation() == 0) { // REMOVE
                list.remove(idx);
            } else if (payload.operation() == 1 && payload.newName().isPresent()) { // RENAME
                MaterialChecklistData.setDisplayName(list.getCompound(idx), payload.newName().get());
            }
            MaterialChecklistData newData = data.withContainerList(list);
            stack.set(ModDataComponents.MATERIAL_CHECKLIST.get(), newData);
            player.setItemInHand(payload.hand(), stack);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();

            // 在处理完修改后，发送同步包
            ItemStack updatedStack = player.getItemInHand(payload.hand());
            CompoundTag tag = (CompoundTag) updatedStack.save(player.registryAccess(), new CompoundTag());
            PacketDistributor.sendToPlayer(player, new SyncHandItemPayload(payload.hand(), tag));
        });
    }
}