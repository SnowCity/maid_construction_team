package com.snowcity.maid_construction_team.api.contract;

import com.snowcity.maid_construction_team.api.contract.impl.CollectorEffect;
import com.snowcity.maid_construction_team.component.ServantContractData;
import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class DefaultBonusCollector implements IBonusCollector {

    private final Player player;

    public DefaultBonusCollector(Player player) {
        this.player = player;
    }

    @Override
    public int collectBlocksPerTickBonus(PlacementSession session, ServerLevel level, BlockPos anchor) {
        int total = 0;
        for (UUID contractId : session.getActiveContractIds()) {
            EntityType<?> type = getEntityType(contractId); // 省略查找实现
            if (type != null) {
                IContractEffect effect = ContractRoleRegistry.getEffect(type);
                if (effect != null) total += effect.getBlocksPerTickBonus(type, level, anchor);
            }
        }
        return total;
    }

    @Override
    public int collectIntervalReduction(PlacementSession session, ServerLevel level, BlockPos anchor) {
        int total = 0;
        for (UUID contractId : session.getActiveContractIds()) {
            EntityType<?> type = getEntityType(contractId);
            if (type != null) {
                IContractEffect effect = ContractRoleRegistry.getEffect(type);
                if (effect != null) total += effect.getIntervalReduction(type, level, anchor);
            }
        }
        return total;
    }

    @Override
    public float collectDurabilitySave(PlacementSession session, ServerLevel level, BlockPos anchor) {
        float total = 0f;
        for (UUID contractId : session.getActiveContractIds()) {
            EntityType<?> type = getEntityType(contractId);
            if (type != null) {
                IContractEffect effect = ContractRoleRegistry.getEffect(type);
                if (effect != null) total += effect.getDurabilitySave(type, level, anchor);
            }
        }
        return total;
    }

    // 新增方法
    @Override
    public float collectExtraDropChance(PlacementSession session, ServerLevel level, BlockPos anchor) {
        float totalChance = 0f;
        for (UUID contractId : session.getActiveContractIds()) {
            EntityType<?> type = getEntityType(contractId);
            if (type != null) {
                IContractEffect effect = ContractRoleRegistry.getEffect(type);
                if (effect instanceof CollectorEffect collector) {
                    totalChance += collector.getExtraDropChance(type, level, anchor);
                }
            }
        }
        return Math.min(totalChance, 1.0f);
    }

    private EntityType<?> getEntityType(UUID contractId) {
        ServantContractData data = ContractBonusManager.findContractData(player, contractId);
        if (data == null) return null;
        return BuiltInRegistries.ENTITY_TYPE.get(data.entityType());
    }
}