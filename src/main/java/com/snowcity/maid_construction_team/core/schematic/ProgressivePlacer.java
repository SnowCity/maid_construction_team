package com.snowcity.maid_construction_team.core.schematic;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.snowcity.maid_construction_team.api.contract.DefaultBonusCollector;
import com.snowcity.maid_construction_team.api.contract.IBonusCollector;
import com.snowcity.maid_construction_team.api.contract.IContractValidator;
import com.snowcity.maid_construction_team.api.contract.validator.MaxSameRoleValidator;
import com.snowcity.maid_construction_team.core.manager.SessionStateMachine;
import com.snowcity.maid_construction_team.core.util.MaidWorkAnimationHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import com.snowcity.maid_construction_team.network.payload.session.SessionStateChangedPayload;
import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
import com.snowcity.maid_construction_team.item.MaidConstructionTeamItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 渐进式蓝图放置器。
 * <p>
 * 负责将 {@link SchematicData} 中的方块和实体逐步放置到游戏世界。
 * 集成了材料消耗、工具模拟、匹配跳过、断点恢复、劳动力动画等功能。
 */
public class ProgressivePlacer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressivePlacer.class);

    // ---------- 放置上下文 ----------
    private final SchematicData schematicData;
    private final ServerLevel targetLevel;
    private final BlockPos anchor;
    private final Rotation rotation;
    private final MaidConstructionTeamConfig config;
    private final IMaterialProvider materialProvider;
    private final Player player;
    //    private final Set<UUID> participantUuids;
    private final PlacementProgress progress;

    // ---------- 内部状态 ----------
    private int currentBlockIndex;
    private final List<BlockEntityInfo> pendingBlockEntities = new ArrayList<>();
    private boolean entitiesPlaced = false;
    private int tickCounter = 0;

    // ---------- 管理器回调 ----------
    private final PlacementSessionManager sessionManager;
    private final UUID sessionId;

    // ---------- 最近一次加成计算结果（供命令查询） ----------
    private int lastLaborerCount = 0;
    private int lastContractBlocks = 0;
    private int lastContractInterval = 0;
    private int lastEffectiveBlocksPerTick = 0;
    private int lastActualInterval = 0;

    private float lastDurabilitySave = 0f;

    /**
     * 当前正在处理的世界坐标
     */
    private BlockPos currentWorldPos;

    // ---------- 断点恢复 ----------
    private int resumeBlockIndex = 0;

    // ---------- 已处理方块坐标集合（用于运行时去重） ----------
    private final Set<BlockPos> processedBlocks = new HashSet<>();


    private final IBonusCollector bonusCollector;
    private final IContractValidator validator = new MaxSameRoleValidator(); // 暂未使用

    /**
     * 设置恢复断点，由 PlayerSessionManager 在恢复会话时调用。
     */
    public void setResumeBlockIndex(int index) {
        this.resumeBlockIndex = index;
    }

    public BlockPos getAnchor() {
        return anchor;
    }

    public Rotation getRotation() {
        return rotation;
    }

    // ---------- 暂停/恢复状态 ----------
    @Nullable
    private BlockState pendingBlockState;
    private boolean waitingForTool;

    // ---------- 扩展点 ----------
    @Nullable
    private IToolLackNotifier toolLackNotifier;

    public ProgressivePlacer(PlacementContext context, PlacementProgress progress,
                             PlacementSessionManager sessionManager, UUID sessionId) {
        this.schematicData = context.schematicData();
        this.targetLevel = context.targetLevel();
        this.anchor = context.anchor();
        this.rotation = context.rotation();
        this.config = context.config();
        this.materialProvider = context.materialProvider();
        this.player = context.player();
        this.bonusCollector = new DefaultBonusCollector(this.player);
        this.progress = progress;
        this.sessionManager = sessionManager;
        this.sessionId = sessionId;
        this.currentBlockIndex = 0;
        this.toolLackNotifier = null;
        this.pendingBlockState = null;
        this.waitingForTool = false;
    }

    public void setToolLackNotifier(@Nullable IToolLackNotifier notifier) {
        this.toolLackNotifier = notifier;
    }

    // ==================== 每 Tick 执行 ====================

    /**
     * 每 Tick 执行一次，处理本 Tick 的放置任务。
     * 由 {@link PlacementSessionManager} 的工作集驱动。
     */
    public void tick() {
        // 1. 放置间隔控制
        tickCounter++;
        if (tickCounter < config.getPlacementInterval()) {
            return;
        }
        tickCounter = 0;

        // 2. 获取当前会话（用于查询加成契约列表）
        PlacementSession session = sessionManager.getSession(sessionId);
        if (session == null) return; // 会话已失效，不再处理

        // 3. 计算劳动力数量（仅宠物和自定义劳动力）
        int laborerCount = countLaborers();
        if (laborerCount == 0) {
            // 无劳动力，暂停建造（WAITING_MATERIALS 或 PAUSED）
            sessionManager.pauseSession(sessionId, false); // false 表示非离线，会转为 WAITING_MATERIALS
            return;
        }

        // 4. 从加成计算器获取契约加成汇总
        int contractBlocks = bonusCollector.collectBlocksPerTickBonus(session, targetLevel, anchor);
        int contractInterval = bonusCollector.collectIntervalReduction(session, targetLevel, anchor);

        // 获取耐久节省系数
        float durabilitySave = bonusCollector.collectDurabilitySave(session, targetLevel, anchor);

        // 5. 计算实际间隔和单次放置数量
        int effectiveBlocksPerTick = laborerCount * config.getLaborerBonus() + contractBlocks;
        int actualInterval = Math.max(1, config.getPlacementInterval() - contractInterval);

        // ===== 保存最近一次结果 =====
        this.lastLaborerCount = laborerCount;
        this.lastContractBlocks = contractBlocks;
        this.lastContractInterval = contractInterval;
        this.lastEffectiveBlocksPerTick = effectiveBlocksPerTick;
        this.lastActualInterval = actualInterval;
        this.lastDurabilitySave = durabilitySave;

        // 6. 分批处理方块
        for (int i = 0; i < effectiveBlocksPerTick; i++) {
            if (currentBlockIndex >= schematicData.getBlocks().size()) break;

            BlockInfo blockInfo = schematicData.getBlocks().get(currentBlockIndex);
            currentWorldPos = transformBlockPos(blockInfo.pos());
            BlockState rotatedState = blockInfo.state().rotate(targetLevel, currentWorldPos, rotation);
            BlockState originalState = targetLevel.getBlockState(currentWorldPos);

            // 阶段 0：断点强制跳过
            if (currentBlockIndex < resumeBlockIndex) {
                currentBlockIndex++;
                progress.advanceBlock();
                continue;
            }

            // 阶段 1：劳动力动画 (仅宠物等实体劳动力)
            animateLaborers(currentWorldPos);

            // 阶段 2：匹配检查
            if (rotatedState.equals(originalState)) {
                currentBlockIndex++;
                progress.advanceBlock();
                continue;
            }

            // 阶段 A：清空原有方块
            boolean shouldRemove = config.isClearOriginalBlocks() || !rotatedState.isAir();
            if (shouldRemove && !originalState.isAir()) {
                if (!(blockInfo == schematicData.getBlocks().get(0) && currentWorldPos.equals(anchor))) {
                    boolean removed = removeOriginalBlock(currentWorldPos, originalState);
                    if (!removed) return; // 工具缺失或损坏，暂停任务
                }
            }

            // 阶段 B：放置新方块
            boolean shouldPlace = config.isPlaceAir() || !rotatedState.isAir();
            if (!shouldPlace) {
                currentBlockIndex++;
                progress.advanceBlock();
                continue;
            }

            if (config.isConsumeMaterials()) {
                if (!materialProvider.tryConsume(rotatedState)) {
                    MaterialShortageStrategy strategy = getCurrentStrategy();
                    if (strategy == MaterialShortageStrategy.PAUSE) {
                        pendingBlockState = rotatedState;
                        waitingForTool = false;
                        String lackInfo = "缺少材料: " + rotatedState.getBlock().getName().getString();
                        sessionManager.transitionToWaitingMaterials(sessionId, lackInfo);
                        return;
                    } else {
                        progress.addMissedBlock(currentWorldPos);
                        currentBlockIndex++;
                        progress.advanceBlock();
                        continue;
                    }
                }
            }

            int flags = config.isUpdateBlocks() ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS;
            targetLevel.setBlock(currentWorldPos, rotatedState, flags);

            if (blockInfo.hasBlockEntity()) {
                CompoundTag beTag = blockInfo.blockEntityData();
                if (beTag != null) pendingBlockEntities.add(new BlockEntityInfo(currentWorldPos, beTag.copy()));
            }

            progress.advanceBlock();
            currentBlockIndex++;
        }


        // 7. 所有方块处理完毕，完成放置
        if (currentBlockIndex >= schematicData.getBlocks().size()) {
            finishPlacement();
        }
    }

    // ==================== 完成阶段 ====================

    private void finishPlacement() {
        for (BlockEntityInfo info : pendingBlockEntities) {
            BlockEntity be = targetLevel.getBlockEntity(info.pos());
            if (be == null) continue;
            try {
                be.loadWithComponents(info.tag(), targetLevel.registryAccess());
                be.setChanged();
            } catch (Exception e) {
                LOGGER.warn("加载方块实体失败 {}: {}", info.pos(), e.getMessage());
            }
        }
        pendingBlockEntities.clear();

        if (!entitiesPlaced) {
            placeEntities();
            entitiesPlaced = true;
        }

        sessionManager.completeSession(sessionId);
    }

    private void placeEntities() {
        for (EntityInfo entityInfo : schematicData.getEntities()) {
            try {
                Vec3 worldPos = transformEntityVec(entityInfo.pos());
                CompoundTag entityData = entityInfo.entityData().copy();
                entityData.putDouble("x", worldPos.x);
                entityData.putDouble("y", worldPos.y);
                entityData.putDouble("z", worldPos.z);
                Entity entity = EntityType.loadEntityRecursive(entityData, targetLevel, (e) -> e);
                if (entity != null) targetLevel.addFreshEntity(entity);
            } catch (Exception e) {
                LOGGER.warn("放置实体失败: {}", e.getMessage());
            }
        }
    }

    // ==================== 清空原有方块 ====================

    private boolean removeOriginalBlock(BlockPos pos, BlockState originalState) {
        if (hasMoriyaIronRing()) {
            targetLevel.removeBlock(pos, false);
            return true;
        }
        if (config.isEnableToolSimulation()) {
            return simulateBlockBreak(pos, originalState);
        } else {
            targetLevel.removeBlock(pos, false);
            return true;
        }
    }

    /**
     * 模拟使用工具破坏方块，处理耐久、掉落回收和暂停。
     */
    private boolean simulateBlockBreak(BlockPos pos, BlockState originalState) {
        // 1. 借出工具
        ItemStack borrowedTool = materialProvider.findTool(originalState);
        if (borrowedTool.isEmpty()) {
            pendingBlockState = originalState;
            waitingForTool = true;
            String desc = "缺少工具：可破坏 " + originalState.getBlock().getName().getString() + " 的工具";
            notifyToolMissing(originalState, desc);
            sessionManager.transitionToWaitingMaterials(sessionId, desc);
            return false;
        }

        // 2. 计算掉落物
        List<ItemStack> drops = getDropsForState(originalState, pos, borrowedTool);

        // 采集工加成需重新获取，我们通过 bonusCollector 重新计算
        float extraDropChance = lastDurabilitySave;
        // 获取采集工额外掉落概率（可放在 animateLaborers 之后或单独计算）
        float collectorChance = bonusCollector.collectExtraDropChance(sessionManager.getSession(sessionId), targetLevel, anchor);
        if (collectorChance > 0 && Math.random() < collectorChance) {
            // 再获取一次掉落物并添加到列表
            List<ItemStack> extraDrops = getDropsForState(originalState, pos, borrowedTool);
            drops.addAll(extraDrops);
        }

        // 3. 扣除工具耐久
        boolean toolBroken = false;
        if (borrowedTool.isDamageableItem()) {
            // 根据耐久节省系数决定本次是否消耗耐久
            float saveChance = lastDurabilitySave; // 0.0~1.0，例如 0.2 表示 20% 几率不消耗
            boolean consumeDurability = Math.random() >= saveChance;
            if (consumeDurability) {
                int newDamage = borrowedTool.getDamageValue() + 1;
                borrowedTool.setDamageValue(newDamage);
                if (newDamage >= borrowedTool.getMaxDamage()) toolBroken = true;
            }
        }

        // 4. 移除方块
        targetLevel.removeBlock(pos, false);

        // 5. 优先归还工具
        if (toolBroken) {
            pendingBlockState = originalState;
            waitingForTool = true;
            String desc = "工具损坏：" + borrowedTool.getDisplayName().getString();
            notifyToolMissing(originalState, desc);
            sessionManager.transitionToWaitingMaterials(sessionId, desc);
            return false;
        } else {
            materialProvider.returnTool(borrowedTool);
        }

        // 6. 回收掉落物
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                boolean deposited = materialProvider.deposit(drop.copy());
                if (!deposited) {
                    targetLevel.addFreshEntity(new ItemEntity(targetLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop));
                }
            }
        }

        return true;
    }

    private List<ItemStack> getDropsForState(BlockState state, BlockPos pos, ItemStack tool) {
        LootParams.Builder builder = new LootParams.Builder(targetLevel)
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.TOOL, tool)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.BLOCK_STATE, state);
        LootParams lootParams = builder.create(LootContextParamSets.BLOCK);
        ResourceKey<LootTable> lootTableKey = state.getBlock().getLootTable();
        LootTable lootTable = targetLevel.getServer().reloadableRegistries().getLootTable(lootTableKey);
        return lootTable.getRandomItems(lootParams);
    }

    public SchematicData getSchematicData() {
        return schematicData;
    }

    // ==================== 暂停/恢复 ====================

    public boolean checkResumeCondition() {
        if (pendingBlockState == null) return true;
        if (waitingForTool) {
            ItemStack tool = materialProvider.findTool(pendingBlockState);
            if (!tool.isEmpty()) {
                materialProvider.returnTool(tool);
                clearPending();
                return true;
            }
        } else {
            int stock = materialProvider.getStock(pendingBlockState);
            if (stock > 0) {
                clearPending();
                return true;
            }
        }
        return false;
    }

    private void clearPending() {
        pendingBlockState = null;
        waitingForTool = false;
    }

    private void notifyToolMissing(BlockState targetState, String description) {
        if (toolLackNotifier != null) {
            toolLackNotifier.onToolMissing(sessionId, targetState, getActiveParticipants(), description);
        }
    }

    // ==================== 外部方块变化同步 ====================

    public void onBlockChanged(BlockPos worldPos, BlockState actualState) {
        for (BlockInfo info : schematicData.getBlocks()) {
            BlockPos expectedWorldPos = transformBlockPos(info.pos());
            if (!expectedWorldPos.equals(worldPos)) continue;

            BlockState requiredState = info.state().rotate(targetLevel, worldPos, rotation);
            if (!requiredState.equals(actualState)) break;

            // 防重复计数：只有新坐标才记录
            if (processedBlocks.add(worldPos)) {
                progress.recordConsumption(requiredState);
                progress.incrementCompletedBlockCount();
            }
            progress.removeMissedBlock(worldPos);

            if (pendingBlockState != null && pendingBlockState.equals(requiredState)) {
                clearPending();
                sessionManager.resumeSession(sessionId);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                SessionStateMachine.State currentState = sessionManager.getSession(sessionId).getState();
                PacketDistributor.sendToPlayer(serverPlayer,
                        new SessionStateChangedPayload(sessionId, currentState));
            }
            break;
        }
    }

    // ==================== 劳动力动画 ====================

    /**
     * 让派遣的劳动力（宠物等实体）向目标位置移动并挥动手臂。
     * 契约的虚拟劳动力不会触发此动画，只有实体劳动力才会。
     */
    private void animateLaborers(BlockPos targetPos) {
        PlacementSession session = sessionManager.getSession(sessionId);
        if (session == null) return;

        for (UUID uuid : session.getParticipantUuids()) {
            Entity entity = targetLevel.getEntity(uuid);
            if (entity == null || !(entity instanceof LivingEntity living) || !living.isAlive()) continue;

            if (entity instanceof EntityMaid maid) {
                // 女仆：使用专用动画（面朝、移动）
                MaidWorkAnimationHelper.animateMaid(maid, targetPos, tickCounter);
            } else {
                // 宠物或其他生物：原版挥臂 + 轻量移动
                living.swingTime = 20;
                living.swing(InteractionHand.MAIN_HAND, true);
                if (living instanceof Mob mob) {
                    double dx = targetPos.getX() + 0.5 - living.getX();
                    double dz = targetPos.getZ() + 0.5 - living.getZ();
                    float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
                    living.setYRot(yaw);
                    living.yHeadRot = yaw;
                    living.yBodyRot = yaw;
                    double distanceSq = mob.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
                    if (distanceSq > 4.0 && mob.tickCount % 20 == 0) {
                        mob.getNavigation().stop();
                        mob.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0);
                    }
                }
            }
        }
    }

    // ==================== 工具方法 ====================

    private boolean hasMoriyaIronRing() {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(MaidConstructionTeamItems.MORIYA_IRON_RING.get())) return true;
        }
        return false;
    }

    private int countLaborers() {
        PlacementSession session = sessionManager.getSession(sessionId);
        if (session == null) return 0;
        return (int) session.getParticipantUuids().stream()
                .filter(uuid -> !uuid.equals(player.getUUID()))
                .count();
    }

    private BlockPos transformBlockPos(BlockPos localPos) {
        BlockPos rotated = localPos.rotate(rotation);
        return anchor.offset(rotated);
    }

    private Vec3 transformEntityVec(Vec3 localVec) {
        double x = localVec.x, z = localVec.z;
        switch (rotation) {
            case NONE -> {
            }
            case CLOCKWISE_90 -> {
                x = localVec.z;
                z = -localVec.x;
            }
            case CLOCKWISE_180 -> {
                x = -localVec.x;
                z = -localVec.z;
            }
            case COUNTERCLOCKWISE_90 -> {
                x = -localVec.z;
                z = localVec.x;
            }
        }
        return new Vec3(anchor.getX() + x, anchor.getY() + localVec.y, anchor.getZ() + z);
    }

    private MaterialShortageStrategy getCurrentStrategy() {
        PlacementSession session = sessionManager.getSession(sessionId);
        return session != null ? session.getShortageStrategy() : MaterialShortageStrategy.PAUSE;
    }

    private record BlockEntityInfo(BlockPos pos, CompoundTag tag) {
    }

    /**
     * 动态获取当前会话的所有参与者 UUID
     */
    private Set<UUID> getActiveParticipants() {
        PlacementSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            return session.getParticipantUuids();
        }
        return Collections.emptySet();
    }

    // ==================== 命令查询接口 ====================

    public int getLastLaborerCount() { return lastLaborerCount; }
    public int getLastContractBlocks() { return lastContractBlocks; }
    public int getLastContractInterval() { return lastContractInterval; }
    public int getLastEffectiveBlocksPerTick() { return lastEffectiveBlocksPerTick; }
    public int getLastActualInterval() { return lastActualInterval; }
    public float getLastDurabilitySave() { return lastDurabilitySave; }
}