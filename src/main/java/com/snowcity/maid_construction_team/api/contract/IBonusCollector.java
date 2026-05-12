package com.snowcity.maid_construction_team.api.contract;

import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface IBonusCollector {
    int collectBlocksPerTickBonus(PlacementSession session, ServerLevel level, BlockPos anchor);
    int collectIntervalReduction(PlacementSession session, ServerLevel level, BlockPos anchor);
    float collectDurabilitySave(PlacementSession session, ServerLevel level, BlockPos anchor);
    float collectExtraDropChance(PlacementSession session, ServerLevel level, BlockPos anchor);
}