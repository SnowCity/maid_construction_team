package com.snowcity.maid_construction_team.client.screen;

import com.snowcity.maid_construction_team.client.preview.PreviewManager;
import com.snowcity.maid_construction_team.client.preview.PreviewPlacementContext;
import com.snowcity.maid_construction_team.component.BlueprintData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.core.manager.SessionStateMachine;
import com.snowcity.maid_construction_team.network.payload.session.RequestSessionsPayload;
import com.snowcity.maid_construction_team.network.payload.session.SessionsResponsePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 规划表主界面。
 * <p>
 * 提供两个标签页：
 * <ul>
 *   <li>活跃会话：列出玩家当前所有活跃的蓝图放置会话，点击卡片可打开详情界面。</li>
 *   <li>未部署预览：列出所有已创建但尚未确认放置的蓝图预览（来自 {@link PreviewManager}），并提供单独取消预览的按钮。</li>
 * </ul>
 * 界面使用自定义渲染，不再依赖 {@link net.minecraft.client.gui.components.ObjectSelectionList}。
 */
public class ScheduleScreen extends Screen {

    private enum Tab { ACTIVE_SESSIONS, UNDEPLOYED_PREVIEWS }

    // ---- 当前选中的标签 ----
    private Tab currentTab = Tab.ACTIVE_SESSIONS;

    // ---- 标签按钮 ----
    private Button tabActiveBtn;
    private Button tabPreviewBtn;

    // ---- 数据 ----
    private List<SessionsResponsePayload.SessionSummary> sessions = new ArrayList<>();

    // ---- 会话卡片列表（自定义渲染） ----
    private final List<SessionCard> sessionCards = new ArrayList<>();

    // ---- 未部署预览卡片列表（自定义渲染） ----
    private final List<PreviewCard> previewCards = new ArrayList<>();

    // ---- 滚动偏移（活跃会话标签） ----
    private double scrollOffset = 0;

    // ---- 静态实例，供客户端处理器更新 UI ----
    @Nullable
    private static ScheduleScreen currentInstance;

    // ---- 常量 ----
    private static final int CARD_HEIGHT = 36;
    private static final int CARD_PADDING = 5;
    private static final int CARD_SPACING = 4;
    private static final int CARD_X = 10;

    public ScheduleScreen() {
        super(Component.literal("规划表"));
    }

    @Nullable
    public static ScheduleScreen getCurrentInstance() {
        return currentInstance;
    }

    @Override
    protected void init() {
        super.init();
        currentInstance = this;

        // ---- 标签按钮 ----
        int tabW = 100, tabH = 20;
        tabActiveBtn = addRenderableWidget(
                Button.builder(Component.literal("📋 活跃会话"), btn -> switchTab(Tab.ACTIVE_SESSIONS))
                        .pos(width / 4 - tabW / 2, 10).size(tabW, tabH).build()
        );
        tabPreviewBtn = addRenderableWidget(
                Button.builder(Component.literal("👁️ 未部署预览"), btn -> switchTab(Tab.UNDEPLOYED_PREVIEWS))
                        .pos(3 * width / 4 - tabW / 2, 10).size(tabW, tabH).build()
        );

        // 默认选中活跃会话标签，并向服务端请求会话列表
        switchTab(Tab.ACTIVE_SESSIONS);
        PacketDistributor.sendToServer(new RequestSessionsPayload(Optional.empty()));
    }

    public void requestSessionList() {
        PacketDistributor.sendToServer(new RequestSessionsPayload(Optional.empty()));
    }

    /**
     * 切换标签页，并清空旧卡片。
     */
    private void switchTab(Tab tab) {
        currentTab = tab;
        tabActiveBtn.active = tab != Tab.ACTIVE_SESSIONS;
        tabPreviewBtn.active = tab != Tab.UNDEPLOYED_PREVIEWS;

        // 清空旧卡片及按钮
        clearAllCards();

        // 如果切回活跃会话且已有缓存数据，立刻重建卡片
        if (tab == Tab.ACTIVE_SESSIONS && !sessions.isEmpty()) {
            rebuildSessionCards();
        } else if (tab == Tab.UNDEPLOYED_PREVIEWS) {
            // 切到未部署预览时，立即根据当前预览列表重建卡片
            rebuildPreviewCards();
        }
    }

    /**
     * 清除所有卡片及它们的按钮（包括会话卡片和预览卡片）。
     */
    private void clearAllCards() {
        // 移除会话卡片的详情按钮
        for (SessionCard card : sessionCards) {
            removeWidget(card.detailBtn);
        }
        sessionCards.clear();

        // 移除预览卡片的取消按钮
        for (PreviewCard card : previewCards) {
            removeWidget(card.cancelBtn);
        }
        previewCards.clear();
    }

