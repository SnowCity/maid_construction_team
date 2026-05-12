package com.snowcity.maid_construction_team.api.contract.impl;

import com.snowcity.maid_construction_team.api.contract.AutoContractEffect;
import com.snowcity.maid_construction_team.api.contract.IContractEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;

import java.util.List;

/**
 * 采集工分工：破坏方块时有一定概率获得额外掉落。
 * <ul>
 *   <li>基础额外掉落概率：20%</li>
 *   <li>蜘蛛在夜晚/雷雨时概率翻倍（40%）</li>
 *   <li>洞穴蜘蛛在低亮度（天空光≤7 且方块光≤7）时概率+10%</li>
 * </ul>
 */
@AutoContractEffect(assignTo = {"minecraft:spider", "minecraft:cave_spider"}, maxActive = 2)
public class CollectorEffect implements IContractEffect {

    private static final float BASE_DROP_CHANCE = 0.20f;

    @Override
    public String getRoleId() {
        return "collector";
    }

    @Override
    public int getBlocksPerTickBonus(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        return 0; // 不影响放置
    }

    @Override
    public int getIntervalReduction(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        return 0; // 不影响间隔
    }

    @Override
    public float getDurabilitySave(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        return 0f; // 不影响耐久
    }

    /**
     * 返回额外掉落的概率（0.0~1.0）。
     */
    public float getExtraDropChance(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        float chance = BASE_DROP_CHANCE;

        if (entityType == EntityType.SPIDER) {
            if (level.isNight() || level.isThundering()) {
                chance *= 2; // 夜晚/雷雨翻倍
            }
        } else if (entityType == EntityType.CAVE_SPIDER) {
            int skyLight = level.getBrightness(LightLayer.SKY, anchor);
            int blockLight = level.getBrightness(LightLayer.BLOCK, anchor);
            if (skyLight <= 7 && blockLight <= 7) {
                chance += 0.10f;
            }
        }
        return Math.min(chance, 1.0f); // 不超过100%
    }

    @Override
    public List<net.minecraft.network.chat.Component> getEffectDescriptions() {
        return List.of(
                net.minecraft.network.chat.Component.translatable("mct.contract.collector.desc"),
                net.minecraft.network.chat.Component.translatable("mct.contract.collector.detail.1"+"(+40%)"),
                net.minecraft.network.chat.Component.translatable("mct.contract.collector.detail.2"),
                net.minecraft.network.chat.Component.translatable("mct.contract.placement_speed")
        );
    }
}