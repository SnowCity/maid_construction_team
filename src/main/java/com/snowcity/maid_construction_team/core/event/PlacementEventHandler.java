package com.snowcity.maid_construction_team.core.event;

import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import com.snowcity.maid_construction_team.core.schematic.ProgressivePlacer;
import com.snowcity.maid_construction_team.core.manager.SessionStateMachine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.UUID;

/**
 * 蓝图放置的事件驱动层。
 * <p>
 * 负责在每个服务器 Tick 中推动所有活跃的放置任务，
 * 并监听世界中的方块放置事件，自动同步手动放置的、符合蓝图要求的方块。
 */
@EventBusSubscriber(modid = "maid_construction_team")
public class PlacementEventHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 主 Tick 驱动：遍历所有在线玩家的工作集，推动放置器。
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlacementSessionManager manager = PlayerSessionManager.of(player);
            // 驱动 RUNNING 会话
            for (UUID sessionId : manager.getRunningSessionIds()) {
                PlacementSession session = manager.getSession(sessionId);
                if (session == null) continue;
                ProgressivePlacer placer = session.getPlacer();
                if (placer != null) {
                    placer.tick();
                }
            }
            // 尝试恢复 WAITING_MATERIALS 会话
            for (PlacementSession session : manager.getActiveSessions()) {
                if (session.getState() != SessionStateMachine.State.WAITING_MATERIALS) continue;
                ProgressivePlacer placer = session.getPlacer();
                if (placer != null && placer.checkResumeCondition()) {   // 改为纯检查
                    manager.resumeSession(session.getSessionId());
                }
            }
        }
    }

    /**
     * 方块放置事件：检测玩家或生物实体放置的方块是否与某个活跃蓝图匹配，
     * 如果匹配则通知对应放置器进行同步。
     * 监听世界方块变化事件
     * 任何方块发生变化（放置、破坏、替换）都会触发，覆盖面最广。
     */
    @SubscribeEvent
    public static void onBlockChanged(BlockEvent.EntityPlaceEvent event) {
        // 先尝试获取变化的方块
        BlockPos pos = event.getPos();
        BlockState newState = event.getPlacedBlock();

        // 直接遍历在线玩家，找到所有活跃的放置器，让它们自己去判断是否匹配
        var server = event.getLevel().getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlacementSessionManager manager = PlayerSessionManager.of(player);
            Collection<PlacementSession> activeSessions = manager.getActiveSessions();
            for (PlacementSession session : activeSessions) {
                ProgressivePlacer placer = session.getPlacer();
                if (placer != null) {
                    // 让放置器自己去检查是否匹配，并处理
                    LOGGER.info("[DEBUG] Block changed: {} state={}", pos, newState);
                    placer.onBlockChanged(pos, newState);
                }
            }
        }
    }
}