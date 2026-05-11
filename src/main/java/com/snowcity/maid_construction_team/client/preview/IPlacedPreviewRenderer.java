package com.snowcity.maid_construction_team.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 蓝图放置预览的渲染接口。
 * 不同的实现可以提供不同的视觉效果（如线框、半透明方块等）。
 */
public interface IPlacedPreviewRenderer {

    /**
     * 在指定位置渲染蓝图的全息预览。
     *
     * @param graphics       渲染上下文
     * @param poseStack      矩阵栈，已应用相机偏移
     * @param bufferSource   缓冲源
     * @param context        预览上下文（蓝图数据、锚点、旋转等）
     * @param partialTick    部分刻时间
     */
    void render(GuiGraphics graphics,
                PoseStack poseStack,
                MultiBufferSource.BufferSource bufferSource,
                PreviewPlacementContext context,
                float partialTick);
}