package com.snowcity.maid_construction_team.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.snowcity.maid_construction_team.core.schematic.BlockInfo;
import com.snowcity.maid_construction_team.core.schematic.SchematicData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * 线框预览渲染器的实现。
 * 使用半透明青绿色线框绘制每个方块的边界盒。
 */
public class LineBoxPreviewRenderer implements IPlacedPreviewRenderer {

    @Override
    public void render(GuiGraphics graphics,
                       PoseStack poseStack,
                       MultiBufferSource.BufferSource bufferSource,
                       PreviewPlacementContext context,
                       float partialTick) {
        SchematicData schematic = context.getSchematicData();
        BlockPos anchor = context.getAnchor();
        Rotation rotation = context.getRotation();

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.LINES);

        for (BlockInfo info : schematic.getBlocks()) {
            BlockPos localPos = info.getPos();
            // 旋转并偏移到最终世界坐标
            BlockPos worldPos = transformPos(localPos, anchor, rotation);

            // 绘制方块线框（从0到1的边界盒）
            AABB box = new AABB(worldPos);
            LevelRenderer.renderLineBox(poseStack, vertexConsumer, box,
                    0.11f, 0.71f, 0.71f, 0.6f); // 半透明青绿色
        }
    }

    /**
     * 将蓝图局部坐标根据锚点和旋转转换为世界坐标。
     * 与 ProgressivePlacer.transformBlockPos 逻辑一致。
     */
    private BlockPos transformPos(BlockPos localPos, BlockPos anchor, Rotation rotation) {
        BlockPos rotated = localPos.rotate(rotation);
        return anchor.offset(rotated);
    }
}