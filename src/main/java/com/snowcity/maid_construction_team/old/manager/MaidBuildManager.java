package com.snowcity.maid_construction_team.old.manager;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.snowcity.maid_construction_team.old.config.MaidBuildConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蓝图建造会话控制器
 * 核心职责：统一管理所有蓝图建造任务的生命周期
 *  - 每个任务拥有唯一 sessionId
 *  - 支持：暂停、恢复、取消
 *  - 支持：建造完成后注销会话
 */

public class MaidBuildManager {

    // 加载默认配置
    private static final int SESSION_TIMEOUT_TICKS = MaidBuildConfig.getSessionTimeoutTick();


    // ============================ 核心数据存储 ============================
    /** 会话表：Key=sessionId, Value=会话对象 */
    private static final Map<UUID, BuildSession> BUILD_SESSIONS = new ConcurrentHashMap<>();
    /** 女仆归属表：Key=女仆UUID, Value=sessionId (用于快速查找女仆在哪个任务) */
    private static final Map<UUID, UUID> MAID_BUILD_SESSION = new ConcurrentHashMap<>();
    // ============================ 对外API：会话管理 ============================

    /**
     * 创建一个新的蓝图建造会话
     * @param player 任务发起者
     * @param maid 初始参与的女仆
     * @return 唯一标识 sessionId (创建失败返回 null)
     */
    public static UUID createSession(Player player, EntityMaid maid) {

        // 女仆存活判定
        if (!maid.isAlive()){
            player.sendSystemMessage(Component.literal("§c该女仆已死亡"));
            return null;
        }

        UUID maidId = maid.getUUID();

        // 前置校验：女仆不能同时参与多个任务
        if (MAID_BUILD_SESSION.containsKey(maidId)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c该女仆已在其他建造任务中！"));
            return null;
        }

        // 虽然几乎不能出现相同的UUID，但是万一呢
        UUID sessionId;
        // 循环生成，直到生成一个 Map 里没有的 ID（实际上这个循环只会执行一次）
        do {
            sessionId = UUID.randomUUID();
        } while (BUILD_SESSIONS.containsKey(sessionId)); // 检查是否已存在

        // 创建会话对象
//        BuildSession session = new BuildSession(sessionId, player, maid, player.getUUID());
        BuildSession session = new BuildSession(sessionId, player, player.getUUID(), maid);

        // 存入数据
        BUILD_SESSIONS.put(sessionId, session); // 建筑
        MAID_BUILD_SESSION.put(maidId, sessionId); // 女仆

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a建造任务已创建！\n调试: 建筑会话(ID: " + sessionId.toString().substring(0, 8) + "...)"));
        return sessionId;
    }


    /**
     * 为会话附加自定义数据，用来强转成各个模组的蓝图打印机
     * @param sessionId 会话
     * @param context 自定义蓝图打印对象, 用来强转成各个模组的蓝图打印机
     */
    public static void attachContext(UUID sessionId, Object context){
        if (sessionId == null) return;
        BuildSession session = BUILD_SESSIONS.get(sessionId);
        if (session != null) {
            session.context = context;
        }
    }


    /**
     * 获取建筑会话的共享自定义蓝图打印对象, 用来共享建筑进度
     * @param sessionId 会话
     * @return 自定义蓝图打印对象
     */
    public static Object getContext(UUID sessionId){
        if (sessionId == null) return null;
        BuildSession session = BUILD_SESSIONS.get(sessionId);
        return session == null ? null : session.context;
    }

    // ============================ 女仆管理 ============================

    /**
     * 添加指定女仆到指定会话
     *
     * @param sessionId 目标会话ID
     * @param maid      要加入的女仆
     * @param player    操作的玩家
     */
    public static void addMaidToSession(UUID sessionId, EntityMaid maid, Player player) {
        if (sessionId == null) {
            player.sendSystemMessage(Component.literal("测试: 任务ID无效"));
            return;
        }
        // 女仆存活判定
        if (!maid.isAlive()){
            player.sendSystemMessage(Component.literal("§c该女仆已死亡"));
            return;
        }

        UUID maidId = maid.getUUID();
        BuildSession session = BUILD_SESSIONS.get(sessionId);

        // 校验1：会话是否存在
        if (session == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c任务不存在！"));
            return;
        }

        // 校验2：女仆是否已在其他任务中
        if (MAID_BUILD_SESSION.containsKey(maidId)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c该女仆已在其他任务中！"));
            return;
        }

        // 执行加入
        session.participatingMaids.add(maidId);
        MAID_BUILD_SESSION.put(maidId, sessionId);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a女仆已加入任务！当前人数: " + session.participatingMaids.size()));
    }


