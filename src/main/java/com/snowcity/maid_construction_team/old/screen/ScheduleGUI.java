package com.snowcity.maid_construction_team.old.screen;

import com.snowcity.maid_construction_team.old.manager.MaidBuildManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ScheduleGUI extends Screen {

    // ============================ 常量配置 ============================
    // 尺寸
    private static final int WIDTH = 220;
    private static final int HEIGHT = 200;
    // 背景颜色：黑灰色 (ARGB格式)
    private static final int BG_COLOR = 0;
    // 白色方框颜色
    private static final int BOX_BORDER_COLOR = 0xFFFFFFFF;
    private static final int BOX_BG_COLOR = 0xFF3A3A3A;
    // 文字颜色
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    // 会话卡片配置
    private static final int CARD_HEIGHT = 40;
    private static final int CARD_PADDING = 5;
    private static final int CARD_SPACING = 8;
    private static final int BUTTON_WIDTH = 50;
    private static final int BUTTON_HEIGHT = 20;

    // ============================ 状态字段 ============================
    private int leftPos; // GUI左上角X坐标
    private int topPos; // GUI左上角Y坐标
    private float scrollOffset = 0.0F; // 滚动偏移量
    private final List<SessionCard> sessionCards = new ArrayList<>(); // 会话卡片列表

    // ============================ 【新增】标记位：是否应该在 tick 中打开详情页 ============================
//    private boolean shouldOpenDetail = false;
//    private UUID pendingSessionId = null;

    public ScheduleGUI() {
        super(Component.translatable("screen.schedule_gui"));
    }

    @Override
    protected void init() {
        super.init();

        // 计算GUI居中位置
        this.leftPos = (this.width - WIDTH) / 2;
        this.topPos = (this.height - HEIGHT) / 2;

        // 1.获得该玩家所有建筑会话
        UUID currentPlayerId = null;
        if (this.minecraft != null) {
            if (this.minecraft.player != null) {
                currentPlayerId = this.minecraft.player.getUUID();
            }
        }
        List<UUID> sessionIds = MaidBuildManager.getAllSessionIdsForPlayer(currentPlayerId);

        // 2. 清空旧卡片
        this.clearWidgets();
        this.sessionCards.clear();

        // 3. 为每个会话创建卡片和按钮
        int cardIndex = 0;
        for (UUID sessionId : sessionIds) {
            // 创建卡片数据
            SessionCard card = new SessionCard(sessionId, cardIndex);
            this.sessionCards.add(card);

            // 创建"详情"按钮（位置会在render中动态更新）
            Button detailButton = Button.builder(Component.literal("详情"), (btn) -> {
                // 按钮点击逻辑：打开详情GUI或发送网络包
                this.onDetailButtonClick(sessionId);
            }).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            this.addRenderableWidget(detailButton);
            card.detailButton = detailButton;

            cardIndex++;
        }
    }


    /**
     * 默认渲染黑灰色书型背景, 禁用可以直接空实现
     */
    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        // 背景
        guiGraphics.fill(leftPos, topPos, leftPos + WIDTH, topPos + HEIGHT, 0xFF2C2C2C);
        guiGraphics.renderOutline(leftPos, topPos, WIDTH, HEIGHT, 0xFFFFFFFF);
        // 标题
        guiGraphics.drawString(
                this.font, Component.literal("建筑任务列表"),
                leftPos + 10, topPos + 35, 0xFFFFFFFF, false
        );

        // 根据玩家状态显示
        if (this.sessionCards.isEmpty()) {
            // ---------------- 情况A：没有会话 ----------------
            String emptyText = "当前无建筑任务";
            // 计算文字宽度，实现水平居中
            int textWidth = this.font.width(emptyText);
            int textX = leftPos + (WIDTH - textWidth) / 2;
            // 垂直居中
            int textY = topPos + (HEIGHT - this.font.lineHeight) / 2;

            // 绘制文字
            guiGraphics.drawString(this.font, Component.literal(emptyText), textX, textY, 0xFFAAAAAA, false);
        }

        // 渲染会话卡片
        this.renderSessionCards(guiGraphics, mouseX, mouseY);
        // 渲染按钮和其他组件（必须在最后调用，保证按钮在最上层）
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * 渲染所有会话卡片
     */
    private void renderSessionCards(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 计算可视区域
        int contentTop = topPos + 55;
        int contentBottom = topPos + HEIGHT - 15;
        int contentLeft = leftPos + 10;
        int contentRight = leftPos + WIDTH - 10;
        int contentWidth = leftPos + WIDTH - contentLeft;

        // 启用剪刀：只渲染可视区域内的内容（滚动优化）
        guiGraphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom);

        // 遍历渲染每个卡片
        for (SessionCard card : this.sessionCards) {
            // 计算卡片的绝对Y坐标（应用滚动偏移）
            int cardY = contentTop + card.index * (CARD_HEIGHT + CARD_SPACING) + (int) scrollOffset;
            int cardX = contentLeft;

            // 2. 渲染会话ID文字
            String idText = "会话: " + card.sessionId.toString().substring(0, 8) + "...";

            int textY = cardY + (CARD_HEIGHT - this.font.lineHeight) / 2;

            guiGraphics.drawString(this.font, Component.literal(idText), cardX + CARD_PADDING, textY, 0xFFFFFFFF, false);

            // 更新"详情"按钮的位置
            if (card.detailButton != null) {
                int btnX = cardX + contentWidth - BUTTON_WIDTH - CARD_PADDING;
                int btnY = cardY + (CARD_HEIGHT - BUTTON_HEIGHT) / 2;
                card.detailButton.setPosition(btnX, btnY);
            }
        }

        // 关闭剪刀
        guiGraphics.disableScissor();
    }

    /**
     * 【修复】处理鼠标滚轮（滚动列表）
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 只有当有卡片时才处理滚动
        if (this.sessionCards.isEmpty()) {
            return true;
        }

        // ============================ 1. 重新计算正确的可视区域高度 ============================
        // 内容区域是从 topPos + 55 开始，到 topPos + HEIGHT - 15 结束
        int contentTop = topPos + 55;
        int contentBottom = topPos + HEIGHT - 15;
        int visibleHeight = contentBottom - contentTop; // 这才是真正能看到内容的高度

        // ============================ 2. 计算内容总高度 ============================
        SessionCard lastCard = this.sessionCards.getLast();
        int totalHeight = lastCard.index * (CARD_HEIGHT + CARD_SPACING) + CARD_HEIGHT;

        // ============================ 3. 计算最大滚动范围（关键修复） ============================
        // 如果内容总高度 <= 可视高度，不需要滚动
        if (totalHeight <= visibleHeight) {
            this.scrollOffset = 0.0F;
            return true;
        }

        // 计算最大滚动偏移（负数，代表内容向上移动的最大距离）
        // 逻辑：我们最多只能让最后一个卡片的底部刚好对齐可视区域的底部
        float maxScrollUp = -(totalHeight - visibleHeight);

        // ============================ 4. 更新滚动偏移（修复方向） ============================
        // scrollY > 0 是向上滚（内容向下移），scrollY < 0 是向下滚（内容向上移）
        // 乘以15是滚动速度，你可以根据手感调整
        this.scrollOffset += (float) (scrollY * 15.0);

        // ============================ 5. 严格限制滚动范围（防止跑出GUI） ============================
        // 限制1：不能滚得太靠上（内容不能完全跑上去，最上面要留第一个卡片）
        // 限制2：不能滚得太靠下（内容不能完全跑下来，最下面要留最后一个卡片）
        this.scrollOffset = Math.max(maxScrollUp, Math.min(0.0F, this.scrollOffset));

        return true;
    }

    /**
     * "详情"按钮点击逻辑
     */
    private void onDetailButtonClick(UUID sessionId) {
        // 打开会话详情
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.setScreen(new SessionDetailGUI(sessionId, this));
        }
    }

    /**
     * 会话卡片数据类
     */
    private static class SessionCard {
        public final UUID sessionId;
        public final int index;
        public int x;
        public int y;
        public int width;
        public Button detailButton;

        public SessionCard(UUID sessionId, int index) {
            this.sessionId = sessionId;
            this.index = index;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 打开GUI时不暂停游戏
    }
}
