package com.snowcity.maid_construction_team.item.custom;

import com.snowcity.maid_construction_team.client.screen.MaterialChecklistScreen;
import com.snowcity.maid_construction_team.component.MaterialChecklistData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.network.payload.SyncHandItemPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MaterialChecklistItem extends Item {

    public MaterialChecklistItem(Properties properties) {
        super(properties);
    }

    // ---------- 右键方块（蹲下切换标记）----------
    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (context.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return InteractionResult.PASS;

        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, context.getClickedFace());
        if (handler == null && !(be instanceof net.minecraft.world.Container)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            toggleMark(stack, level.dimension().location(), pos, player);
            // 手持物品同步到客户端
            player.setItemInHand(context.getHand(), stack);
            // 立即同步手持物品到客户端
            CompoundTag tag = (CompoundTag) stack.save(player.registryAccess(), new CompoundTag());
            PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncHandItemPayload(context.getHand(), tag));
            syncPlayerInventory(player);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // ---------- 右键空气（不做任何事）----------
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new MaterialChecklistScreen(stack, hand));
        }
        return InteractionResultHolder.success(stack);
    }

    // ========== 标记逻辑 ==========

    private void toggleMark(ItemStack stack, ResourceLocation dimension, BlockPos pos, Player player) {
        MaterialChecklistData data = stack.getOrDefault(
                ModDataComponents.MATERIAL_CHECKLIST.get(),
                new MaterialChecklistData(new CompoundTag())
        );
        data = new MaterialChecklistData(data.ensureDataVersion());

        ListTag containers = data.getContainerList();
        int idx = findContainerIndex(containers, dimension, pos);
        if (idx >= 0) {
            containers.remove(idx);
            player.sendSystemMessage(Component.literal("已取消登记容器 " + pos.toShortString()));
        } else {
            CompoundTag entry = new CompoundTag();
            entry.putString(MaterialChecklistData.TAG_DIMENSION, dimension.toString());
            entry.putInt(MaterialChecklistData.TAG_X, pos.getX());
            entry.putInt(MaterialChecklistData.TAG_Y, pos.getY());
            entry.putInt(MaterialChecklistData.TAG_Z, pos.getZ());
            containers.add(entry);
            player.sendSystemMessage(Component.literal("已登记容器 " + pos.toShortString()));
        }

        MaterialChecklistData newData = data.withContainerList(containers);
        stack.set(ModDataComponents.MATERIAL_CHECKLIST.get(), newData);
    }

    private int findContainerIndex(ListTag containers, ResourceLocation dimension, BlockPos pos) {
        for (int i = 0; i < containers.size(); i++) {
            CompoundTag entry = containers.getCompound(i);
            if (Objects.equals(entry.getString(MaterialChecklistData.TAG_DIMENSION), dimension.toString())
                    && entry.getInt(MaterialChecklistData.TAG_X) == pos.getX()
                    && entry.getInt(MaterialChecklistData.TAG_Y) == pos.getY()
                    && entry.getInt(MaterialChecklistData.TAG_Z) == pos.getZ()) {
                return i;
            }
        }
        return -1;
    }

    // 强制同步（保留之前的高亮修复）
    private void syncPlayerInventory(Player player) {
        if (player instanceof ServerPlayer sp) {
            sp.getInventory().setChanged();
            sp.containerMenu.broadcastChanges();
        }
    }
}