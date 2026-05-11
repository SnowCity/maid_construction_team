package com.snowcity.maid_construction_team.client.preview;

import com.snowcity.maid_construction_team.component.BlueprintData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.core.schematic.SchematicData;
import com.snowcity.maid_construction_team.item.custom.BlueprintPaperItem;
import com.snowcity.maid_construction_team.network.payload.blueprint.StartPlacementPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 蓝图预览管理器（客户端）。
 * <p>
 * 支持多个预览同时存在，每个预览通过蓝图纸物品上的 {@code PREVIEW_ID} 数据组件进行绑定。
 * 可见的预览由玩家当前主手物品的 {@code PREVIEW_ID} 决定。
 * <p>
 * 主要功能：
 * <ul>
 *   <li>{@link #enterOrUpdate(Player, BlockPos, Rotation)} 为玩家手持的蓝图纸创建或更新预览</li>
 *   <li>{@link #getVisibleContext(Player)} 获取当前应对玩家可见的预览上下文</li>
 *   <li>{@link #cancel(UUID)} 取消指定预览并清理关联物品上的 ID</li>
 *   <li>{@link #confirm()} 确认当前可见的预览，发送放置包并移除预览</li>
 *   <li>{@link #getAllPreviews()} 获取所有活跃预览，供规划表等 UI 使用</li>
 * </ul>
 */
public class PreviewManager {

    /** 所有活跃的预览，key = 预览唯一标识 */
    private static final Map<UUID, PreviewEntry> previews = new HashMap<>();

    /** 当前使用的预览渲染器（可随时替换） */
//    private static IPlacedPreviewRenderer activeRenderer = new LineBoxPreviewRenderer();
    private static IPlacedPreviewRenderer activeRenderer = new SchematicProjectionRenderer();

    /** 全局微调工具（后续可扩展为每个预览独立的工具） */
    private static PlacementPreviewTool currentTool = PlacementPreviewTool.MOVE_XZ;

    // ==================== 进入/更新预览 ====================

    /**
     * 为玩家手持的蓝图纸创建新的预览，或更新已有预览的位置。
     *
     * @param player   玩家
     * @param anchor   放置锚点
     * @param rotation 旋转方向
     * @return 如果创建或更新成功返回 true
     */
    public static boolean enterOrUpdate(Player player, BlockPos anchor, Rotation rotation) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlueprintPaperItem)) return false;

        // 获取物品上已有的预览 ID
        Optional<UUID> existingId = stack.get(ModDataComponents.PREVIEW_ID.get());
        UUID previewId;
        if (existingId != null && existingId.isPresent()) {
            previewId = existingId.get();
            PreviewEntry entry = previews.get(previewId);
            if (entry != null) {
                // 更新已有预览的位置和旋转
                entry.context = new PreviewPlacementContext(
                        entry.context.getSchematicData(), anchor, rotation);
                return true;
            }
            // 预览 ID 存在但上下文丢失（异常情况），继续创建新预览
        }

        // 创建全新预览
        BlueprintData data = stack.get(ModDataComponents.BLUEPRINT_DATA.get());
        if (data == null) return false;
        SchematicData schematic = data.toSchematicData();
        previewId = UUID.randomUUID();
        PreviewPlacementContext ctx = new PreviewPlacementContext(schematic, anchor, rotation);
        // 存储物品快照，方便未部署列表显示
        previews.put(previewId, new PreviewEntry(previewId, ctx, stack.copy()));
        // 将预览 ID 写入物品，使该物品与预览绑定
        stack.set(ModDataComponents.PREVIEW_ID.get(), Optional.of(previewId));
        return true;
    }

    // ==================== 可见性 ====================

    /**
     * 获取当前对玩家可见的预览上下文。
     * 可见性由玩家主手物品的 {@code PREVIEW_ID} 决定。
     *
     * @param player 玩家
     * @return 如果玩家手持绑定预览的蓝图纸，则返回上下文；否则返回 null
     */
    @Nullable
    public static PreviewPlacementContext getVisibleContext(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlueprintPaperItem)) return null;

        Optional<UUID> optId = stack.get(ModDataComponents.PREVIEW_ID.get());
        if (optId == null || optId.isEmpty()) return null;

        UUID id = optId.get();
        PreviewEntry entry = previews.get(id);
        return entry != null ? entry.context : null;
    }

    // ==================== 取消预览 ====================

    /**
     * 取消指定 UUID 的预览，并清理所有持有该预览 ID 的物品上的标记。
     *
     * @param previewId 要取消的预览 ID
     */
    public static void cancel(UUID previewId) {
        PreviewEntry entry = previews.remove(previewId);
        if (entry == null) return;

        // 遍历玩家背包，移除所有匹配的预览 ID
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            for (ItemStack stack : mc.player.getInventory().items) {
                if (stack.isEmpty()) continue;
                Optional<UUID> optId = stack.get(ModDataComponents.PREVIEW_ID.get());
                if (optId != null && optId.isPresent() && optId.get().equals(previewId)) {
                    stack.remove(ModDataComponents.PREVIEW_ID.get());
                }
            }
        }
    }

    /**
     * 注销所有预览（不建议频繁调用，主要用于清理）。
     */
    public static void cancelAll() {
        previews.clear();
    }

    // ==================== 确认放置 ====================

    /**
     * 确认当前可见的预览，发送放置请求并移除预览。
     */
    public static void confirm() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PreviewPlacementContext ctx = getVisibleContext(mc.player);
        if (ctx == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof BlueprintPaperItem)) return;

        // 发送放置包到服务端
        BlockPos anchor = ctx.getAnchor();
        Rotation rotation = ctx.getRotation();
        PacketDistributor.sendToServer(new StartPlacementPayload(stack, anchor, rotation));

        // 移除预览及相关物品上的 ID
        Optional<UUID> optId = stack.get(ModDataComponents.PREVIEW_ID.get());
        if (optId != null && optId.isPresent()) {
            cancel(optId.get());
        }
    }

    // ==================== 退出（不发送放置） ====================

    /**
     * 退出当前可见的预览（不发送放置请求）。
     * 仅移除可见性，保留预览上下文以备下次使用。
     * 如需彻底取消预览，请使用 {@link #cancel(UUID)}。
     */
    public static void exit() {
        // 单预览兼容：不做任何操作，因为可见性由物品决定。
        // 实际取消由规划表或切换物品时触发。
    }

    // ==================== 获取所有预览（供 UI 使用） ====================

    /**
     * 获取所有活跃预览的入口信息。
     *
     * @return 不可变的预览入口集合
     */
    public static Collection<PreviewEntry> getAllPreviews() {
        return Collections.unmodifiableCollection(previews.values());
    }

    // ==================== 渲染器管理 ====================

    /**
     * 获取当前使用的预览渲染器。
     */
    public static IPlacedPreviewRenderer getActiveRenderer() {
        return activeRenderer;
    }

    /**
     * 设置新的预览渲染器（可用于替换视觉效果，如从线框切换到半透明方块）。
     *
     * @param renderer 新的渲染器实现
     */
    public static void setActiveRenderer(IPlacedPreviewRenderer renderer) {
        activeRenderer = renderer;
    }

    // ==================== 工具管理 ====================

    /**
     * 获取全局微调工具（暂时所有预览共享同一个工具）。
     */
    public static PlacementPreviewTool getCurrentTool() {
        return currentTool;
    }

    /**
     * 切换到下一个微调工具。
     */
    public static void nextTool() {
        currentTool = currentTool.next();
    }

    /**
     * 切换到上一个微调工具。
     */
    public static void previousTool() {
        currentTool = currentTool.previous();
    }

    // ==================== 内部数据结构 ====================

    /**
     * 预览入口，包含上下文和创建时的物品栈快照。
     */
    public static class PreviewEntry {
        private final UUID previewId;
        private PreviewPlacementContext context;
        private final ItemStack sourceStack;

        public PreviewEntry(UUID previewId, PreviewPlacementContext context, ItemStack sourceStack) {
            this.previewId = previewId;
            this.context = context;
            this.sourceStack = sourceStack;
        }

        /** 获取预览 ID */
        public UUID getPreviewId() { return previewId; }

        /** 获取预览上下文（蓝图数据、锚点、旋转等） */
        public PreviewPlacementContext getContext() {
            return context;
        }

        /** 获取创建该预览时的蓝图纸物品栈快照（用于显示蓝图名称等信息） */
        public ItemStack getSourceStack() {
            return sourceStack;
        }
    }
}