    // ==================== 活跃会话列表相关方法 ====================

    /**
     * 由客户端处理器调用，更新活跃会话摘要列表。
     */
    public void updateSessions(List<SessionsResponsePayload.SessionSummary> sessions) {
        this.sessions = sessions;
        if (currentTab == Tab.ACTIVE_SESSIONS) {
            rebuildSessionCards();
        }
    }

    /**
     * 根据当前会话列表重建会话卡片。
     */
    private void rebuildSessionCards() {
        // 先清除旧会话卡片
        for (SessionCard card : sessionCards) {
            removeWidget(card.detailBtn);
        }
        sessionCards.clear();

        int y = 40;
        for (SessionsResponsePayload.SessionSummary summary : sessions) {
            // 创建“详情”按钮
            Button detailBtn = Button.builder(Component.literal("详情"), btn -> {
                PacketDistributor.sendToServer(new RequestSessionsPayload(Optional.of(summary.sessionId())));
            }).pos(0, 0).size(40, 20).build();
            addRenderableWidget(detailBtn);

            SessionCard card = new SessionCard(summary, y, detailBtn);
            sessionCards.add(card);
            y += CARD_HEIGHT + CARD_SPACING;
        }
    }

    /**
     * 渲染活跃会话标签页的内容，包括卡片和滚动。
     */
    private void renderActiveSessions(GuiGraphics graphics, int mouseX, int mouseY) {
        if (sessions.isEmpty()) {
            graphics.drawString(font, "当前无活跃会话", width / 2 - 40, height / 2 - 10, 0xFFAAAAAA);
            return;
        }

        int contentTop = 35;
        int contentBottom = height - 15;
        int contentLeft = CARD_X;
        int contentRight = width - 10;

        graphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom);

        for (SessionCard card : sessionCards) {
            int cardY = card.baseY + (int) scrollOffset;
            if (cardY + CARD_HEIGHT < contentTop || cardY > contentBottom) continue;
            card.render(graphics, mouseX, mouseY, cardY);
        }

