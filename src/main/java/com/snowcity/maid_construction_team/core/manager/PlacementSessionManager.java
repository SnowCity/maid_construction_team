package com.snowcity.maid_construction_team.core.manager;

import com.snowcity.maid_construction_team.api.contract.ContractBonusManager;
import com.snowcity.maid_construction_team.api.labor.ILaborProvider;
import com.snowcity.maid_construction_team.api.labor.LaborProviderRegistry;
import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
import com.snowcity.maid_construction_team.core.schematic.*;
import com.snowcity.maid_construction_team.core.schematic.persistence.SessionPersistenceHelper;
import com.snowcity.maid_construction_team.network.payload.session.SessionStateChangedPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.nio.file.Path;
import java.util.*;

/**
 * 玩家专属的会话管理器。
 * <p>
 * 负责为一个玩家创建、取消、暂停、恢复及查询 {@link PlacementSession}。
 * 内部维护一个工作集（仅包含 RUNNING 状态的任务），以便 Tick 高效遍历。
 * <p>
 * 当会话状态发生变更（完成、暂停、恢复等）时，会向玩家客户端发送
 * {@link SessionStateChangedPayload} 通知，使规划表等 UI 能及时刷新。
 */
public class PlacementSessionManager {

    /** 所有会话，key = 会话 ID */
    private final Map<UUID, PlacementSession> sessions = new HashMap<>();
    /** 工作集：仅存储当前处于 RUNNING 状态的会话 ID */
    private final Set<UUID> workSet = new HashSet<>();
    /** 关联的玩家，用于发送网络通知 */
    private final Player player;

    private final MinecraftServer server;

    /**
     * 构造一个玩家专属的会话管理器。
     *
     * @param player 所属玩家，不能为 null
     */
    public PlacementSessionManager(Player player, MinecraftServer server) {
        this.player = Objects.requireNonNull(player, "player cannot be null");
        this.server = server;
    }

    // ---------- 生命周期 --------

    /**
     * 创建一个新的放置会话，并将其置为 RUNNING 状态并加入工作集。
     * 会话的材料不足策略默认从全局配置中读取。
     *
     * @param context 放置上下文，包含蓝图、配置、参与者等
     * @return 新会话的唯一标识符
     */
    public UUID startSession(PlacementContext context) {
        PlacementProgress progress = new PlacementProgress(
                context.schematicData().getBlockCount(),
                context.schematicData().getEntityCount()
        );
        String blueprintName = context.blueprintName();
        if (blueprintName == null || blueprintName.isEmpty()) {
            blueprintName = "未命名蓝图";
        }
        MaterialShortageStrategy strategy = MaidConstructionTeamConfig.getInstance().getMaterialShortageStrategy();

        // 参与者集合可以传入空集，因为劳动力将单独派遣
        PlacementSession session = new PlacementSession(
                context.player().getUUID(),
                Collections.emptySet(),   // 玩家不自动参与
                progress,
                blueprintName,
                strategy
        );
        ProgressivePlacer placer = new ProgressivePlacer(context, progress, this, session.getSessionId());
        session.setPlacer(placer);
        session.addFeedback(new FeedbackMessage(FeedbackMessage.Type.TASK_STARTED, "放置任务已启动"));
        sessions.put(session.getSessionId(), session);
        workSet.add(session.getSessionId());
        return session.getSessionId();
    }

    /**
     * 将一个已存在的会话添加到管理器中（通常用于恢复持久化的会话）。
     */
    public void addSession(PlacementSession session) {
        sessions.put(session.getSessionId(), session);
        // 如果会话状态为 RUNNING，也加入工作集
        if (session.getState() == SessionStateMachine.State.RUNNING) {
            workSet.add(session.getSessionId());
        }
    }

    /**
     * 取消一个正在运行的会话（非终态均可）。
     * 已放置的方块保留，不提供回滚。
     *
     * @param sessionId 会话 ID
     * @return 是否成功取消
     */
    public boolean cancelSession(UUID sessionId) {
        PlacementSession session = sessions.get(sessionId);
        if (session == null) return false;
        // 终态不可取消
        if (session.getState() == SessionStateMachine.State.COMPLETED ||
                session.getState() == SessionStateMachine.State.CANCELLED) {
            return false;
        }
        session.setState(SessionStateMachine.State.CANCELLED);
        session.addFeedback(new FeedbackMessage(FeedbackMessage.Type.TASK_CANCELLED, "任务已取消"));
        workSet.remove(sessionId);
        sessions.remove(sessionId);

        // 清理持久化文件
        deletePersistentData(sessionId);

        //  自动召回所有劳动力
        ServerPlayer creator = server.getPlayerList().getPlayer(session.getCreatorUuid());
        if (creator != null) {
            Set<UUID> participants = session.getParticipantUuids();
            for (UUID laborId : participants) {
                for (ILaborProvider provider : LaborProviderRegistry.getEnabledProviders()) {
                    provider.recall(laborId, creator);
                }
            }
        }

        // 发送状态变更通知
        notifyStateChange(sessionId, SessionStateMachine.State.CANCELLED);
        return true;
    }

