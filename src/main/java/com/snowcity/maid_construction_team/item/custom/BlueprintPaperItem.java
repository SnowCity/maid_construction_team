package com.snowcity.maid_construction_team.item.custom;

import com.snowcity.maid_construction_team.client.preview.PreviewManager;
import com.snowcity.maid_construction_team.client.screen.BlueprintSelectionScreen;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 蓝图纸物品。
 * <p>
 * 功能：
 * <ul>
 *   <li>右键（非潜行）：打开蓝图选择界面，从 schematics 目录导入 .nbt 文件。</li>
 *   <li>潜行右键：若物品已存储有效蓝图数据，则基于玩家准星指向的方块创建或更新蓝图预览，
 *       并将预览 ID 写入物品的 {@code PREVIEW_ID} 数据组件，实现多预览管理。</li>
 * </ul>
 */
public class BlueprintPaperItem extends Item {

    public BlueprintPaperItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 只响应主手操作，避免副手重复触发
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        // ---- 潜行右键：创建/更新蓝图预览 ----
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                // 检查物品是否包含蓝图数据
                var componentType = ModDataComponents.BLUEPRINT_DATA.get();
                if (componentType == null || !stack.has(componentType)) {
                    player.sendSystemMessage(Component.literal("蓝图纸为空，请先右键导入蓝图"));
                    return InteractionResultHolder.fail(stack);
                }

                // 获取玩家准星指向的方块位置作为初始锚点
                var hit = Minecraft.getInstance().hitResult;
                if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
                    player.sendSystemMessage(Component.literal("请指向一个方块来确定放置位置"));
                    return InteractionResultHolder.fail(stack);
                }
                BlockPos anchor = ((BlockHitResult) hit).getBlockPos();

                // 根据玩家朝向计算初始旋转
                Rotation rotation = getRotationFromPlayer(player);

                // 创建或更新预览（多预览模式）
                boolean success = PreviewManager.enterOrUpdate(player, anchor, rotation);
                if (success) {
                    player.sendSystemMessage(Component.literal("已进入蓝图预览模式"));
                } else {
                    player.sendSystemMessage(Component.literal("无法创建预览，请检查蓝图数据是否有效"));
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        // ---- 非潜行右键：打开蓝图选择界面（仅客户端） ----
        if (level.isClientSide()) {
            Minecraft.getInstance().setScreen(new BlueprintSelectionScreen(player));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        // 显示蓝图存储状态
        var componentType = ModDataComponents.BLUEPRINT_DATA.get();
        if (componentType != null && stack.has(componentType)) {
            tooltip.add(Component.literal("已存储蓝图").withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("空白").withStyle(ChatFormatting.GRAY));
        }

        // 显示预览状态（如果已绑定预览ID）
        var previewId = stack.get(ModDataComponents.PREVIEW_ID.get());
        if (previewId != null && previewId.isPresent()) {
            tooltip.add(Component.literal("已放置预览").withStyle(ChatFormatting.AQUA));
        }
    }

    /**
     * 根据玩家的水平朝向转换为蓝图旋转方向。
     */
    private static Rotation getRotationFromPlayer(Player player) {
        return switch (player.getDirection()) {
            case SOUTH -> Rotation.NONE;        // 南：0°
            case WEST  -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST  -> Rotation.COUNTERCLOCKWISE_90;
            default    -> Rotation.NONE;
        };
    }
}