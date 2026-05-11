package com.snowcity.maid_construction_team.api.contract.impl;

import com.snowcity.maid_construction_team.api.contract.AutoContractEffect;
import com.snowcity.maid_construction_team.api.contract.IContractEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

import java.util.List;

@AutoContractEffect(assignTo = {"minecraft:skeleton", "minecraft:stray"})
public class EngineerEffect implements IContractEffect {

    @Override
    public String getRoleId() {
        return "engineer";
    }

    @Override
    public int getBlocksPerTickBonus(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        return 0; // 工程师不增加方块数
    }

    @Override
    public int getIntervalReduction(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        int base = 3;
        // 骷髅在低亮度（夜晚或雷雨或露天低亮度）额外减少2刻
        if (entityType == EntityType.SKELETON && isLowLight(level, anchor)) {
            base += 2;
        }
        return base;
    }

    @Override
    public float getDurabilitySave(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        return 0f;
    }

    private boolean isLowLight(ServerLevel level, BlockPos anchor) {
        // 简单判断：夜晚、雷雨，或者锚点处天空亮度 <= 7
        if (level.isNight() || level.isThundering()) return true;
        int skyLight = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, anchor);
        return skyLight <= 7;
    }

    @Override
    public List<Component> getEffectDescriptions() {
        return List.of(
                Component.literal("§6◆ 工程师 §r(间隔 -3 刻)"),
                Component.literal("§7  - 骷髅在低亮度下额外 -2 刻 (合计 -5)"),
                Component.literal("§7  - 不影响单次放置数量")
        );
    }
}