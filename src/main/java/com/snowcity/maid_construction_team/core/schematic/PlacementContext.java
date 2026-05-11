package com.snowcity.maid_construction_team.core.schematic;

import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;

import java.util.Set;
import java.util.UUID;

/**
 * 封装一次蓝图放置任务所需的全部静态信息。
 * <p>
 * 该记录在创建放置会话时被传递给 {@link PlacementSessionManager#startSession(PlacementContext)}，
 * 其中包含蓝图数据、目标世界、放置位置与旋转、配置、材料供应器以及参与者等信息。
 * <p>
 * 所有字段在会话生命周期内不会改变，放置器通过此上下文获取运行时所需的环境。
 *
 * @param schematicData    已解析的蓝图数据，包含方块与实体列表
 * @param targetLevel      蓝图将被放置的目标世界
 * @param anchor           放置锚点，蓝图内部坐标 (0,0,0) 将映射到此世界坐标
 * @param rotation         蓝图旋转方向，方块与实体会相应旋转
 * @param player           执行放置的玩家，用于破坏模拟和通知
 * @param config           全局配置（实时读取，支持热更新）
 * @param materialProvider 材料供应器，用于消耗、回收和工具借用
 * @param participants     参与此次放置的生物实体 UUID 集合（至少包含玩家自身）
 * @param blueprintName    蓝图显示名称（如文件名），用于界面标识
 */
public record PlacementContext(
        SchematicData schematicData,
        ServerLevel targetLevel,
        BlockPos anchor,
        Rotation rotation,
        Player player,
        MaidConstructionTeamConfig config,
        IMaterialProvider materialProvider,
        Set<UUID> participants,
        String blueprintName
) {
}