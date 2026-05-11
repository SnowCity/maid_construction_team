package com.snowcity.maid_construction_team.api.contract.impl;

import com.snowcity.maid_construction_team.api.contract.AutoContractEffect;
import com.snowcity.maid_construction_team.api.contract.IContractEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

import java.util.List;

@AutoContractEffect(assignTo = {"minecraft:creeper", "minecraft:slime"}, maxActive = 4)
public class MaintenanceEffect implements IContractEffect {

    @Override
    public String getRoleId() {
        return "maintenance";
    }

    @Override
    public int getBlocksPerTickBonus(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        return 0;
    }

    @Override
    public int getIntervalReduction(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        return 0;
    }

    @Override
    public float getDurabilitySave(EntityType<?> entityType, ServerLevel level, BlockPos anchor) {
        float base = 0.2f; // 20%
        // 苦力怕在雷雨下翻倍
        if (entityType == EntityType.CREEPER && level.isThundering()) {
            base = 0.4f;
        }
        return base;
    }

    @Override
    public List<Component> getEffectDescriptions() {
        return List.of(
                Component.literal("§6◆ 养护工 §r(耐久节省 20%)"),
                Component.literal("§7  - 苦力怕在雷雨时节省提升至 40%"),
                Component.literal("§7  - 不影响放置速度")
        );
    }
}