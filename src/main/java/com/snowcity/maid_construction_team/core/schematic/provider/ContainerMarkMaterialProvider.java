package com.snowcity.maid_construction_team.core.schematic.provider;

import com.snowcity.maid_construction_team.component.MaterialChecklistData;
import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.core.schematic.IMaterialProvider;
import com.snowcity.maid_construction_team.item.MaidConstructionTeamItems;
import com.snowcity.maid_construction_team.item.custom.MaterialChecklistItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 基于物资点登记表标记容器的材料供应器。
 * <p>
 * 统一的查找优先级（适用于工具、材料和掉落回收）：
 * <ol>
 *   <li>非玩家生物实体背包</li>
 *   <li>方块容器（物资点登记表标记）</li>
 *   <li>玩家背包（受全局配置 {@code includePlayerInventory} 控制，默认关闭）</li>
 * </ol>
 * 支持跨维度供料，容器失效时向玩家发送聊天提示，并维护去抖重检标志。
 * <p>
 * 工具借用/归还机制：
 * <ul>
 *   <li>{@link #findTool(BlockState)} 会从来源中真实提取一个工具物品，并记录来源。</li>
 *   <li>{@link #returnTool(ItemStack)} 将消耗耐久后的工具归还至原来源；若工具损坏则直接丢弃。</li>
 * </ul>
 */
public class ContainerMarkMaterialProvider implements IMaterialProvider {

    private static final Logger LOGGER = LogManager.getLogger();

    /** 执行放置的玩家 */
    private final Player player;
    /** 本次放置会话的参与者 UUID 集合（包含玩家自身及生物实体） */
    private final Set<UUID> participantUuids;
    /** 去抖重检标志，由容器变更事件触发，用于恢复等待材料的会话 */
    private boolean needsRecheck = false;

    // ---- 工具借出记录，用于归还 ----
    @Nullable
    private IItemHandler borrowedToolHandler;
    private int borrowedToolSlot = -1;
    @Nullable
    private Entity borrowedToolEntity; // 未直接使用，但保留以扩展

    /**
     * @param player            执行放置的玩家
     * @param participantUuids  参与者 UUID 集合（至少包含玩家自己）
     */
    public ContainerMarkMaterialProvider(Player player, Set<UUID> participantUuids) {
        this.player = player;
        this.participantUuids = new HashSet<>(participantUuids);
        this.participantUuids.add(player.getUUID());
        clearBorrowedTool(); // 初始清空记录
    }

    // ==================== 工具查找与归还 ====================

    @Override
    public ItemStack findTool(BlockState targetState) {
        // 每次新的工具查找前清空旧的借出记录
        clearBorrowedTool();

        // 1. 非玩家生物实体背包
        ItemStack tool = borrowToolFromEntities(targetState, false);
        if (!tool.isEmpty()) return tool; // 找到一个就立即返回

        // 2. 方块容器（物资点登记表标记）
        tool = borrowToolFromContainers(targetState);
        if (!tool.isEmpty()) return tool; // 找到一个就立即返回

        // 3. 玩家背包（如果配置允许）
        if (getConfig().isIncludePlayerInventory()) {
            tool = borrowToolFromEntities(targetState, true);
            if (!tool.isEmpty()) return tool; // 找到一个就立即返回
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void returnTool(ItemStack tool) {
        // 无效或已损坏的工具直接清除记录
        if (tool.isEmpty() || (tool.isDamageableItem() && tool.getDamageValue() >= tool.getMaxDamage())) {
            clearBorrowedTool();
            return;
        }

        // 必须归还原借出时的物品栏
        if (borrowedToolHandler != null) {
            // 1. 先尝试放回原槽位
            if (borrowedToolSlot >= 0 && borrowedToolSlot < borrowedToolHandler.getSlots()) {
                ItemStack remaining = borrowedToolHandler.insertItem(borrowedToolSlot, tool, false);
                if (remaining.isEmpty()) {
                    clearBorrowedTool();
                    return;
                }
            }

            // 2. 原槽位放不下，尝试该物品栏的其他槽位
            for (int i = 0; i < borrowedToolHandler.getSlots(); i++) {
                if (i == borrowedToolSlot) continue;
                tool = borrowedToolHandler.insertItem(i, tool, false);
                if (tool.isEmpty()) {
                    clearBorrowedTool();
                    return;
                }
            }
        }

        // 3. 完全无法容纳，工具遗失，记录日志
        LOGGER.warn("工具无法被归还到原容器，已遗失。");
        clearBorrowedTool();
    }

    // ==================== 材料消耗 ====================

    @Override
    public boolean tryConsume(BlockState state) {
        // 戒指开关：直接允许放置，不消耗材料
        if (hasMoriyaIronRing()) {
            return true;
        }

        Block block = state.getBlock();
        ItemStack required = new ItemStack(block.asItem());
        if (required.isEmpty() || required.is(Items.AIR)) {
            return true;
        }

        // 1. 非玩家生物实体背包
        if (tryConsumeFromEntities(required, false)) return true;

        // 2. 方块容器
        if (tryConsumeFromContainers(required)) return true;

        // 3. 玩家背包（如果开启）
        if (getConfig().isIncludePlayerInventory()) {
            return tryConsumeFromEntities(required, true);
        }
        return false;
    }

    // ==================== 掉落回收 ====================

    @Override
    public boolean deposit(ItemStack stack) {
        if (stack.isEmpty()) return true;
        ItemStack remaining = stack.copy();

        // 1. 非玩家生物实体背包
        remaining = depositIntoEntities(remaining, false);
        if (remaining.isEmpty()) return true;

        // 2. 方块容器
        remaining = depositIntoContainers(remaining);
        if (remaining.isEmpty()) return true;

        // 3. 玩家背包（如果开启）
        if (getConfig().isIncludePlayerInventory()) {
            remaining = depositIntoEntities(remaining, true);
            if (remaining.isEmpty()) return true;
        }
        return false;
    }

    // ==================== 库存查询（新增，供规划表使用） ====================

    @Override
    public int getStock(BlockState state) {
        // 戒指模式：返回一个大数，表示无限
        if (hasMoriyaIronRing()) {
            return Integer.MAX_VALUE;
        }

        Block block = state.getBlock();
        ItemStack required = new ItemStack(block.asItem());
        if (required.isEmpty() || required.is(Items.AIR)) {
            return Integer.MAX_VALUE;
        }

        int total = 0;
        // 1. 非玩家生物实体背包
        total += countStockFromEntities(required, false);
        // 2. 方块容器
        total += countStockFromContainers(required);
        // 3. 玩家背包（如果配置允许）
        if (getConfig().isIncludePlayerInventory()) {
            total += countStockFromEntities(required, true);
        }
        return total;
    }

    // ==================== 工具借用/归还的内部实现 ====================

    /** 从生物实体背包搜索并真实提取最佳工具。includePlayer 控制是否包含玩家。 */
    private ItemStack borrowToolFromEntities(BlockState targetState, boolean includePlayer) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return ItemStack.EMPTY;

        for (UUID uuid : participantUuids) {
            if (!includePlayer && uuid.equals(player.getUUID())) continue;
            IItemHandler handler = null;
            Entity entity = null;

            if (uuid.equals(player.getUUID()) && includePlayer) {
                handler = getPlayerItemHandler();
            } else {
                entity = findEntityByUUID(uuid);
                if (entity != null) {
                    handler = getEntityItemHandler(entity);
                }
            }

            if (handler != null) {
                ItemStack tool = extractBestTool(handler, targetState, entity);
                if (!tool.isEmpty()) return tool;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 从所有标记的方块容器中搜索并真实提取最佳工具。 */
    private ItemStack borrowToolFromContainers(BlockState targetState) {
        for (ContainerEntry entry : getMarkedContainers()) {
            IItemHandler handler = getBlockItemHandler(entry);
            if (handler != null) {
                ItemStack tool = extractBestTool(handler, targetState, null);
                if (!tool.isEmpty()) return tool;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 在给定的物品栏中寻找对目标方块最佳的可用工具，提取并记录借出来源。
     * @param handler     物品栏
     * @param targetState 目标方块状态
     * @param entity      来源实体（可为null，表示方块容器）
     * @return 提取出的工具栈，如果没有则返回 EMPTY
     */
    @Nullable
    private ItemStack extractBestTool(IItemHandler handler, BlockState targetState, @Nullable Entity entity) {
        // 借出锁定
        if (this.borrowedToolHandler != null) {
            return ItemStack.EMPTY;
        }

        int bestSlot = -1;
        ItemStack bestStack = ItemStack.EMPTY;
        int highestDamage = -1; // ★ 改为记录最高损坏值

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty() || !stack.isCorrectToolForDrops(targetState)) continue;
            int damage = stack.getDamageValue(); // 获取损坏值
            if (damage > highestDamage) {         // 大于，找损坏最严重的工具
                highestDamage = damage;
                bestSlot = i;
                bestStack = stack;
            }
        }
        if (bestSlot < 0) return ItemStack.EMPTY;

        ItemStack extracted = handler.extractItem(bestSlot, 1, false);
        if (!extracted.isEmpty()) {
            this.borrowedToolHandler = handler;
            this.borrowedToolSlot = bestSlot;
            this.borrowedToolEntity = entity;
        }
        return extracted;
    }

    /** 清除借出记录 */
    private void clearBorrowedTool() {
        this.borrowedToolHandler = null;
        this.borrowedToolSlot = -1;
        this.borrowedToolEntity = null;
    }

    // ==================== 材料消耗与回收的辅助方法 ====================

    private boolean tryConsumeFromEntities(ItemStack required, boolean includePlayer) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;

        for (UUID uuid : participantUuids) {
            if (!includePlayer && uuid.equals(player.getUUID())) continue;
            IItemHandler handler = null;

            if (uuid.equals(player.getUUID()) && includePlayer) {
                handler = getPlayerItemHandler();
            } else {
                Entity entity = findEntityByUUID(uuid);
                if (entity != null) {
                    handler = getEntityItemHandler(entity);
                }
            }

            if (handler != null && extractExact(handler, required)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryConsumeFromContainers(ItemStack required) {
        for (ContainerEntry entry : getMarkedContainers()) {
            IItemHandler handler = getBlockItemHandler(entry);
            if (handler == null) {
                if (player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.literal(
                            "物资点 " + entry.pos().toShortString() + " 容器已失效，请重新标记"
                    ));
                }
                continue;
            }
            if (extractExact(handler, required)) {
                return true;
            }
        }
        return false;
    }

    private boolean extractExact(IItemHandler handler, ItemStack required) {
        int total = countItem(handler, required);
        if (total < 1) return false;

        int toExtract = 1;
        for (int i = 0; i < handler.getSlots() && toExtract > 0; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(stack, required)) {
                ItemStack extracted = handler.extractItem(i, toExtract, false);
                toExtract -= extracted.getCount();
            }
        }
        return toExtract == 0;
    }

    private ItemStack depositIntoEntities(ItemStack stack, boolean includePlayer) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return stack;

        ItemStack remaining = stack.copy();
        for (UUID uuid : participantUuids) {
            if (!includePlayer && uuid.equals(player.getUUID())) continue;
            IItemHandler handler = null;

            if (uuid.equals(player.getUUID()) && includePlayer) {
                handler = getPlayerItemHandler();
            } else {
                Entity entity = findEntityByUUID(uuid);
                if (entity != null) {
                    handler = getEntityItemHandler(entity);
                }
            }

            if (handler != null) {
                remaining = ItemHandlerHelper.insertItem(handler, remaining, false);
                if (remaining.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    private ItemStack depositIntoContainers(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (ContainerEntry entry : getMarkedContainers()) {
            IItemHandler handler = getBlockItemHandler(entry);
            if (handler != null) {
                remaining = ItemHandlerHelper.insertItem(handler, remaining, false);
                if (remaining.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    // ==================== 库存统计辅助方法 ====================

    private int countStockFromEntities(ItemStack required, boolean includePlayer) {
        int count = 0;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 0;
        for (UUID uuid : participantUuids) {
            if (!includePlayer && uuid.equals(player.getUUID())) continue;
            IItemHandler handler = null;
            if (uuid.equals(player.getUUID()) && includePlayer) {
                handler = getPlayerItemHandler();
            } else {
                Entity entity = findEntityByUUID(uuid);
                if (entity != null) handler = getEntityItemHandler(entity);
            }
            if (handler != null) count += countItem(handler, required);
        }
        return count;
    }

    private int countStockFromContainers(ItemStack required) {
        int count = 0;
        for (ContainerEntry entry : getMarkedContainers()) {
            IItemHandler handler = getBlockItemHandler(entry);
            if (handler != null) count += countItem(handler, required);
        }
        return count;
    }

    /** 统计物品栏中指定物品的数量 */
    private int countItem(IItemHandler handler, ItemStack target) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(stack, target)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // ==================== 工具与容器访问 ====================

    @Nullable
    private IItemHandler getPlayerItemHandler() {
        return player.getCapability(Capabilities.ItemHandler.ENTITY);
    }

    @Nullable
    private Entity findEntityByUUID(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    @Nullable
    private IItemHandler getEntityItemHandler(Entity entity) {
        var handler = entity.getCapability(Capabilities.ItemHandler.ENTITY);
        if (handler != null) return handler;
        if (entity instanceof Container container) {
            return new net.neoforged.neoforge.items.wrapper.InvWrapper(container);
        }
        return null;
    }

    @Nullable
    private IItemHandler getBlockItemHandler(ContainerEntry entry) {
        ServerLevel level = getLevelByDimension(entry.dimension());
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(entry.pos());
        if (be == null) return null;
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, entry.pos(), null);
        if (handler != null) return handler;
        if (be instanceof Container container) {
            return new net.neoforged.neoforge.items.wrapper.InvWrapper(container);
        }
        return null;
    }

    private List<ContainerEntry> getMarkedContainers() {
        List<ContainerEntry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty() || !(stack.getItem() instanceof MaterialChecklistItem)) continue;
            MaterialChecklistData data = stack.getOrDefault(
                    ModDataComponents.MATERIAL_CHECKLIST.get(),
                    new MaterialChecklistData(new CompoundTag())
            );
            ListTag list = data.getContainerList();
            for (Tag t : list) {
                CompoundTag tag = (CompoundTag) t;
                String dim = tag.getString(MaterialChecklistData.TAG_DIMENSION);
                BlockPos pos = new BlockPos(
                        tag.getInt(MaterialChecklistData.TAG_X),
                        tag.getInt(MaterialChecklistData.TAG_Y),
                        tag.getInt(MaterialChecklistData.TAG_Z)
                );
                String key = dim + ":" + pos.toShortString();
                if (seen.add(key)) {
                    entries.add(new ContainerEntry(dim, pos));
                }
            }
        }
        return entries;
    }

    @Nullable
    private ServerLevel getLevelByDimension(String dimensionId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        ResourceLocation location = ResourceLocation.tryParse(dimensionId);
        if (location == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimensionId)) {
                return level;
            }
        }
        return null;
    }

    // ==================== 戒指检查 ====================

    private boolean hasMoriyaIronRing() {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(MaidConstructionTeamItems.MORIYA_IRON_RING.get())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 配置获取 ====================

    private MaidConstructionTeamConfig getConfig() {
        return MaidConstructionTeamConfig.getInstance();
    }

    // ==================== 去抖机制 ====================

    public void markNeedsRecheck() { this.needsRecheck = true; }
    public boolean needsRecheck() { return needsRecheck; }
    public void clearRecheck() { this.needsRecheck = false; }

    // ==================== 内部数据结构 ====================

    private record ContainerEntry(String dimension, BlockPos pos) {}
}