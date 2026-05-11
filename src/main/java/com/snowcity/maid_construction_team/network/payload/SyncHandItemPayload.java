package com.snowcity.maid_construction_team.network.payload;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import com.snowcity.maid_construction_team.client.screen.ContractBookScreen;
import com.snowcity.maid_construction_team.client.screen.MaterialChecklistScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncHandItemPayload(InteractionHand hand, CompoundTag itemTag) implements CustomPacketPayload {

    public static final Type<SyncHandItemPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "sync_hand_item"));

    // 手动编解码，不使用 NeoForgeStreamCodecs.enumCodec
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncHandItemPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.hand);
                ByteBufCodecs.COMPOUND_TAG.encode(buf, payload.itemTag);
            },
            buf -> new SyncHandItemPayload(
                    buf.readEnum(InteractionHand.class),
                    ByteBufCodecs.COMPOUND_TAG.decode(buf)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // 客户端处理
    public static void handleClient(SyncHandItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            // 更新客户端手持物品
            ItemStack stack = ItemStack.parse(mc.player.registryAccess(), payload.itemTag()).orElse(ItemStack.EMPTY);
            mc.player.setItemInHand(payload.hand(), stack);
            // 若当前打开了物资登记表或契约之书，刷新数据
            if (mc.screen instanceof MaterialChecklistScreen checklistScreen) {
                checklistScreen.refreshFromServer(stack);
            }
            if (mc.screen instanceof ContractBookScreen bookScreen) {
                bookScreen.refreshFromServer(stack);
            }
        });
    }
}