    /**
     * 删除该会话的持久化文件夹（如果存在）。
     */
    private void deletePersistentData(UUID sessionId) {
        try {
            Path serverDir = player.level().getServer().getServerDirectory();
            Path blueprintRootDir = serverDir.resolve("maid_building_blueprint").resolve(player.getUUID().toString());
            SessionPersistenceHelper.delete(sessionId, blueprintRootDir);
        } catch (Exception e) {
            // 删除失败不应影响核心功能，仅记录日志
            e.printStackTrace();
        }
    }

    // ---------- 任务完成通知 ----------

    /**
     * 由放置器在成功完成全部放置任务后调用，将会话状态转换为 COMPLETED，
     * 并从工作集和活跃会话中移除。
     */
    public void completeSession(UUID sessionId) {
        PlacementSession session = sessions.get(sessionId);
        if (session == null || session.getState() != SessionStateMachine.State.RUNNING) {
            return;
        }

        // ===== 契约清理开始 =====
        ServerPlayer player = server.getPlayerList().getPlayer(session.getCreatorUuid());
        if (player != null) {
            for (UUID contractId : session.getActiveContractIds()) {
                ContractBonusManager.clearContractState(contractId, player);
            }
        }
        // 清空会话内的契约ID列表
        session.clearContractBonuses();
        // ===== 契约清理结束 =====

        session.setState(SessionStateMachine.State.COMPLETED);
        session.addFeedback(new FeedbackMessage(FeedbackMessage.Type.TASK_COMPLETED, "放置已完成"));

        // 从工作集和会话集合中移除
        workSet.remove(sessionId);
        sessions.remove(sessionId);

        // 清理持久化文件
        deletePersistentData(sessionId);

        //  自动召回所有参与此会话的劳动力
        ServerPlayer creator = server.getPlayerList().getPlayer(session.getCreatorUuid());
        if (creator != null) {
            Set<UUID> participants = session.getParticipantUuids();
            for (UUID laborId : participants) {
                for (ILaborProvider provider : LaborProviderRegistry.getEnabledProviders()) {
                    provider.recall(laborId, creator);
                }
            }
        }

        // 发送状态变更通知
        notifyStateChange(sessionId, SessionStateMachine.State.COMPLETED);
    }

    // ---------- 手动暂停/恢复 ----------

    /**
     * 玩家手动暂停任务。只允许当前状态为 RUNNING 或 WAITING_MATERIALS 时操作。
     */
    public boolean pauseSessionManually(UUID sessionId) {
        PlacementSession session = sessions.get(sessionId);
        if (session == null) return false;
        var state = session.getState();
        if (state != SessionStateMachine.State.RUNNING &&
                state != SessionStateMachine.State.WAITING_MATERIALS) {
            return false;
        }
        session.setState(SessionStateMachine.State.PAUSED);
        session.addFeedback(new FeedbackMessage(FeedbackMessage.Type.TASK_PAUSED_MANUALLY, "任务已手动暂停"));
        workSet.remove(sessionId);
        notifyStateChange(sessionId, SessionStateMachine.State.PAUSED);
        return true;
    }

    /**
     * 尝试恢复任务。适用于所有非终态的暂停状态。
     * 若状态为 PAUSED 或 PAUSED_OFFLINE（上线恢复）或 WAITING_MATERIALS（材料已足），
     * 都将转为 RUNNING 并加入工作集。
     */
    public boolean resumeSession(UUID sessionId) {
        PlacementSession session = sessions.get(sessionId);
        if (session == null) return false;
        var state = session.getState();
        // 允许从任何可恢复状态恢复：WAITING_MATERIALS、PAUSED_OFFLINE、PAUSED
        if (state == SessionStateMachine.State.WAITING_MATERIALS ||
                state == SessionStateMachine.State.PAUSED_OFFLINE ||
                state == SessionStateMachine.State.PAUSED) {
            session.setState(SessionStateMachine.State.RUNNING);
            workSet.add(sessionId);
            session.getProgress().clearLackMaterialInfo();
            session.addFeedback(new FeedbackMessage(FeedbackMessage.Type.TASK_RESUMED, "任务已恢复"));
            notifyStateChange(sessionId, SessionStateMachine.State.RUNNING);
            return true;
        }
        return false;
    }

