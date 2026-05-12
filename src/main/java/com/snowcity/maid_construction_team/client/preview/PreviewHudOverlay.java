package com.snowcity.maid_construction_team.client.preview;

import com.snowcity.maid_construction_team.client.key.ModKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝图预览模式下的 HUD 信息提示。
 * 在屏幕左上角绘制当前工具、锚点、旋转、蓝图尺寸及操作快捷键。
 */
@EventBusSubscriber(modid = "maid_construction_team", value = Dist.CLIENT)
public class PreviewHudOverlay {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiLayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 获取当前应对玩家可见的预览上下文（基于手持物品或默认单预览）
        PreviewPlacementContext ctx = PreviewManager.getVisibleContext(mc.player);
        if (ctx == null) return; // 没有可见预览，不绘制 HUD

        GuiGraphics graphics = event.getGuiGraphics();
        int x = 5;
        int y = 5;
        int color = 0xFFFFFF;

        // 收集信息行
        List<Component> lines = new ArrayList<>();

        // 当前工具
        PlacementPreviewTool tool = PreviewManager.getCurrentTool();
        lines.add(Component.translatable("工具: " + tool.getDisplayName()).withStyle(ChatFormatting.YELLOW));

        // 锚点坐标
        lines.add(Component.literal("锚点: " + ctx.getAnchor().toShortString()));

        // 旋转角度
        String rotationText = switch (ctx.getRotation()) {
            case NONE -> "0°";
            case CLOCKWISE_90 -> "90°";
            case CLOCKWISE_180 -> "180°";
            case COUNTERCLOCKWISE_90 -> "270°";
        };
        lines.add(Component.literal("旋转: " + rotationText));

        // 蓝图尺寸
        lines.add(Component.literal("尺寸: " + ctx.getSizeDisplay() + " (" + ctx.getTotalBlocks() + "方块)"));

        // 快捷键提示
        String switchKey = ModKeyMappings.PREVIEW_TOOL_SWITCH.get().getTranslatedKeyMessage().getString();
        String executeKey = ModKeyMappings.PREVIEW_EXECUTE.get().getTranslatedKeyMessage().getString();
        lines.add(Component.literal(switchKey + "+滚轮:切换工具  " + executeKey + "+滚轮:操作").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal(executeKey + "+Shift+滚轮:5格步长  Enter:确认  ").withStyle(ChatFormatting.GRAY));

        // 绘制
        for (Component line : lines) {
            graphics.drawString(mc.font, line, x, y, color, true);
            y += mc.font.lineHeight + 2;
        }
    }
}