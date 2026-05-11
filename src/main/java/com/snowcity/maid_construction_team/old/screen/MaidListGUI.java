package com.snowcity.maid_construction_team.old.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.snowcity.maid_construction_team.old.manager.MaidBuildManager;
import com.snowcity.maid_construction_team.old.network.MaidDispatchPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class MaidListGUI extends Screen {
    private final UUID sessionId;
    private final Screen parentScreen;
    private int leftPos;
    private int topPos;
    private static final int WIDTH = 220;
    private static final int HEIGHT = 200;
    private static final int WORK_MODE_AREA_WIDTH = 70;

    // 【新增】滚动相关字段
    private double scrollOffset = 0; // 当前滚动偏移量
    private static final int ENTRY_HEIGHT = 30; // 每个条目的高度（含间距）
    private int visibleAreaTop; // 可视区域顶部Y坐标
    private int visibleAreaBottom; // 可视区域底部Y坐标

    private final List<MaidEntry> maidEntries = new ArrayList<>();

    private static final Map<MaidState, Integer> STATE_PRIORITY = Map.of(
            MaidState.DISPATCHED, 0,
            MaidState.NORMAL, 1
    );

    public MaidListGUI(UUID sessionId, Screen parentScreen) {
        super(Component.literal("女仆列表"));
        if (sessionId == null) throw new IllegalArgumentException("会话ID不能为空！");
        this.sessionId = sessionId;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - WIDTH) / 2;
        this.topPos = (this.height - HEIGHT) / 2;
        this.clearWidgets();
        this.maidEntries.clear();
        this.scrollOffset = 0; // 打开界面时重置滚动

        // 定义可视区域（在背景框内，留出上下边距）
        this.visibleAreaTop = this.topPos + 50;
        this.visibleAreaBottom = this.topPos + HEIGHT - 10;

        this.addRenderableWidget(Button.builder(Component.literal("← 返回"), (btn) -> {
            if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
        }).bounds(leftPos + 5, topPos + 5, 60, 20).build());

        List<EntityMaid> playerMaids = (this.minecraft != null && this.minecraft.player != null)
                ? MaidBuildManager.getAllPlayerMaids(this.minecraft.player.getUUID()) : null;

        int yIndex = 0;
        if (playerMaids != null && !playerMaids.isEmpty()) {
            for (EntityMaid maid : playerMaids) {
                UUID maidId = maid.getUUID();
                MaidEntry entry = new MaidEntry(maidId, maid.getName().getString(), yIndex, maid);

                UUID maidSessionId = MaidBuildManager.getMaidSessionId(maidId);
                entry.state = (maidSessionId != null && maidSessionId.equals(this.sessionId)) ? MaidState.DISPATCHED : MaidState.NORMAL;

                Button dispatchBtn = Button.builder(
                        Component.literal(entry.state == MaidState.DISPATCHED ? "召回" : "派遣"),
                        (btn) -> handleDispatchClick(entry)
                ).bounds(0, 0, 45, 18).build();

                entry.dispatchBtn = dispatchBtn;
                this.addRenderableWidget(dispatchBtn);
                this.maidEntries.add(entry);
                updateButtonStates(entry);
                yIndex++;
            }
        }
    }

    // =========================================================================
    // 【核心新增1】重写鼠标滚轮监听方法
    // =========================================================================
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 计算内容总高度
        int contentHeight = this.maidEntries.size() * ENTRY_HEIGHT;
        // 计算可视区域高度
        int visibleHeight = this.visibleAreaBottom - this.visibleAreaTop;

        // 如果内容高度小于可视高度，不需要滚动
        if (contentHeight <= visibleHeight) {
            return true;
        }

        // 调整滚动偏移量（scrollY > 0 是向上滚，< 0 是向下滚）
        // 这里乘以 30 是滚动速度，可自行调整
        this.scrollOffset -= scrollY * 30;

        // 【边界限制】
        // 1. 不能滚到最上面以上（offset最小为0）
        // 2. 不能滚到最下面以下（offset最大为 内容高度 - 可视高度）
        double maxOffset = contentHeight - visibleHeight;
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxOffset);

        return true;
    }

    private Component getMaidWorkModeText(MaidEntry entry) {
        if (entry.state == MaidState.DISPATCHED) {
            return Component.literal("§a建筑会话中");
        }
        if (entry.maid != null && entry.maid.getTask() != null && entry.maid.getTask().getUid() != null) {
            String key = "task." + entry.maid.getTask().getUid().getNamespace() + "." + entry.maid.getTask().getUid().getPath();
            return Component.translatable(key);
        }
        return Component.literal("§7待命中");
    }

    private void handleDispatchClick(MaidEntry entry) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        UUID playerUuid = this.minecraft.player.getUUID();
        UUID maidId = entry.maidId;

        UUID realMaidSessionId = MaidBuildManager.getMaidSessionId(maidId);
        boolean isActuallyInSession = (realMaidSessionId != null) && realMaidSessionId.equals(this.sessionId);

        if (isActuallyInSession || entry.state == MaidState.DISPATCHED) {
            entry.state = MaidState.NORMAL;
            updateButtonStates(entry);
            PacketDistributor.sendToServer(new MaidDispatchPayload(this.sessionId, playerUuid, maidId, false));
            this.minecraft.player.sendSystemMessage(Component.literal("已召回女仆"));
        } else {
            if (realMaidSessionId != null && !realMaidSessionId.equals(this.sessionId)) {
                this.minecraft.player.sendSystemMessage(Component.literal("该女仆正在其他会话中工作！"));
                return;
            }
            entry.state = MaidState.DISPATCHED;
            updateButtonStates(entry);
            PacketDistributor.sendToServer(new MaidDispatchPayload(this.sessionId, playerUuid, maidId, true));
            this.minecraft.player.sendSystemMessage(Component.literal("已派遣女仆"));
        }
    }

    private void updateButtonStates(MaidEntry entry) {
        if (entry.dispatchBtn == null) return;
        entry.dispatchBtn.setMessage(Component.literal(entry.state == MaidState.DISPATCHED ? "召回" : "派遣"));
        entry.dispatchBtn.active = true;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 画背景和标题
        guiGraphics.fill(leftPos, topPos, leftPos + WIDTH, topPos + HEIGHT, 0xFF2C2C2C);
        guiGraphics.renderOutline(leftPos, topPos, WIDTH, HEIGHT, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, "女仆列表", leftPos + 10, topPos + 35, 0xFFFFFFFF, false);

        // 排序
        this.maidEntries.sort((e1, e2) -> {
            int priorityCompare = Integer.compare(STATE_PRIORITY.get(e1.state), STATE_PRIORITY.get(e2.state));
            return priorityCompare != 0 ? priorityCompare : Integer.compare(e1.index, e2.index);
        });

        // =========================================================================
        // 【核心修改2】渲染逻辑：应用滚动偏移 + 可视区域裁剪
        // =========================================================================

        // 1. 启用 Scissor 裁剪：只在可视区域内绘制内容
        guiGraphics.enableScissor(leftPos, visibleAreaTop, leftPos + WIDTH, visibleAreaBottom);

        int baseStartY = visibleAreaTop; // 起始Y坐标改为可视区域顶部

        for (int i = 0; i < this.maidEntries.size(); i++) {
            MaidEntry entry = this.maidEntries.get(i);

            // 【关键】计算条目实际Y坐标 = 基础Y + 索引*高度 - 滚动偏移
            int entryY = baseStartY + i * ENTRY_HEIGHT - (int)scrollOffset;
            int entryX = leftPos + 10;
            int entryW = WIDTH - 20;

            // 【性能优化】如果条目完全在可视区域外，跳过渲染
            if (entryY + 25 < visibleAreaTop || entryY > visibleAreaBottom) {
                // 虽然不渲染，但如果有按钮，需要把按钮移到屏幕外（防止误触）
                if (entry.dispatchBtn != null) {
                    entry.dispatchBtn.setPosition(-100, -100);
                }
                continue;
            }

            // 画条目背景
            int bgColor = entry.state == MaidState.DISPATCHED ? 0xFF3A5A3A : 0xFF444444;
            guiGraphics.fill(entryX, entryY, entryX + entryW, entryY + 25, bgColor);

            // 1. 女仆名字
            guiGraphics.drawString(this.font, entry.maidName, entryX + 5, entryY + 7, 0xFFFFFFFF, false);

            // 2. 工作模式文本
            int buttonLeftEdge = entryX + entryW - 50;
            int areaRightX = buttonLeftEdge - 2;
            int areaLeftX = areaRightX - WORK_MODE_AREA_WIDTH;
            int textY = entryY + 7;

            Component workModeComponent = getMaidWorkModeText(entry);
            int textWidth = this.font.width(workModeComponent);

            if (textWidth > WORK_MODE_AREA_WIDTH) {
                // 超出滚动
                guiGraphics.enableScissor(areaLeftX, entryY, areaRightX, entryY + 25); // 嵌套Scissor是允许的

                entry.scrollTimer += partialTick;
                float scrollSpeed = 1.0F;
                int pauseTime = 30;
                int totalScrollDistance = textWidth - WORK_MODE_AREA_WIDTH;

                if (entry.scrollTimer > pauseTime) {
                    entry.scrollOffset = (entry.scrollTimer - pauseTime) * scrollSpeed;
                    if (entry.scrollOffset > totalScrollDistance + pauseTime) {
                        entry.scrollTimer = 0;
                        entry.scrollOffset = 0;
                    }
                }
                entry.scrollOffset = Mth.clamp(entry.scrollOffset, 0, totalScrollDistance);

                int drawX = areaLeftX - (int)entry.scrollOffset;
                guiGraphics.drawString(this.font, workModeComponent, drawX, textY, 0xFFFFFFFF, false);

                guiGraphics.disableScissor(); // 关闭嵌套的Scissor
            } else {
                // 未超出右对齐
                int textX = areaRightX - textWidth;
                guiGraphics.drawString(this.font, workModeComponent, textX, textY, 0xFFFFFFFF, false);
            }

            // 3. 更新按钮位置（也要加上滚动偏移！）
            if (entry.dispatchBtn != null) {
                entry.dispatchBtn.setPosition(entryX + entryW - 50, entryY + 3);
                // 确保按钮在可视区域内才可见（可选）
                entry.dispatchBtn.visible = true;
            }
        }

        // 关闭列表的Scissor
        guiGraphics.disableScissor();

        // =========================================================================
        // 【可选新增】绘制滚动条
        // =========================================================================
        int contentHeight = this.maidEntries.size() * ENTRY_HEIGHT;
        int visibleHeight = visibleAreaBottom - visibleAreaTop;
        if (contentHeight > visibleHeight) {
            // 滚动条轨道
            int scrollBarX = leftPos + WIDTH - 6;
            int scrollBarY = visibleAreaTop;
            int scrollBarHeight = visibleHeight;
            guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + 4, scrollBarY + scrollBarHeight, 0xFF111111);

            // 滚动条滑块
            float sliderHeightRatio = (float) visibleHeight / contentHeight;
            int sliderHeight = (int) (sliderHeightRatio * visibleHeight);
            sliderHeight = Math.max(sliderHeight, 10); // 滑块最小高度

            float sliderOffsetRatio = (float) scrollOffset / (contentHeight - visibleHeight);
            int sliderY = (int) (scrollBarY + sliderOffsetRatio * (visibleHeight - sliderHeight));

            guiGraphics.fill(scrollBarX, sliderY, scrollBarX + 4, sliderY + sliderHeight, 0xFFFFFFFF);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private enum MaidState { NORMAL, DISPATCHED }

    private static class MaidEntry {
        public final UUID maidId;
        public final String maidName;
        public final int index;
        public final EntityMaid maid;
        public Button dispatchBtn;
        public MaidState state = MaidState.NORMAL;
        public float scrollTimer = 0;
        public float scrollOffset = 0;

        public MaidEntry(UUID maidId, String maidName, int index, EntityMaid maid) {
            this.maidId = maidId;
            this.maidName = maidName;
            this.index = index;
            this.maid = maid;
        }
    }
}