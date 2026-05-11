package com.snowcity.maid_construction_team.core.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 描述蓝图中的单个实体及其初始状态。
 * 该类是不可变的。
 */
public record EntityInfo(Vec3 pos, CompoundTag entityData) {
    /**
     * 构造函数。
     *
     * @param pos        实体在蓝图内部坐标系中的相对位置
     * @param entityData 实体的完整 NBT 数据（必须包含实体类型 id）
     */
    public EntityInfo(Vec3 pos, CompoundTag entityData) {
        this.pos = Objects.requireNonNull(pos, "pos cannot be null");
        this.entityData = Objects.requireNonNull(entityData, "entityData cannot be null").copy();
    }

    @Override
    public CompoundTag entityData() {
        return entityData.copy();
    }

    /**
     * 从 NBT 数据中获取实体类型 ID。
     *
     * @return 实体类型的字符串 ID，例如 "minecraft:zombie"
     */
    public String getEntityTypeId() {
        return entityData.getString("id");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EntityInfo other)) return false;
        return pos.equals(other.pos) && entityData.equals(other.entityData);
    }

    @Override
    public @NotNull String toString() {
        return "EntityInfo{" +
                "pos=" + pos +
                ", type=" + getEntityTypeId() +
                '}';
    }
}