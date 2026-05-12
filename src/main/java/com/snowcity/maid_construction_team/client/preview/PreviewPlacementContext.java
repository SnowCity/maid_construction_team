package com.snowcity.maid_construction_team.client.preview;

import com.snowcity.maid_construction_team.core.schematic.SchematicData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * 客户端预览时使用的动态放置参数。
 * <p>
 * 持有蓝图数据、当前锚点以及旋转方向，支持微调操作。
 * 所有对位置和旋转的修改都会立即反映在下一帧的全息渲染中。
 */
public class PreviewPlacementContext {

    private final SchematicData schematicData;
    private BlockPos anchor;
    private Rotation rotation;
    private Vec3 translationOffset = Vec3.ZERO;

    /**
     * 构造预览上下文。
     *
     * @param schematicData 蓝图数据（不可变）
     * @param anchor        初始锚点世界坐标
     * @param rotation      初始旋转方向
     */
    public PreviewPlacementContext(SchematicData schematicData, BlockPos anchor, Rotation rotation) {
        this.schematicData = schematicData;
        this.anchor = anchor;
        this.rotation = rotation;
    }

    // ==================== 访问器 ====================

    public SchematicData getSchematicData() {
        return schematicData;
    }

    public BlockPos getAnchor() {
        return anchor;
    }

    public Rotation getRotation() {
        return rotation;
    }

    /**
     * 获取蓝图的尺寸，便于在 GUI 上显示“长×宽×高”。
     */
    public String getSizeDisplay() {
        var size = schematicData.getSize();
        return size.getX() + "×" + size.getY() + "×" + size.getZ();
    }

    /**
     * 获取总方块数，便于在 GUI 上显示。
     */
    public int getTotalBlocks() {
        return schematicData.getBlocks().size();
    }

    // ==================== 微调操作 ====================

    /**
     * 沿世界 X 轴平移指定格数。
     */
    public void moveX(int steps) {
        anchor = anchor.offset(steps, 0, 0);
    }

    /**
     * 沿世界 Y 轴平移指定格数。
     */
    public void moveY(int steps) {
        anchor = anchor.offset(0, steps, 0);
    }

    /**
     * 沿世界 Z 轴平移指定格数。
     */
    public void moveZ(int steps) {
        anchor = anchor.offset(0, 0, steps);
    }

    /** 获取当前浮点平移偏移量（用于精确预览） */
    public Vec3 getTranslationOffset() {
        return translationOffset;
    }

    /** 设置浮点平移偏移量 */
    public void setTranslationOffset(Vec3 offset) {
        this.translationOffset = offset;
    }

    /** 获取最终渲染/放置时使用的世界锚点（考虑偏移） */
    public BlockPos getFinalAnchor() {
        // 返回原始的整数锚点（用于放置时）
        return anchor;
    }

    /**
     * 顺时针旋转 90°。
     */
    public void rotateClockwise() {
        rotation = switch (rotation) {
            case NONE -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.NONE;
        };
    }

    /**
     * 逆时针旋转 90°。
     */
    public void rotateCounterClockwise() {
        rotation = switch (rotation) {
            case NONE -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.NONE;
        };
    }
}