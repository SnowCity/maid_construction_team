package com.snowcity.maid_construction_team.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 蓝图预览渲染的事件监听器。
 * 负责在合适的渲染阶段，通过 {@link PreviewManager} 获取当前应显示的
 * 预览上下文和渲染器，并调用渲染。
 */
@EventBusSubscriber(modid = "maid_construction_team", value = Dist.CLIENT)
public class PlacementPreviewRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 获取当前活跃且可见的预览上下文（基于玩家主手物品）
        PreviewPlacementContext context = PreviewManager.getVisibleContext(mc.player);
        if (context == null) return;

        // 获取当前的渲染器实现
        IPlacedPreviewRenderer renderer = PreviewManager.getActiveRenderer();
        if (renderer == null) return;

        PoseStack poseStack = event.getPoseStack();
        // 将坐标原点移至相机位置（世界坐标 -> 相机空间）
        var camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // 调用解耦的渲染器进行绘制
        renderer.render(null, poseStack, bufferSource, context, event.getPartialTick().getGameTimeDeltaTicks());

        poseStack.popPose();
        bufferSource.endBatch();
    }
}