    // ---------- 自动暂停（离线和材料不足） ----------

    /**
     * 通用暂停方法，用于离线和材料不足场景。
     *
     * @param sessionId 会话 ID
     * @param offline   是否因玩家离线（true = PAUSED_OFFLINE，false = WAITING_MATERIALS）
     */
    public void pauseSession(UUID sessionId, boolean offline) {
        PlacementSession session = sessions.get(sessionId);
        if (session == null) return;
        var current = session.getState();
        if (current != SessionStateMachine.State.RUNNING &&
                current != SessionStateMachine.State.WAITING_MATERIALS) {
            return;
        }
        SessionStateMachine.State target = offline ? SessionStateMachine.State.PAUSED_OFFLINE : SessionStateMachine.State.WAITING_MATERIALS;
        session.setState(target);
        workSet.remove(sessionId);
        if (offline) {
            session.addFeedback(new FeedbackMessage(FeedbackMessage.Type.TASK_PAUSED_OFFLINE, "玩家离线，任务暂停"));
        }
        notifyStateChange(sessionId, target);
    }

    /**
     * 材料不足时由放置器调用，将会话转入 WAITING_MATERIALS 状态。
     */
    public void transitionToWaitingMaterials(UUID sessionId, String lackInfo) {
        PlacementSession session = sessions.get(sessionId);
        if (session == null || session.getState() != SessionStateMachine.State.RUNNING) return;
        session.setState(SessionStateMachine.State.WAITING_MATERIALS);
        session.getProgress().setLackMaterialInfo(lackInfo);
        session.addFeedback(new FeedbackMessage(FeedbackMessage.Type.MATERIAL_LACK, lackInfo));
        workSet.remove(sessionId);
        notifyStateChange(sessionId, SessionStateMachine.State.WAITING_MATERIALS);
    }

    // ---------- 参与者管理 ----------

    /**
     * 向指定会话添加一个参与者。只有会话创建者本人才能执行此操作。
     *
     * @param sessionId  目标会话 ID
     * @param entityUuid 要添加的实体 UUID
     * @param requester  请求添加的玩家（必须是会话创建者）
     * @return true 表示添加成功，false 表示无权限或会话不存在
     */
    public boolean addParticipant(UUID sessionId, UUID entityUuid, Player requester) {
        PlacementSession session = sessions.get(sessionId);
        if (session == null) return false;
        if (!session.getCreatorUuid().equals(requester.getUUID())) return false;
        boolean added = session.addParticipant(entityUuid);
        if (added) {
            session.addFeedback(new FeedbackMessage(FeedbackMessage.Type.TASK_STARTED,
                    "新增参与者: " + entityUuid.toString()));
        }
        return added;
    }

    /**
     * 从指定会话移除一个参与者。只有会话创建者本人才能执行此操作。
     *
     * @param sessionId  目标会话 ID
     * @param entityUuid 要移除的实体 UUID
     * @param requester  请求移除的玩家（必须是会话创建者）
     * @return true 表示移除成功，false 表示无权限、会话不存在或尝试移除创建者
     */
    public boolean removeParticipant(UUID sessionId, UUID entityUuid, Player requester) {
        PlacementSession session = sessions.get(sessionId);
        if (session == null) return false;
        if (!session.getCreatorUuid().equals(requester.getUUID())) return false;
        boolean removed = session.removeParticipant(entityUuid);
        if (removed) {
            session.addFeedback(new FeedbackMessage(FeedbackMessage.Type.TASK_STARTED,
                    "移除参与者: " + entityUuid.toString()));
        }
        return removed;
    }

    // ---------- 查询 ----------

    /**
     * 根据会话 ID 查找会话。
     *
     * @param sessionId 会话 ID
     * @return 会话对象，若不存在返回 null
     */
    public PlacementSession getSession(UUID sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 获取该玩家当前所有活跃会话（包含暂停、等待等非终态）。
     *
     * @return 不可变集合
     */
    public Collection<PlacementSession> getActiveSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    /**
     * 获取当前处于 RUNNING 状态的会话 ID 集合（工作集）。
     *
     * @return 不可变集合，供 Tick 驱动使用
     */
    public Set<UUID> getRunningSessionIds() {
        return Collections.unmodifiableSet(workSet);
    }

    // ---------- 内部辅助 ----------

    /**
     * 向玩家客户端发送会话状态变更通知。
     *
     * @param sessionId 变更的会话 ID
     * @param newState  新的会话状态
     */
    private void notifyStateChange(UUID sessionId, SessionStateMachine.State newState) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new SessionStateChangedPayload(sessionId, newState));
        }
    }
}