package com.snowcity.maid_construction_team.client.screen;

import com.snowcity.maid_construction_team.client.util.MaterialExporter;
import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
import com.snowcity.maid_construction_team.core.manager.SessionStateMachine;
import com.snowcity.maid_construction_team.core.schematic.MaterialShortageStrategy;
import com.snowcity.maid_construction_team.network.payload.PrintMaterialPayload;
import com.snowcity.maid_construction_team.network.payload.RequestMaterialListPayload;
import com.snowcity.maid_construction_team.network.payload.session.ControlSessionPayload;
import com.snowcity.maid_construction_team.network.payload.session.RequestSessionsPayload;
import com.snowcity.maid_construction_team.network.payload.session.SessionsResponsePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

/**
 * 会话详情界面。
 * <p>
 * 展示指定蓝图放置会话的完整信息，包括蓝图基本信息、进度百分比、
 * 材料清单（含图标和实时库存状态）、参与者列表、缺失方块列表，
 * 并提供暂停/继续、取消建造（二次确认）、刷新、返回列表以及
 * 材料不足策略切换等控制按钮。
 * <p>
 * 界面会根据当前放置速度自动定时刷新，确保数据实时性。
 */
public class SessionDetailScreen extends Screen {

    /** 当前显示的会话详情数据，会被自动刷新或手动刷新更新 */
    private SessionsResponsePayload.SessionDetail detail;

    /** 材料清单区域的垂直滚动偏移量（像素，负数表示向上滚动） */
    private double scrollOffset = 0;

    /** 上次自动刷新的时间戳（纳秒） */
    private long lastRefreshTime = 0;
    /** 当前自动刷新间隔（纳秒），由配置动态计算 */
    private long intervalNanos = 500_000_000L; // 默认500ms

    // 按钮引用，用于动态更新文本
    private Button pauseResumeBtn;
    private Button strategyBtn;

    /**
     * @param detail 服务端返回的会话详情数据
     */
    public SessionDetailScreen(SessionsResponsePayload.SessionDetail detail) {
        super(Component.literal("会话详情"));
        this.detail = detail;
        updateRefreshInterval();
    }

    @Override
    protected void init() {
        super.init();

        // ---- 暂停/继续按钮（文本动态更新） ----
        pauseResumeBtn = addRenderableWidget(
                Button.builder(Component.literal(getPauseResumeLabel()), btn -> {
                    ControlSessionPayload.Action action = isRunningOrWaiting()
                            ? ControlSessionPayload.Action.PAUSE
                            : ControlSessionPayload.Action.RESUME;
                    sendControl(action);
                    refreshDetail(); // 发送控制后自动请求最新数据
                }).pos(width / 2 - 140, height - 30).size(60, 20).build()
        );

        // ---- 取消建造按钮（二次确认） ----
        addRenderableWidget(
                Button.builder(Component.literal("取消建造"), btn -> {
                    minecraft.setScreen(new ConfirmScreen(
                            confirmed -> {
                                if (confirmed) {
                                    sendControl(ControlSessionPayload.Action.CANCEL);
                                    onClose();
                                } else {
                                    minecraft.setScreen(this);
                                }
                            },
                            Component.literal("确认取消建造？"),
                            Component.literal("已放置的方块将保留，操作不可撤销。")
                    ));
                }).pos(width / 2 - 70, height - 30).size(60, 20).build()
        );

        // ---- 手动刷新按钮 ----
        addRenderableWidget(
                Button.builder(Component.literal("刷新"), btn -> refreshDetail())
                        .pos(width / 2, height - 30).size(60, 20).build()
        );

        // ---- 返回列表按钮 ----
        addRenderableWidget(
                Button.builder(Component.literal("返回列表"), btn -> {
                    minecraft.setScreen(new ScheduleScreen());
                }).pos(10, height - 30).size(60, 20).build()
        );

        // ---- 材料不足策略切换按钮（文本动态更新） ----
        strategyBtn = addRenderableWidget(
                Button.builder(
                        Component.literal(getStrategyLabel()),
                        btn -> {
                            MaterialShortageStrategy newStrategy = (detail.strategy() == MaterialShortageStrategy.PAUSE)
                                    ? MaterialShortageStrategy.SKIP : MaterialShortageStrategy.PAUSE;
                            PacketDistributor.sendToServer(new ControlSessionPayload(detail.sessionId(),
                                    ControlSessionPayload.Action.SET_STRATEGY, newStrategy));
                            refreshDetail(); // 刷新以获取服务端确认
                        }
                ).pos(width - 110, height - 30).size(100, 20).build()
        );

        // 策略按钮上方
        addRenderableWidget(Button.builder(Component.literal("📄 打印材料"), btn -> {
            PacketDistributor.sendToServer(new PrintMaterialPayload(detail.sessionId(), -1));
        }).pos(width - 110, height - 60).size(80, 20).build());
    }

