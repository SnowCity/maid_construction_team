package com.snowcity.maid_construction_team.client.preview;

import com.snowcity.maid_construction_team.client.key.ModKeyMappings;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * 蓝图预览模式的输入处理器（纯客户端）。
 * <p>
 * <b>操作方式（借鉴机械动力）：</b>
 * <ul>
 *   <li><b>切换工具</b>：按住左Alt（可自定义）+ 滚动滚轮，在水平移动、垂直移动、旋转之间循环切换。</li>
 *   <li><b>执行操作</b>：按住左Ctrl（可自定义）+ 滚动滚轮，执行当前工具对应的微调动作。
 *       水平移动基于玩家视线方向，垂直移动上下平移，旋转为顺时针/逆时针90°。</li>
 *   <li><b>步长切换</b>：同时按住Shift键，步长变为5格，否则为1格。</li>
 *   <li><b>确认放置</b>：按Enter键。</li>
 *   <li><b>取消预览</b>：按R键（可自定义）。</li>
 * </ul>
 * 所有操作按键均可通过原版“选项→控制”界面自定义。
 */
@EventBusSubscriber(modid = "maid_construction_team", value = Dist.CLIENT)
public class PreviewInputHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        PreviewPlacementContext ctx = PreviewManager.getVisibleContext(mc.player);

        // --- 确认与取消 ---
        while (ModKeyMappings.PREVIEW_CONFIRM.get().consumeClick()) {
            if (ctx != null) PreviewManager.confirm();
            return;
        }
        while (ModKeyMappings.PREVIEW_CANCEL.get().consumeClick()) {
            if (ctx != null) PreviewManager.exit();
            return;
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        PreviewPlacementContext ctx = PreviewManager.getVisibleContext(mc.player);
        if (ctx == null) return;

        boolean altDown = ModKeyMappings.PREVIEW_TOOL_SWITCH.get().isDown();
        boolean ctrlDown = ModKeyMappings.PREVIEW_EXECUTE.get().isDown();

        double scroll = event.getScrollDeltaY();

        if (altDown) {
            // Alt + 滚轮 → 切换工具
            event.setCanceled(true);
            if (scroll > 0) {
                PreviewManager.nextTool();
            } else if (scroll < 0) {
                PreviewManager.previousTool();
            }
        } else if (ctrlDown) {
            // Ctrl + 滚轮 → 执行当前工具操作
            event.setCanceled(true);
            if (ctx == null) return;

            int step = mc.player.isShiftKeyDown() ? 5 : 1;
            PlacementPreviewTool tool = PreviewManager.getCurrentTool();

            if (scroll > 0) {
                applyToolAction(tool, ctx, step, true, mc.player.getYRot());
            } else if (scroll < 0) {
                applyToolAction(tool, ctx, step, false, mc.player.getYRot());
            }
        }
    }

    /**
     * 根据工具类型和滚轮方向执行对应的微调操作。
     * @param forward true 表示滚轮向上（增加或顺时针）
     */
    private static void applyToolAction(PlacementPreviewTool tool, PreviewPlacementContext ctx,
                                        int step, boolean forward, float playerYaw) {
        switch (tool) {
            case MOVE_XZ -> {
                // 水平移动：基于玩家视线方向
                double rad = Math.toRadians(playerYaw);
                double dx = -Math.sin(rad) * step;
                double dz = Math.cos(rad) * step;
                if (!forward) {
                    dx = -dx;
                    dz = -dz;
                }
                int moveX = (int) Math.round(dx);
                int moveZ = (int) Math.round(dz);
                if (moveX != 0) ctx.moveX(moveX);
                if (moveZ != 0) ctx.moveZ(moveZ);
            }
            case MOVE_Y -> {
                if (forward) ctx.moveY(step);
                else ctx.moveY(-step);
            }
            case ROTATE -> {
                if (forward) ctx.rotateClockwise();
                else ctx.rotateCounterClockwise();
            }
        }
    }

    public static void reset() {
        // 暂无内部状态需要重置
    }
}