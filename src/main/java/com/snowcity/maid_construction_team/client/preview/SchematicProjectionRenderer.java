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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.*;

/**
 * 可靠的即时渲染蓝图预览器。
 * <p>
 * 功能：
 * <ul>
 *   <li>半透明方块渲染（原版 batch 方式）</li>
 *   <li>内部方块剔除（可选）</li>
 *   <li>恒定亮度，不受环境光影响</li>
 *   <li>粉色边框标注范围</li>
 *   <li>可调深度测试</li>
 * </ul>
 */
public class SchematicProjectionRenderer implements IPlacedPreviewRenderer {

    private boolean depthTestEnabled = false;
    private boolean enableCulling = true;

    private static final float FRAME_RED = 1.0f, FRAME_GREEN = 0.41f, FRAME_BLUE = 0.71f;

    private int lastBlocksHash = 0;
    private Set<BlockPos> blockSet = Collections.emptySet();
    private Map<BlockPos, BlockState> blueprintStates = Collections.emptyMap();
    private BlockPos minPos = BlockPos.ZERO;
    private AABB localBoundingBox = null;

    public void setDepthTestEnabled(boolean enabled) {
        this.depthTestEnabled = enabled;
    }

    public void setEnableCulling(boolean enable) {
        this.enableCulling = enable;
    }

    @Override
    public void render(GuiGraphics graphics, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                       PreviewPlacementContext context, float partialTick) {
        SchematicData schematic = context.getSchematicData();
        if (schematic.getBlocks().isEmpty()) return;

        // 如果方块列表发生变化，重建缓存
        int currentHash = schematic.getBlocks().hashCode();
        if (currentHash != lastBlocksHash) {
            rebuildCache(schematic);
            lastBlocksHash = currentHash;
        }
        if (blockSet.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel clientLevel = mc.level;
        if (clientLevel == null) return;

        BlockPos anchor = context.getAnchor();
        Vec3 offset = context.getTranslationOffset();
        Rotation rotation = context.getRotation();
        Vec3 worldAnchor = Vec3.atLowerCornerOf(anchor).add(offset);

        poseStack.pushPose();
        poseStack.translate(worldAnchor.x, worldAnchor.y, worldAnchor.z);
        applyRotation(poseStack, rotation);

        // 准备渲染
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.translucent());
        RandomSource random = RandomSource.create();

        // 恒定亮度
        Lighting.setupForEntityInInventory();
        if (!depthTestEnabled) {
            RenderSystem.disableDepthTest();
        }

        // 逐块渲染（应用内部剔除）
        for (BlockInfo info : schematic.getBlocks()) {
            BlockPos localPos = info.pos();
            if (enableCulling && isSurrounded(localPos)) continue;

            poseStack.pushPose();
            Vec3 rel = new Vec3(localPos.getX() - minPos.getX(),
                    localPos.getY() - minPos.getY(),
                    localPos.getZ() - minPos.getZ());
            poseStack.translate(rel.x, rel.y, rel.z);

            blockRenderer.renderBatched(info.state(), localPos, clientLevel,
                    poseStack, vertexConsumer, false, random, ModelData.EMPTY, null);
            poseStack.popPose();
        }

        if (!depthTestEnabled) {
            RenderSystem.enableDepthTest();
        }
        Lighting.setupFor3DItems();

        // 绘制粉色边框（始终最前，忽略深度）
        if (localBoundingBox != null) {
            VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
            RenderSystem.disableDepthTest();
            LevelRenderer.renderLineBox(poseStack, lineConsumer, localBoundingBox, FRAME_RED, FRAME_GREEN, FRAME_BLUE, 1.0f);
            RenderSystem.enableDepthTest();
        }

        poseStack.popPose();
    }

    private void rebuildCache(SchematicData schematic) {
        blockSet = new HashSet<>();
        Map<BlockPos, BlockState> states = new HashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockInfo info : schematic.getBlocks()) {
            BlockPos pos = info.pos();
            blockSet.add(pos);
            states.put(pos, info.state());
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }

        blueprintStates = states;
        minPos = new BlockPos(minX, minY, minZ);
        localBoundingBox = new AABB(0, 0, 0, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    private boolean isSurrounded(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (!blockSet.contains(pos.relative(dir))) return false;
        }
        return true;
    }

    private void applyRotation(PoseStack ps, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90 -> ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90));
            case CLOCKWISE_180 -> ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            case COUNTERCLOCKWISE_90 -> ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
            default -> {}
        }
    }
}