    /**
     * 从指定会话中移除指定女仆
     * @param sessionId 目标会话ID
     * @param maid 要加入的女仆
     * @param player 操作的玩家
     */
    public static void removeMaidToSession(UUID sessionId, EntityMaid maid, Player player){
        if (sessionId == null) {
            player.sendSystemMessage(Component.literal("测试: 任务ID无效"));
            return;
        }

        UUID maidId = maid.getUUID();
        BuildSession session = BUILD_SESSIONS.get(sessionId);

        // 会话是否存在
        if (session == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c任务不存在！"));
            return;
        }

        // 执行移除
        session.participatingMaids.remove(maidId);
        MAID_BUILD_SESSION.remove(maidId, sessionId);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a女仆移除该任务！当前人数: " + session.participatingMaids.size()));
    }

    /**
     * 获取女仆参与的会话
     * @param maidId 女仆ID
     */
    public static UUID getMaidSessionId(UUID maidId){
        return MAID_BUILD_SESSION.get(maidId);
    }


    /**
     * 获取指定会话中所有存活的女仆实体（自动遍历世界）
     * @param sessionId 会话ID
     * @return 女仆实体列表
     */
    public static List<EntityMaid> getSessionMaids(UUID sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }

        BuildSession session = BUILD_SESSIONS.get(sessionId);
        if (session == null || session.participatingMaids.isEmpty()) {
            return Collections.emptyList();
        }

        List<EntityMaid> maids = new ArrayList<>();

