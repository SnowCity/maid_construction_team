package com.snowcity.maid_construction_team.core.schematic;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.UUID;

/**
 * 工具缺失通知器接口。
 * <p>
 * 当工具模拟开启时，若放置器无法从材料供应器找到有效工具，
 * 或在破坏过程中工具损坏，则会调用此接口来通知外部系统。
 * <p>
 * 当前仅定义接口，实现可为空（默认无操作），供未来模拟经营等玩法扩展使用。
 */
public interface IToolLackNotifier {

    /**
     * 当缺少破坏指定方块所需的工具时触发。
     *
     * @param sessionId         发生工具缺失的会话 ID
     * @param targetState       需要被破坏的方块状态
     * @param participantUuids  当前会话的所有参与者 UUID 集合
     * @param toolDescription   缺少的工具描述（如“石镐”或“任意镐”）
     */
    void onToolMissing(UUID sessionId, BlockState targetState, Set<UUID> participantUuids, String toolDescription);
}