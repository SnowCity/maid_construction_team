package com.snowcity.maid_construction_team.core.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 记录单个放置会话的实时进度。
 * <p>
 * 包含方块/实体的处理数量，以及材料不足时的缺料信息。
 */
public class PlacementProgress {

    /** 蓝图总方块数 */
    private final int totalBlocks;
    /** 蓝图总实体数 */
    private final int totalEntities;
    /** 当前已处理的方块索引（指向接下来将要处理的方块） */
    private int currentBlockIndex;
    /** 已放置的实体数量 */
    private int placedEntityCount;
    /** 是否已标记为完成（通常由 {@link PlacementSession} 控制） */
    private boolean completed;
    /** 是否已取消 */
    private boolean cancelled;
    /** 当前缺少的材料信息（仅在 WAITING_MATERIALS 状态时有效） */
    private String lackMaterialInfo;
    /** 记录每种方块已消耗的数量 */
    private final Map<Block, Integer> materialConsumed = new HashMap<>();
    private final List<BlockPos> missedBlocks = new ArrayList<>();
    private int completedBlockCount = 0;

    /** 记录一个因材料不足而跳过的方块坐标（世界坐标） */
    public void addMissedBlock(BlockPos pos) {
        missedBlocks.add(pos);
    }

    /** 获取所有缺失方块的坐标列表（不可变） */
    public List<BlockPos> getMissedBlocks() {
        return Collections.unmodifiableList(missedBlocks);
    }

    /** 移除指定坐标的缺失记录（当该方块被外部放置或后续恢复时调用） */
    public void removeMissedBlock(BlockPos pos) {
        missedBlocks.remove(pos);
    }

    /**
     * 清空已消耗材料映射，通常在手动恢复重建进度前调用。
     */
    public void clearMaterialConsumed() {
        materialConsumed.clear();
    }

    /**
     * 构造一个进度追踪器。
     *
     * @param totalBlocks  蓝图方块总数
     * @param totalEntities 蓝图实体总数
     */
    public PlacementProgress(int totalBlocks, int totalEntities) {
        this.totalBlocks = totalBlocks;
        this.totalEntities = totalEntities;
        this.currentBlockIndex = 0;
        this.placedEntityCount = 0;
    }

    /** 获取已完成方块的总数 */
    public int getCompletedBlockCount() {
        return completedBlockCount;
    }

    public void incrementCompletedBlockCount(){
        completedBlockCount++;
    }

    // 只读访问器
    public int getTotalBlocks() { return totalBlocks; }
    public int getTotalEntities() { return totalEntities; }
    public int getCurrentBlockIndex() { return currentBlockIndex; }
    public int getPlacedEntityCount() { return placedEntityCount; }
    public boolean isCompleted() { return completed; }
    public boolean isCancelled() { return cancelled; }
    public String getLackMaterialInfo() { return lackMaterialInfo; }

    // 进度更新方法

    /** 将方块索引向前推进 1 */
    public void advanceBlock() { this.currentBlockIndex++; }

    /** 将已放置实体数加 1 */
    public void advanceEntity() { this.placedEntityCount++; }

    /** 标记任务已成功完成 */
    public void markCompleted() { this.completed = true; }

    /** 标记任务被取消 */
    public void cancel() { this.cancelled = true; }

    /** 设置缺料信息 */
    public void setLackMaterialInfo(String info) { this.lackMaterialInfo = info; }

    /** 清除缺料信息（材料恢复后调用） */
    public void clearLackMaterialInfo() { this.lackMaterialInfo = null; }

    /** 获取已消耗材料的不可变视图 */
    public Map<Block, Integer> getMaterialConsumed() {
        return java.util.Collections.unmodifiableMap(materialConsumed);
    }

    /** 放置器成功消耗一个材料后调用，记录消耗 */
    public void recordConsumption(BlockState state) {
        materialConsumed.merge(state.getBlock(), 1, Integer::sum);
    }

    /** 恢复当前方块索引（用于从持久化加载） */
    public void setCurrentBlockIndex(int index) {
        this.currentBlockIndex = index;
    }

    /** 恢复已放置实体计数（用于从持久化加载） */
    public void setPlacedEntityCount(int count) {
        this.placedEntityCount = count;
    }
}