    // ==================== 状态与控制辅助方法 ====================

    /** 判断当前会话是否处于可暂停状态（RUNNING 或 WAITING_MATERIALS）。 */
    private boolean isRunningOrWaiting() {
        SessionStateMachine.State state = detail.state();
        return state == SessionStateMachine.State.RUNNING
                || state == SessionStateMachine.State.WAITING_MATERIALS;
    }

    /** 根据状态返回暂停/继续按钮的文本 */
    private String getPauseResumeLabel() {
        return isRunningOrWaiting() ? "暂停" : "继续";
    }

    /** 返回策略按钮的显示文本 */
    private String getStrategyLabel() {
        return "策略: " + (detail.strategy() == MaterialShortageStrategy.PAUSE ? "暂停" : "跳过");
    }

    /** 发送控制包至服务端 */
    private void sendControl(ControlSessionPayload.Action action) {
        // 对于非 SET_STRATEGY 操作，策略参数忽略
        PacketDistributor.sendToServer(new ControlSessionPayload(detail.sessionId(), action, detail.strategy()));
    }

    /** 向服务端请求当前会话的最新详情，用于手动或自动刷新 */
    private void refreshDetail() {
        PacketDistributor.sendToServer(new RequestSessionsPayload(Optional.of(detail.sessionId())));
    }

    /**
     * 由客户端处理器（SessionsResponseClientHandler）调用，用于更新界面数据。
     * 同时重新计算刷新间隔，以适应可能改变的配置。
     */
    public void updateDetail(SessionsResponsePayload.SessionDetail newDetail) {
        this.detail = newDetail;
        updateRefreshInterval();
    }

    /** 根据全局配置动态计算自动刷新间隔（纳秒） */
    private void updateRefreshInterval() {
        MaidConstructionTeamConfig config = MaidConstructionTeamConfig.getInstance();
        int blocksPerTick = Math.max(1, config.getBlocksPerTick());
        long intervalMillis = Math.max(500, 1000 / blocksPerTick);
        this.intervalNanos = intervalMillis * 1_000_000L;
    }

    // ==================== 渲染与输入处理 ====================

