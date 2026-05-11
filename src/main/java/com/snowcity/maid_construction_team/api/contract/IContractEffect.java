package com.snowcity.maid_construction_team.api.contract;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 契约分工效果接口。
 * 每种分工（建筑工、工程师等）对应一个实现，负责计算该分工的具体加成数值。
 */
public interface IContractEffect {
    /**
     * 返回分工标识，如 "builder", "engineer"
     */
    String getRoleId();

    /**
     * 计算对单次放置数量的额外加成（可为负数表示惩罚）
     */
    int getBlocksPerTickBonus(EntityType<?> entityType, ServerLevel level, BlockPos anchor);

    /**
     * 计算对放置间隔的减少量（Tick，可为负数表示惩罚）
     */
    int getIntervalReduction(EntityType<?> entityType, ServerLevel level, BlockPos anchor);

    /**
     * 计算对工具耐久消耗的修正比例（0.0~1.0，0.2表示节省20%，负数表示额外消耗）
     */
    float getDurabilitySave(EntityType<?> entityType, ServerLevel level, BlockPos anchor);

    /**
     * 可选：附加的负面效果
     */
    default Map<String, Float> getNegativeEffects(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        return Collections.emptyMap();
    }

    /**
     * 返回该分工效果的人类可读描述（多行）。
     * 用于在 UI 中展示给玩家，不依赖具体世界状态。
     */
    default List<Component> getEffectDescriptions() {
        return Collections.emptyList();
    }
}