        // ============================ 【核心】获取服务器实例并遍历所有世界 ============================
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return maids; // 服务器还没启动完，直接返回空
        }

        // 遍历服务器中所有已加载的世界
        for (ServerLevel serverLevel : server.getAllLevels()) {
            for (UUID maidId : session.participatingMaids) {
                Entity entity = serverLevel.getEntity(maidId);

                // 只要活着的女仆实体
                if (entity instanceof EntityMaid maid && maid.isAlive()) {
                    maids.add(maid);
                }
            }
        }
        // ================================================================================================

        return maids;
    }

    // ============================ 会话管理 ============================

    /**
     * 暂停指定会话
     * @param sessionId 会话ID
     * @param player 操作的玩家
     */
    public static void pauseSession(UUID sessionId, Player player) {
        if (sessionId == null) {
            player.sendSystemMessage(Component.literal("会话ID无效"));
            return;
        }
        BuildSession session = BUILD_SESSIONS.get(sessionId);

        // 校验1：会话是否存在
        if (session == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c任务不存在！"));
            return;
        }

        // 校验2: 会话是否已经暂停
        if (session.isPaused) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e任务已经是暂停状态。"));
            return;
        }

        session.isPaused = true;
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e任务已暂停。"));
    }

    /**
     * 恢复（重新开始）指定会话
     * @param sessionId 会话ID
     * @param player 操作的玩家
     */
    public static void resumeSession(UUID sessionId, Player player) {
        if (sessionId == null) {
            player.sendSystemMessage(Component.literal("会话ID无效"));
            return;
        }
        BuildSession session = BUILD_SESSIONS.get(sessionId);
        if (session == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c任务不存在！"));
            return;
        }
        if (!session.isPaused) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a任务正在进行中，无需恢复。"));
            return;
        }

        session.isPaused = false;
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a任务已恢复！"));
    }

    /**
     * 取消指定会话（强制终止）
     * @param sessionId 会话ID
     * @param player 操作的玩家
     */
    public static void cancelSession(UUID sessionId, Player player) {
        if (sessionId == null) {
            player.sendSystemMessage(Component.literal("会话ID无效"));
            return;
        }
        BuildSession session = BUILD_SESSIONS.remove(sessionId);
        if (session == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c任务不存在！"));
            return;
        }

        // 清理女仆归属
        session.participatingMaids.forEach(MAID_BUILD_SESSION::remove);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c任务已取消。"));
    }


    /**
     * 获得当前所有活跃的会话
     * @return Collections
     */
    public static Map<UUID, BuildSession> getAllSessions() {
        return Collections.unmodifiableMap(BUILD_SESSIONS);
    }

    /**
     * 获取指定玩家创建的所有活跃建筑会话的ID列表
     * @param playerUuid 玩家的UUID
     * @return 该玩家的会话ID列表（空列表表示没有会话）
     */
    public static List<UUID> getAllSessionIdsForPlayer(UUID playerUuid) {
        List<UUID> result = new ArrayList<>();

        // 遍历所有会话
        for (Map.Entry<UUID, BuildSession> entry : getAllSessions().entrySet()) {
            UUID sessionId = entry.getKey();
            BuildSession session = entry.getValue();

            // 对比会话的所有者UUID
            // 【注意】请确保BuildSession中有 ownerUUID 字段
            if (session.playerId.equals(playerUuid)) {
                result.add(sessionId);
            }
        }

        return result;
    }

    // ============================ 建造完成回调 ============================

    /**
     * 蓝图建造完成后调用此方法注销会话
     * @param sessionId 完成的会话ID
     */
    public static void completeSession(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        BuildSession session = BUILD_SESSIONS.remove(sessionId);
        if (session != null) {
            // 清理女仆归属
            session.participatingMaids.forEach(MAID_BUILD_SESSION::remove);
            // 通知所有者
            session.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a✅ 建造完成！任务已注销。\n主人快夸我!"));
        }
    }

    // ============================ 状态查询 ============================

    /**
     * 检查女仆是否有任务在身
     */
    public static boolean isMaidBusy(UUID maidId) {
        return MAID_BUILD_SESSION.containsKey(maidId);
    }

    /**
     * 检查会话是否存在且活跃
     */
    public static boolean isSessionActive(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        return BUILD_SESSIONS.containsKey(sessionId);
    }

    /**
     * 检查会话是否处于暂停状态
     */
    public static boolean isSessionPaused(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        BuildSession session = BUILD_SESSIONS.get(sessionId);
        return session != null && session.isPaused;
    }

    /**
     * 获取会话对象（供建造逻辑层使用）
     */
    public static BuildSession getSession(UUID sessionId) {
        if (sessionId == null) {
            return null;
        }
        return BUILD_SESSIONS.get(sessionId);
    }

    // ============================ 内部: 生命周期监控 ============================

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (BUILD_SESSIONS.isEmpty()) return;
        long currentTick = getCurrentServerTick();

        // 清理无效会话
        BUILD_SESSIONS.entrySet().removeIf(entry -> {
            BuildSession session = entry.getValue();
            boolean needRemove = false;

            // 1. 清理死亡/消失的女仆
            session.participatingMaids.removeIf(maidId -> {
                EntityMaid maid = getMaidEntity(maidId); // 需要你根据环境实现此方法
                boolean isDead = maid == null || !maid.isAlive();
                if (isDead) {
                    MAID_BUILD_SESSION.remove(maidId);
                }
                return isDead;
            });

            // 2. 如果没有女仆，自动销毁会话
            if (session.participatingMaids.isEmpty()) {
                needRemove = true;
            }

            // 3.非暂停会话超时自动销毁
            if (!needRemove && !session.isPaused && (currentTick - session.lastActiveTick) > SESSION_TIMEOUT_TICKS){
                needRemove = true;
                session.player.sendSystemMessage(Component.literal("建造任务超时, 已自动注销"));
            }
            return needRemove;
        });
    }

    private static long getCurrentServerTick(){
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? 0 : server.getTickCount();
    }


    /**
     * 通过UUID查找女仆, 支持跨纬度
     * @param maidId 女仆唯一标识
     * @return maid 或者 null
     */
    private static EntityMaid getMaidEntity(UUID maidId) {
        if (maidId == null) return null;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()){
            Entity entity = level.getEntity(maidId);
            if (entity instanceof EntityMaid maid && maid.isAlive()) return maid;
        }
        return null;
    }


    /**
     * 遍历全服所有世界，获取某玩家的所有存活女仆
     * @param playerUuid 玩家UUID
     * @return 全服范围内的女仆列表
     */
    public static List<EntityMaid> getAllPlayerMaids(UUID playerUuid) {
        if (playerUuid == null) {
            return Collections.emptyList();
        }

        List<EntityMaid> maids = new ArrayList<>();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null) {
            return maids;
        }

        // 遍历服务器的所有世界（主世界、地狱、末地、其他维度）
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityMaid maid) {
                    UUID ownerUuid = maid.getOwnerUUID();
                    if (ownerUuid != null && ownerUuid.equals(playerUuid) && maid.isAlive()) {
                        maids.add(maid);
                    }
                }
            }
        }

        return maids;
    }

    // ============================ 数据模型 ============================

    /**
     * 建造会话数据类
     */
    public static class BuildSession {
        public final UUID sessionId;           // 唯一标识
        public final Player player;              // 任务所有者
        public final UUID playerId;              // 任务所有者
        public final Set<UUID> participatingMaids; // 参与的女仆列表

        /**
         * 同一个 sessionId 下的女仆共享一个 context 对象
         */
        public Object context = null; // 通用上下文对象，用来强转为对应模组的蓝图打印机

        public int tickAccumulator = 0;         // Tick 计数器
        public boolean isPaused = false;        // 暂停标记
        public boolean isCancelled = false;     // 取消标记（备用）
        public long lastActiveTick;

        public BuildSession(UUID sessionId, Player player, UUID playerId, EntityMaid initialMaid) {
            this.sessionId = sessionId;
            this.player = player;
            this.playerId = playerId;
            this.participatingMaids = ConcurrentHashMap.newKeySet();
            this.participatingMaids.add(initialMaid.getUUID());
            this.lastActiveTick = getCurrentServerTick(); // 初始化超时事件
        }
    }
}