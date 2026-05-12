package com.snowcity.maid_construction_team.client.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.snowcity.maid_construction_team.core.schematic.BlockInfo;
import com.snowcity.maid_construction_team.core.schematic.SchematicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.*;

/**
 * 蓝图投影预览渲染器（完全对齐实际放置方案）
 * <p>
 * 核心思路：与 {@code ProgressivePlacer.transformBlockPos} 保持绝对一致的坐标计算，
 * 即“先旋转局部坐标，再平移到世界锚点”。边框则通过遍历所有方块的世界坐标动态计算，
 * 确保与实体方块完全贴合。
 */
public class SchematicProjectionRenderer implements IPlacedPreviewRenderer {

    private boolean depthTestEnabled = false;
    private boolean enableCulling = true;

    private static final float FRAME_RED = 1.0f, FRAME_GREEN = 0.41f, FRAME_BLUE = 0.71f;

    private int lastBlocksHash = 0;
    private Set<BlockPos> blockSet = Collections.emptySet();
    // 边框缓存（根据当前变换动态计算）
    private AABB worldBoundingBox = null;

    public void setDepthTestEnabled(boolean enabled) { this.depthTestEnabled = enabled; }
    public void setEnableCulling(boolean enable) { this.enableCulling = enable; }

    @Override
    public void render(GuiGraphics graphics, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                       PreviewPlacementContext context, float partialTick) {
        SchematicData schematic = context.getSchematicData();
        if (schematic.getBlocks().isEmpty()) return;

        // 避免重复重建缓存（蓝图数据未改变）
        int currentHash = schematic.getBlocks().hashCode();
        if (currentHash != lastBlocksHash) {
            rebuildCache(schematic);
            lastBlocksHash = currentHash;
        }
        if (blockSet.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel clientLevel = mc.level;
        if (clientLevel == null) return;

        // 获取当前的锚点、偏移和旋转（与放置时完全一致）
        BlockPos anchor = context.getAnchor();
        Vec3 offset = context.getTranslationOffset();
        Rotation rotation = context.getRotation();

        // 将浮点偏移应用到整数锚点上，构成最终的世界锚点
        Vec3 worldAnchor = Vec3.atLowerCornerOf(anchor).add(offset);

        // 准备渲染资源
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.translucent());
        RandomSource random = RandomSource.create();

        // 恒定亮度，确保任何环境下都清晰可见
        Lighting.setupForEntityInInventory();
        if (!depthTestEnabled) RenderSystem.disableDepthTest();

        // 临时变量用于动态计算世界边框
        int minWorldX = Integer.MAX_VALUE, minWorldY = Integer.MAX_VALUE, minWorldZ = Integer.MAX_VALUE;
        int maxWorldX = Integer.MIN_VALUE, maxWorldY = Integer.MIN_VALUE, maxWorldZ = Integer.MIN_VALUE;

        // ---------- 核心：与 ProgressivePlacer.transformBlockPos 完全相同的坐标计算 ----------
        for (BlockInfo info : schematic.getBlocks()) {
            BlockPos localPos = info.pos();
            if (enableCulling && isSurrounded(localPos)) continue;

            // ★ 步骤1：对蓝图局部坐标应用旋转（原地旋转）
            BlockPos rotatedPos = localPos.rotate(rotation);
            // ★ 步骤2：平移到世界锚点
            BlockPos worldPos = anchor.offset(rotatedPos);
            // 应用微调偏移（offset 已经在 worldAnchor 中体现，但为了保持一致性，这里也加上）
            // 实际上 worldAnchor 已经包含了 offset，但如果我们直接使用 anchor.offset() 并不包含 offset，
            // 所以需要手动加上。正确的做法是将 offset 也纳入世界坐标计算。
            double finalX = worldPos.getX() + offset.x;
            double finalY = worldPos.getY() + offset.y;
            double finalZ = worldPos.getZ() + offset.z;

            // 更新边框范围
            if (finalX < minWorldX) minWorldX = (int) Math.floor(finalX);
            if (finalY < minWorldY) minWorldY = (int) Math.floor(finalY);
            if (finalZ < minWorldZ) minWorldZ = (int) Math.floor(finalZ);
            if (finalX + 1 > maxWorldX) maxWorldX = (int) Math.ceil(finalX + 1);
            if (finalY + 1 > maxWorldY) maxWorldY = (int) Math.ceil(finalY + 1);
            if (finalZ + 1 > maxWorldZ) maxWorldZ = (int) Math.ceil(finalZ + 1);

            // 绘制方块
            poseStack.pushPose();
            poseStack.translate(finalX, finalY, finalZ);
            blockRenderer.renderBatched(info.state(), localPos, clientLevel,
                    poseStack, vertexConsumer, false, random, ModelData.EMPTY, null);
            poseStack.popPose();
        }

        if (!depthTestEnabled) RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();

        // 绘制动态计算的粉色边框
        if (minWorldX <= maxWorldX) {
            AABB worldBox = new AABB(minWorldX, minWorldY, minWorldZ, maxWorldX, maxWorldY, maxWorldZ);
            VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
            RenderSystem.disableDepthTest();
            LevelRenderer.renderLineBox(poseStack, lineConsumer, worldBox, FRAME_RED, FRAME_GREEN, FRAME_BLUE, 1.0f);
            RenderSystem.enableDepthTest();
        }

        // 注意：我们没有再对 poseStack 进行全局平移和旋转，因为每个方块都已经计算好了绝对世界坐标
    }

    /** 重建方块集合（用于快速剔除） */
    private void rebuildCache(SchematicData schematic) {
        blockSet = new HashSet<>();
        for (BlockInfo info : schematic.getBlocks()) {
            blockSet.add(info.pos());
        }
    }

    /** 判断一个方块是否六个面都被蓝图其他方块包围（内部剔除） */
    private boolean isSurrounded(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (!blockSet.contains(pos.relative(dir))) return false;
        }
        return true;
    }

    public boolean isPauseScreen() { return false; }
}