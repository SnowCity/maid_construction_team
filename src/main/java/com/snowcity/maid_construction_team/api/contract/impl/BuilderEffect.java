package com.snowcity.maid_construction_team.api.contract.impl;

import com.snowcity.maid_construction_team.api.contract.AutoContractEffect;
import com.snowcity.maid_construction_team.api.contract.IContractEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

import java.util.List;

@AutoContractEffect(assignTo = {"minecraft:zombie", "minecraft:husk"}, maxActive = 5)
public class BuilderEffect implements IContractEffect {

    @Override
    public String getRoleId() {
        return "builder";
    }

    @Override
    public int getBlocksPerTickBonus(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        int base = 2;
        // 亡灵在夜间（或雷雨天视为低亮度）效果翻倍
        if (isUndead(entityType) && isNightOrDark(level)) {
            base *= 2;
        }
        return base;
    }

    @Override
    public int getIntervalReduction(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        return 0; // 建筑工不影响间隔
    }

    @Override
    public float getDurabilitySave(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        return 0f;
    }

    private boolean isUndead(EntityType<?> type) {
        // 可扩展更多亡灵类型
        return type == EntityType.ZOMBIE || type == EntityType.SKELETON || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.HUSK || type == EntityType.DROWNED;
    }

    private boolean isNightOrDark(ServerLevel level) {
        // 夜晚或雷雨天视为低亮度
        return level.isNight() || level.isThundering();
    }

    @Override
    public List<Component> getEffectDescriptions() {
        return List.of(
                Component.translatable("mct.contract.builder.desc"),
                Component.translatable("mct.contract.builder.detail.1"),
                Component.translatable("mct.contract.placement_interval")
        );
    }
}