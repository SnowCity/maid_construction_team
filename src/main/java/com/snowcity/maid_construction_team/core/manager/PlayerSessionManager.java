package com.snowcity.maid_construction_team.core.manager;

import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
import com.snowcity.maid_construction_team.core.schematic.*;
import com.snowcity.maid_construction_team.core.schematic.persistence.SessionPersistenceHelper;
import com.snowcity.maid_construction_team.core.schematic.persistence.SimpleBlueprintPersistence;
import com.snowcity.maid_construction_team.core.schematic.provider.ContainerMarkMaterialProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * 全局玩家会话管理器。
 * <p>
 * 负责为每个在线玩家分配专属的 {@link PlacementSessionManager}，
 * 并自动处理玩家退出时（离线）的任务暂停与持久化保存，
 * 以及玩家上线时（重新进入游戏）的任务加载与恢复。
 * <p>
 * 恢复逻辑采用“断点续传”：直接恢复保存时的已完成方块、材料消耗和进度索引，
 * 放置器从断点处继续处理，不再执行全量初始扫描，避免因区块未加载导致的材料错扣。
 */
@EventBusSubscriber(modid = "maid_construction_team")
public class PlayerSessionManager {

    /** 玩家 UUID → 专属的会话管理器实例 */
    private static final Map<UUID, PlacementSessionManager> playerManagers = new HashMap<>();

    /**
     * 获取或创建指定玩家的专属会话管理器。
     *
     * @param player 目标玩家
     * @return 该玩家专属的 {@link PlacementSessionManager}，若不存在则创建新的并返回
     */
    public static PlacementSessionManager of(Player player) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");
        return playerManagers.computeIfAbsent(player.getUUID(), id -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            return new PlacementSessionManager(player, server);
        });
    }

    // ==================== 离线处理 ====================

    /**
     * 玩家退出游戏时，自动暂停其所有未完成的会话，并持久化到磁盘。
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        PlacementSessionManager mgr = playerManagers.get(player.getUUID());
        if (mgr == null) return;

        // 获取玩家数据目录，用于存储持久化文件
        Path serverDir = player.level().getServer().getServerDirectory();
        Path playerDir = serverDir.resolve("maid_building_blueprint").resolve(player.getUUID().toString());
        IBlueprintPersistence persistence = new SimpleBlueprintPersistence();

        // 遍历该玩家的所有会话，将非终态的会话暂停并保存
        for (PlacementSession session : mgr.getActiveSessions()) {
            SessionStateMachine.State state = session.getState();
            if (state == SessionStateMachine.State.RUNNING ||
                    state == SessionStateMachine.State.WAITING_MATERIALS) {
                // 先暂停为离线状态
                mgr.pauseSession(session.getSessionId(), true);
                // 尝试持久化保存
                try {
                    SessionPersistenceHelper.save(session, persistence, playerDir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // ==================== 上线处理 ====================

    /**
     * 玩家加入游戏时，从磁盘加载其上次离线时保存的会话数据，
     * 并强制加载锚点区块后，以“断点续传”方式恢复任务。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 获取玩家数据目录（用于存放持久化文件）
        Path serverDir = Objects.requireNonNull(player.level().getServer()).getServerDirectory();
        Path playerDir = serverDir.resolve("maid_building_blueprint").resolve(player.getUUID().toString());

        try {
            // 从磁盘加载所有离线时保存的会话数据（已加载完成即删除源文件）
            List<SessionPersistenceHelper.LoadedSessionData> loadedSessions =
                    SessionPersistenceHelper.load(playerDir, player);

            if (loadedSessions.isEmpty()) return;

            PlacementSessionManager mgr = of(player);
            MaidConstructionTeamConfig config = MaidConstructionTeamConfig.getInstance();
            ServerLevel serverLevel = player.serverLevel();

            for (SessionPersistenceHelper.LoadedSessionData data : loadedSessions) {
                // ---------- 1. 重建进度，并恢复保存时的已完成方块和材料消耗 ----------
                PlacementProgress progress = new PlacementProgress(
                        data.schematic().getBlocks().size(),
                        data.schematic().getEntities().size()
                );

                // 恢复材料消耗记录（用于UI统计，也作为进度快照）
                for (Map.Entry<Block, Integer> entry : data.materialConsumed().entrySet()) {
                    for (int i = 0; i < entry.getValue(); i++) {
                        progress.recordConsumption(entry.getKey().defaultBlockState());
                    }
                }
                // 恢复缺失方块列表（跳过模式下产生的记录）
                for (BlockPos pos : data.missedBlocks()) {
                    progress.addMissedBlock(pos);
                }
                progress.setPlacedEntityCount(data.placedEntityCount());

                // ★ 关键：将 currentBlockIndex 强制设为 0。
                // 放置器会从头开始遍历蓝图方块，但已完成方块已被标记，
                // 在 tick() 中遇到它们时会直接跳过（匹配检查），既不会重新放置也不会消耗材料。
                // 这样做可以 100% 避免因区块未加载或状态不一致导致的材料误扣。
                progress.setCurrentBlockIndex(0);

                // ---------- 2. 构造材料供应器和放置上下文 ----------
                IMaterialProvider materialProvider = new ContainerMarkMaterialProvider(player, data.participants());
                PlacementContext context = new PlacementContext(
                        data.schematic(),
                        serverLevel,
                        data.anchor(),
                        data.rotation(),
                        player,
                        config,
                        materialProvider,
                        data.participants(),
                        data.blueprintName()
                );

                // ---------- 3. 创建会话并恢复原始会话ID ----------
                PlacementSession session = new PlacementSession(
                        player.getUUID(),
                        data.participants(),
                        progress,
                        data.blueprintName(),
                        data.strategy()
                );
                session.setSessionId(data.sessionId()); // 保留原始UUID，便于规划表追踪

                // ---------- 4. 创建放置器并绑定，但不执行任何扫描 ----------
                ProgressivePlacer placer = new ProgressivePlacer(context, progress, mgr, session.getSessionId());
                session.setPlacer(placer);

                // 设置恢复断点，告诉放置器从哪个索引开始继续处理
                placer.setResumeBlockIndex(data.currentBlockIndex());

                // ---------- 5. 将会话设为手动暂停，等待玩家手动点击“继续” ----------
                // 状态设为 PAUSED 后，会话不会加入工作集，放置器不会被 Tick 驱动。
                // 玩家在规划表中点击“继续”时，会调用 resumeSessionWithRebuild，
                // 该方法只需检查材料/工具状态并恢复运行即可，无需再做额外扫描。
                session.setState(SessionStateMachine.State.PAUSED);
                session.addFeedback(new FeedbackMessage(
                        FeedbackMessage.Type.TASK_PAUSED_MANUALLY,
                        "离线会话已恢复，点击“继续”以启动建造"
                ));

                // 将会话注册到玩家专属的管理器中
                mgr.addSession(session);
            }
        } catch (IOException e) {
            // 持久化文件损坏或读取异常不应影响游戏正常运行，仅记录错误日志
            e.printStackTrace();
        }
    }
}