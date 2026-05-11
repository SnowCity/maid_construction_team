package com.snowcity.maid_construction_team.core.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 蓝图顶层数据容器，包含尺寸、所有方块、所有实体以及可选的原始 NBT 数据。
 * 通过内部 {@link Builder} 构建，所有字段均为不可变。
 */
public final class SchematicData {
    private final Vec3i size;
    private final List<BlockInfo> blocks;
    private final List<EntityInfo> entities;

    /**
     * 蓝图原始的 NBT 数据。
     * 该字段由解析器设置，用于持久化存储或离线恢复场景。
     * 若不为 null，可通过 {@link #getOriginalNbt()} 获取。
     */
    @Nullable
    private final CompoundTag originalNbt;

    // ===== 三个构造方法 =====

    /**
     * 完整参数构造方法。
     * 由 {@link Builder#build()} 在构建时调用。
     */
    private SchematicData(Vec3i size, List<BlockInfo> blocks, List<EntityInfo> entities, @Nullable CompoundTag originalNbt) {
        this.size = Objects.requireNonNull(size, "size cannot be null");
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        this.entities = Collections.unmodifiableList(new ArrayList<>(entities));
        this.originalNbt = originalNbt;
    }

    // 保留你原有的两个便捷构造方法，它们调用上面的完整构造方法
    public SchematicData(Vec3i size, List<BlockInfo> blocks, List<EntityInfo> entities) {
        this(size, blocks, entities, null);
    }

    public SchematicData(Vec3i size, List<BlockInfo> blocks) {
        this(size, blocks, new ArrayList<>(), null);
    }

    // ===== Getter 方法 =====

    public Vec3i getSize() { return size; }
    public List<BlockInfo> getBlocks() { return blocks; }
    public List<EntityInfo> getEntities() { return entities; }
    public int getBlockCount() { return blocks.size(); }
    public int getEntityCount() { return entities.size(); }
    public boolean isEmpty() { return blocks.isEmpty() && entities.isEmpty(); }

    @Nullable
    public CompoundTag getOriginalNbt() { return originalNbt; }

    // ===== 内部构建器 Builder =====

    public static class Builder {
        private Vec3i size;
        private final List<BlockInfo> blocks = new ArrayList<>();
        private final List<EntityInfo> entities = new ArrayList<>();
        @Nullable
        private CompoundTag originalNbt;

        public Builder setSize(int x, int y, int z) {
            this.size = new Vec3i(x, y, z);
            return this;
        }

        public Builder setSize(Vec3i size) {
            this.size = size;
            return this;
        }

        public Builder addBlock(BlockInfo block) {
            blocks.add(Objects.requireNonNull(block));
            return this;
        }

        public Builder addEntity(EntityInfo entity) {
            entities.add(Objects.requireNonNull(entity));
            return this;
        }

        /**
         * 设置蓝图原始的 NBT 数据。
         * 仅在从文件解析蓝图时调用，用于后续持久化。
         */
        public Builder originalNbt(CompoundTag nbt) {
            this.originalNbt = nbt;
            return this;
        }

        public SchematicData build() {
            if (size == null) {
                throw new IllegalStateException("Size must be set before building SchematicData");
            }
            // 调用完整参数的私有构造方法
            return new SchematicData(size, blocks, entities, originalNbt);
        }
    }
}