        graphics.disableScissor();
    }

    // ==================== 未部署预览列表相关方法 ====================

    /**
     * 根据当前预览列表重建未部署预览的卡片。
     */
    private void rebuildPreviewCards() {
        // 先清除旧预览卡片及其按钮
        for (PreviewCard card : previewCards) {
            removeWidget(card.cancelBtn);
        }
        previewCards.clear();

        Collection<PreviewManager.PreviewEntry> entries = PreviewManager.getAllPreviews();
        if (entries.isEmpty()) return;

        int y = 40;
        for (PreviewManager.PreviewEntry entry : entries) {
            PreviewPlacementContext ctx = entry.getContext();
            // ★ 从物品栈的蓝图数据组件中获取真实文件名
            ItemStack sourceStack = entry.getSourceStack();
            BlueprintData bd = sourceStack.get(ModDataComponents.BLUEPRINT_DATA.get());
            String blueprintName = (bd != null && !bd.fileName().isEmpty()) ? bd.fileName() : "未命名蓝图";

            // 创建“取消预览”按钮，并绑定正确的预览 ID
            UUID previewId = entry.getPreviewId(); // 确保 PreviewEntry 已暴露 getPreviewId()
            Button cancelBtn = Button.builder(Component.literal("取消预览"), btn -> {
                PreviewManager.cancel(previewId);
                rebuildPreviewCards(); // 取消后立即刷新界面
            }).pos(0, 0).size(60, 20).build();
            addRenderableWidget(cancelBtn);

            PreviewCard card = new PreviewCard(blueprintName, ctx.getSizeDisplay(), ctx.getAnchor(), y, cancelBtn);
            previewCards.add(card);
            y += CARD_HEIGHT + CARD_SPACING;
        }
    }

    /**
     * 渲染未部署预览标签页的内容。
     */
    private void renderUndeployedPreviews(GuiGraphics graphics, int mouseX, int mouseY) {
        if (previewCards.isEmpty()) {
            // 如果预览列表为空，显示提示文字
            if (PreviewManager.getAllPreviews().isEmpty()) {
                graphics.drawString(font, "当前无预览", 10, 40, 0xAAAAAA);
            } else {
                // 预览列表非空但卡片未重建，手动触发一次（避免第一次进入时重建延迟）
                rebuildPreviewCards();
            }
            return;
        }

        // 渲染已有的预览卡片（无滚动，若数量多可后续扩展滚动）
        for (PreviewCard card : previewCards) {
            card.render(graphics);
        }
    }

    // ==================== 渲染 ====================

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        if (currentTab == Tab.ACTIVE_SESSIONS) {
            renderActiveSessions(graphics, mouseX, mouseY);
        } else {
            // 每次渲染未部署预览时，检查预览数量是否变化，如变化则重建卡片
            int currentCount = PreviewManager.getAllPreviews().size();
            if (previewCards.size() != currentCount) {
                rebuildPreviewCards();
            }
            renderUndeployedPreviews(graphics, mouseX, mouseY);
        }
    }

    // ==================== 输入处理 ====================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab != Tab.ACTIVE_SESSIONS || sessions.isEmpty()) return true;

        int totalContentHeight = sessions.size() * (CARD_HEIGHT + CARD_SPACING);
        int visibleHeight = (height - 50);
        if (totalContentHeight <= visibleHeight) {
            scrollOffset = 0;
            return true;
        }

        double maxScrollUp = -(totalContentHeight - visibleHeight);
        scrollOffset += scrollY * 15;
        scrollOffset = Math.max(maxScrollUp, Math.min(0, scrollOffset));

        return true;
    }

    @Override
    public void onClose() {
        currentInstance = null;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 内部类：会话卡片 ====================

    private class SessionCard {
        private final SessionsResponsePayload.SessionSummary data;
        private final Button detailBtn;
        private final int baseY;

        public SessionCard(SessionsResponsePayload.SessionSummary data, int baseY, Button detailBtn) {
            this.data = data;
            this.baseY = baseY;
            this.detailBtn = detailBtn;
        }

        public void render(GuiGraphics graphics, int mouseX, int mouseY, int cardY) {
            int cardWidth = width - 2 * CARD_X;
            // 背景
            graphics.fill(CARD_X, cardY, CARD_X + cardWidth, cardY + CARD_HEIGHT, 0x88000000);

            // 会话ID（截短）和蓝图名称
            String idText = data.sessionId().toString().substring(0, 8) + "...";
            graphics.drawString(font, idText, CARD_X + CARD_PADDING, cardY + 3, 0xFFFFFF);
            graphics.drawString(font, data.blueprintName(), CARD_X + CARD_PADDING + 80, cardY + 3, 0xFFFFFF);

            // 进度
            String progressText = data.currentBlocks() + "/" + data.totalBlocks();
            graphics.drawString(font, progressText, CARD_X + CARD_PADDING, cardY + 16, 0xAAAAAA);

            // 状态
            String stateText = formatState(data.state());
            graphics.drawString(font, stateText, CARD_X + CARD_PADDING + 80, cardY + 16, 0xCCCCCC);

            // 参与者数量
            String participantText = "参与者: " + data.participantCount();
            graphics.drawString(font, participantText, CARD_X + CARD_PADDING + 160, cardY + 16, 0xCCCCCC);

            // 更新详情按钮位置
            detailBtn.setX(CARD_X + cardWidth - 45);
            detailBtn.setY(cardY + (CARD_HEIGHT - 20) / 2);
        }
    }

    // ==================== 内部类：预览卡片 ====================

    private class PreviewCard {
        private final String blueprintName;
        private final String sizeDisplay;
        private final net.minecraft.core.BlockPos anchor;
        private final int baseY;
        private final Button cancelBtn;

        public PreviewCard(String blueprintName, String sizeDisplay, net.minecraft.core.BlockPos anchor, int baseY, Button cancelBtn) {
            this.blueprintName = blueprintName;
            this.sizeDisplay = sizeDisplay;
            this.anchor = anchor;
            this.baseY = baseY;
            this.cancelBtn = cancelBtn;
        }

        public void render(GuiGraphics graphics) {
            int cardWidth = width - 2 * CARD_X;
            int y = baseY;

            // 背景
            graphics.fill(CARD_X, y, CARD_X + cardWidth, y + CARD_HEIGHT, 0x88000000);

            // 蓝图名称
            graphics.drawString(font, blueprintName, CARD_X + CARD_PADDING, y + 3, 0xFFFFFF);

            // 尺寸和锚点
            String info = "尺寸: " + sizeDisplay + "  锚点: " + anchor.toShortString();
            graphics.drawString(font, info, CARD_X + CARD_PADDING, y + 16, 0xAAAAAA);

            // 更新取消按钮位置
            cancelBtn.setX(CARD_X + cardWidth - 65);
            cancelBtn.setY(y + (CARD_HEIGHT - 20) / 2);
        }
    }

    /**
     * 状态转换为可读中文。
     */
    private String formatState(SessionStateMachine.State state) {
        return switch (state) {
            case RUNNING -> "运行中";
            case WAITING_MATERIALS -> "等待材料";
            case PAUSED -> "已暂停";
            case PAUSED_OFFLINE -> "离线暂停";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
        };
    }
}