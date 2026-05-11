package com.snowcity.maid_construction_team.core.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Objects;

public class BlockInfo {
    private final BlockPos pos;
    private final BlockState state;
    @Nullable
    private final CompoundTag blockEntityData;

    /**
     * 完整构造函数。
     *
     * @param pos             方块在蓝图内部坐标系中的相对位置
     * @param state           该位置的方块状态
     * @param blockEntityData 方块实体的 NBT 数据，若无则为 null
     */
    public BlockInfo(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntityData) {
        // 进行显式空值检查,一旦传入了null, 立即在构造函数中抛出 NullPointerException
        this.pos = Objects.requireNonNull(pos, "pos cannot be null");
        this.state = Objects.requireNonNull(state, "state cannot be null");
        this.blockEntityData = blockEntityData != null ? blockEntityData.copy() : null;
    }

    /**
     * 便捷构造函数，用于不含方块实体数据的普通方块。
     *
     * @param pos   方块在蓝图内部坐标系中的相对位置
     * @param state 该位置的方块状态
     */
    public BlockInfo(BlockPos pos, BlockState state) {
        this(pos, state, null);
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    @Nullable
    public CompoundTag getBlockEntityData() {
        // 返回副本以防止外部修改
        return blockEntityData != null ? blockEntityData.copy() : null;
    }

    /**
     * @return 是否具有方块实体数据
     */
    public boolean hasBlockEntity() {
        return blockEntityData != null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BlockInfo other)) return false;
        return pos.equals(other.pos) &&
                state.equals(other.state) &&
                Objects.equals(blockEntityData, other.blockEntityData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos, state, blockEntityData);
    }

    // 返回一个代表该对象的、人类可读的字符串描述。
    // 用于调试和日志记录，方便快速定位
    @Override
    public String toString() {
        return "BlockInfo{" +
                "pos=" + pos +
                ", state=" + state +
                ", hasBlockEntity=" + hasBlockEntity() +
                '}';
    }
}