    @Override
    public void tick() {
        super.tick();
        // 自动刷新：每隔 intervalNanos 纳秒向服务端请求一次最新数据
        long now = System.nanoTime();
        if (now - lastRefreshTime > intervalNanos) {
            refreshDetail();
            lastRefreshTime = now;
        }

        // 动态更新按钮文本，确保状态变化后文本立即生效
        if (pauseResumeBtn != null) {
            pauseResumeBtn.setMessage(Component.literal(getPauseResumeLabel()));
        }
        if (strategyBtn != null) {
            strategyBtn.setMessage(Component.literal(getStrategyLabel()));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = 10;
        int y = 30;

        // -------------------- 蓝图基本信息 --------------------
        graphics.drawString(font, "蓝图: " + detail.blueprintName(), left, y, 0xFFFFFF);
        y += 14;

        String stateText = "状态: " + formatState(detail.state());
        graphics.drawString(font, stateText, left, y, 0xFFFFFF);
        y += 14;

        int percent = detail.totalBlocks() > 0 ? (detail.currentBlocks() * 100 / detail.totalBlocks()) : 0;
        String progressText = "进度: " + detail.currentBlocks() + "/" + detail.totalBlocks() + " (" + percent + "%)";
        graphics.drawString(font, progressText, left, y, 0xFFFFFF);
        y += 14;

        graphics.drawString(font, "实体: " + detail.placedEntities() + "/" + detail.totalEntities(), left, y, 0xFFFFFF);
        y += 20;

        // -------------------- 材料清单（可滚动） --------------------
        graphics.drawString(font, "---- 材料清单 ----", left, y, 0xFFAA00);
        y += 14;

        int materialStartY = y; // 材料列表开始绘制的 Y 坐标
        int materialEndY = height - 80; // 底部留出按钮空间
        int lineHeight = 12;
        int visibleLines = (materialEndY - materialStartY) / lineHeight;
        int totalMaterials = detail.materials().size();

        // 根据滚动偏移计算起始索引
        int startIdx = (int) (-scrollOffset / lineHeight);
        if (startIdx < 0) startIdx = 0;
        if (startIdx > totalMaterials) startIdx = totalMaterials;

        // 裁剪区域，防止文本溢出到底部按钮区域
        graphics.enableScissor(left, materialStartY, width - 10, materialEndY);

        int currentY = materialStartY;
        for (int i = startIdx; i < totalMaterials; i++) {
            if (currentY + lineHeight > materialEndY) break;
            SessionsResponsePayload.SessionDetail.MaterialItemInfo mat = detail.materials().get(i);

            // 绘制方块图标
            Block block = Block.stateById(mat.blockStateId()).getBlock();
            ItemStack iconStack = new ItemStack(block.asItem());
            graphics.renderItem(iconStack, left, currentY);

            // 绘制材料描述文本（右移 20 像素为图标留空间）
            String line = formatMaterialLine(mat);
            graphics.drawString(font, line, left + 20, currentY + 4, 0xCCCCCC);

            currentY += lineHeight;
        }
        graphics.disableScissor();

        // ---- 滚动条（仅当内容超出可视区域时显示） ----
        if (totalMaterials > visibleLines) {
            int scrollbarX = width - 8;
            int scrollbarTop = materialStartY;
            int scrollbarBottom = materialEndY;
            int trackLength = scrollbarBottom - scrollbarTop;

            float ratio = (float) visibleLines / totalMaterials;
            int sliderHeight = Math.max(10, (int) (trackLength * ratio));

            float scrollProgress = (float) (-scrollOffset) / ((totalMaterials - visibleLines) * lineHeight);
            if (scrollProgress < 0) scrollProgress = 0;
            if (scrollProgress > 1) scrollProgress = 1;
            int sliderTop = scrollbarTop + (int) ((trackLength - sliderHeight) * scrollProgress);

            // 绘制轨道和滑块
            graphics.fill(scrollbarX, scrollbarTop, scrollbarX + 4, scrollbarBottom, 0x44AAAAAA);
            graphics.fill(scrollbarX, sliderTop, scrollbarX + 4, sliderTop + sliderHeight, 0xFFCCCCCC);
        }

        // -------------------- 参与者列表 --------------------
        y = materialEndY + 5;
        graphics.drawString(font, "---- 参与者 ----", left, y, 0xFFAA00);
        y += 14;
        for (SessionsResponsePayload.SessionDetail.ParticipantInfo p : detail.participants()) {
            graphics.drawString(font, p.displayName(), left, y, 0xCCCCCC);
            y += 12;
            if (y > height - 30) break; // 防止超出屏幕
        }

        // -------------------- 缺失方块列表 --------------------
        if (!detail.missedBlocks().isEmpty()) {
            y += 2;
            int count = detail.missedBlocks().size();
            graphics.drawString(font, "---- 缺失方块 (" + count + ") ----", left, y, 0xFF5555);
            y += 12;
            for (BlockPos missed : detail.missedBlocks()) {
                if (y > height - 40) break; // 防止字母重叠到底部按钮
                String coord = missed.getX() + ", " + missed.getY() + ", " + missed.getZ();
                graphics.drawString(font, coord, left + 10, y, 0xCC8888);
                y += 12;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int totalMaterials = detail.materials().size();
        if (totalMaterials == 0) return true;

        int materialStartY = 120; // 需要与 render 中的 materialStartY 保持一致（此处简化）
        int materialEndY = height - 80;
        int lineHeight = 12;
        int visibleLines = (materialEndY - materialStartY) / lineHeight;
        if (visibleLines <= 0) return true;

        int maxOffset = Math.max(0, totalMaterials - visibleLines) * lineHeight;
        // 修正滚轮方向：向下滚动应该让内容向下移动，scrollOffset 增加
        scrollOffset += scrollY * lineHeight;
        scrollOffset = Math.clamp(scrollOffset, -maxOffset, 0);
        return true;
    }

    // ==================== 格式化辅助 ====================

    /** 将状态枚举转为中文 */
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

    /** 格式化单条材料信息（仅文本，图标在 render 中单独绘制） */
    private String formatMaterialLine(SessionsResponsePayload.SessionDetail.MaterialItemInfo mat) {
        Block block = Block.stateById(mat.blockStateId()).getBlock();
        String name = block.getName().getString();
        return name + " 需" + mat.totalRequired()
                + " 耗" + mat.consumed()
                + " 存" + mat.inStock()
                + " " + mat.status();
    }

    // 接收服务端返回的材料数据
    public void onMaterialListReceived(Map<Block, Integer> materials) {
        // 直接从当前详情获取
        String blueprintName = detail.blueprintName();
        MaterialExporter.export(materials, blueprintName);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public Object getSessionId() {
        return detail.sessionId();
    }
}