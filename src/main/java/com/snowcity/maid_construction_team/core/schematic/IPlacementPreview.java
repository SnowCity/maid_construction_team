package com.snowcity.maid_construction_team.core.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;

import java.util.Set;
import java.util.UUID;

/**
 * 蓝图放置预览接口。
 * <p>
 * 用于在客户端渲染蓝图的半透明预览，帮助玩家在确认放置前调整位置和方向。
 * 当前为预留扩展点，所有方法均为空实现，未来可接入全息渲染。
 */
public interface IPlacementPreview {

    /**
     * 开始显示蓝图预览。
     *
     * @param schematicData 蓝图数据
     * @param level         目标世界
     * @param anchor        放置锚点
     * @param rotation      旋转方向
     * @param players       需要查看预览的玩家集合
     */
    default void startPreview(SchematicData schematicData, ServerLevel level, BlockPos anchor,
                              Rotation rotation, Set<UUID> players) {
        // 默认不执行任何操作，由未来实现
    }

    /**
     * 停止并移除蓝图预览。
     */
    default void stopPreview() {
        // 默认不执行任何操作
    }
}