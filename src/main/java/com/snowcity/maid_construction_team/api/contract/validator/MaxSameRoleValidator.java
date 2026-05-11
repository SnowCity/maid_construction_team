package com.snowcity.maid_construction_team.api.contract.validator;

import com.snowcity.maid_construction_team.api.contract.IContractValidator;
import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class MaxSameRoleValidator implements IContractValidator {
    @Override
    public String canActivate(UUID contractId, PlacementSession session, Player player) {
        // 获取该契约的生物类型（省略查找过程，需从 session 或契约数据中获取）
        // EntityType<?> entityType = ...;
        // int max = ContractRoleRegistry.getMaxActive(entityType);
        // 统计当前会话中已激活的同种生物数量
        // 此处省略具体实现，将在集成契约数据时完成
        return null; // 占位
    }
}