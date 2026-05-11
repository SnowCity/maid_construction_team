package com.snowcity.maid_construction_team.client.screen;

import com.snowcity.maid_construction_team.api.labor.*;
import com.snowcity.maid_construction_team.api.servant.IServantModelProvider;
import com.snowcity.maid_construction_team.api.servant.ServantModelRegistry;
import com.snowcity.maid_construction_team.network.payload.labor.DispatchLaborPayload;
import com.snowcity.maid_construction_team.network.payload.labor.RecallLaborPayload;
import com.snowcity.maid_construction_team.network.payload.labor.RequestLaborListPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 花名册 GUI —— 劳动力管理总界面。
 * <p>
 * 功能：展示所有已注册劳动力提供者扫描出的劳动力，支持分页、搜索、来源过滤，
 * 以及派遣（打开会话选择界面）和召回操作。
 */
public class RosterScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(RosterScreen.class);

    // ===== 布局常量 =====
    private static final int ENTRY_HEIGHT = 32;// 每行高度
    private static final int LIST_TOP = 90;// 列表顶部 Y 坐标
    private static final int VISIBLE_ROWS = 6;// 固定可见行数

    // ===== 数据 =====
    private final Player player;
    /** 所有劳动力（未过滤） */
    private List<LaborInfo> allLabor = new ArrayList<>();
    /** 过滤后的劳动力列表 */
    private List<LaborInfo> filteredLabor = new ArrayList<>();
    /** 当前页码 (0-based) */
    private int currentPage = 0;
    /** 总页数 */
    private int totalPages = 0;

    // ===== 搜索与过滤 =====
    private EditBox searchBox;
    private String searchText = "";
    private int selectedSourceIndex = 0;          // 0 = 全部, 1..N = 各提供者
    private List<String> sourceOptions = new ArrayList<>(); // 来源选项文本

    // ===== 控件 =====
    private Button btnPrevPage;
    private Button btnNextPage;
    private Button btnClose;
    private Button btnFilter;                     // 来源过滤按钮

    // 动态按钮：每条劳动力对应的派遣/召回按钮，需要每帧更新位置和可见性
    private final Map<UUID, Button> dispatchButtons = new HashMap<>();
    private final Map<UUID, Button> recallButtons = new HashMap<>();

    public RosterScreen() {
        super(Component.literal("花名册"));
        this.player = Minecraft.getInstance().player;
    }

    @Override
    protected void init() {
        LOGGER.info("[Roster] Opening RosterScreen...");
        super.init();

        // ---- 搜索框 ----
        searchBox = new EditBox(font, 10, 48, 120, 16, Component.literal("搜索"));
        searchBox.setValue(searchText);
        searchBox.setResponder(text -> {
            searchText = text;
            applyFilters();
        });
        addRenderableWidget(searchBox);

        // ---- 来源过滤按钮 ----
        sourceOptions.clear();
        sourceOptions.add("全部");
        LaborProviderRegistry.getEnabledProviders().forEach(p -> sourceOptions.add(p.getDisplayName()));
        btnFilter = addRenderableWidget(
                Button.builder(Component.literal(sourceOptions.get(selectedSourceIndex)), btn -> {
                    selectedSourceIndex = (selectedSourceIndex + 1) % sourceOptions.size();
                    btn.setMessage(Component.literal(sourceOptions.get(selectedSourceIndex)));
                    applyFilters();
                }).pos(140, 48).size(60, 16).build()
        );

        // ---- 关闭按钮 ----
        btnClose = addRenderableWidget(
                Button.builder(Component.literal("关闭"), btn -> onClose())
                        .pos(width - 60, height - 30).size(50, 20).build()
        );

        // ---- 翻页按钮 ----
        btnPrevPage = addRenderableWidget(
                Button.builder(Component.literal("< 上一页"), btn -> {
                    if (currentPage > 0) {
                        currentPage--;
                        updatePageButtons();
                    }
                }).pos(width / 2 - 80, height - 30).size(60, 20).build()
        );
        btnNextPage = addRenderableWidget(
                Button.builder(Component.literal("下一页 >"), btn -> {
                    if (currentPage < totalPages - 1) {
                        currentPage++;
                        updatePageButtons();
                    }
                }).pos(width / 2 + 20, height - 30).size(60, 20).build()
        );

        // 扫描宠物按钮
        addRenderableWidget(
                Button.builder(Component.literal("刷新列表"), btn -> {
                    // 向服务端请求重新扫描宠物（重新调用 PetProvider）
                    PacketDistributor.sendToServer(new RequestLaborListPayload());
                    player.sendSystemMessage(Component.literal("正在重新扫描..."));
                }).pos(width / 2 - 35, 22).size(60, 16).build()
        );

        // 初次扫描所有劳动力
        refreshAllLabor();
        applyFilters();
    }

    // ==================== 数据刷新 ====================

    /** 向服务端请求最新的劳动力列表 */
    private void refreshAllLabor() {
        LOGGER.info("[Roster] Sending RequestLaborListPayload...");
        PacketDistributor.sendToServer(new RequestLaborListPayload());
    }

    public void updateLaborList(List<LaborInfo> laborInfos) {
        allLabor = laborInfos;
        applyFilters();
    }

    /** 应用搜索文本和来源过滤，重新分页。 */
    private void applyFilters() {
        String lowerSearch = searchText.toLowerCase();

        filteredLabor = allLabor.stream()
                .filter(info -> {
                    // 来源过滤
                    if (selectedSourceIndex > 0) {
                        String expectedSource = sourceOptions.get(selectedSourceIndex);
                        if (!info.sourceDisplayName().equals(expectedSource)) {
                            return false;
                        }
                    }
                    // 文本搜索（名称、来源、会话名）
                    if (!lowerSearch.isEmpty()) {
                        return info.displayName().toLowerCase().contains(lowerSearch) ||
                                info.sourceDisplayName().toLowerCase().contains(lowerSearch) ||
                                info.workingSessionName().map(s -> s.toLowerCase().contains(lowerSearch)).orElse(false);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 重新计算分页
        totalPages = Math.max(1, (int) Math.ceil((double) filteredLabor.size() / VISIBLE_ROWS));
        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }
        updatePageButtons();
    }

    /** 根据当前页更新按钮状态并清理所有动态派遣/召回按钮。 */
    private void updatePageButtons() {
        btnPrevPage.active = currentPage > 0;
        btnNextPage.active = currentPage < totalPages - 1;

        // 移除所有旧按钮（避免幽灵按钮）
        dispatchButtons.values().forEach(this::removeWidget);
        recallButtons.values().forEach(this::removeWidget);
        dispatchButtons.clear();
        recallButtons.clear();

        // 为当前页的每条劳动力创建对应的操作按钮
        int start = currentPage * VISIBLE_ROWS;
        int end = Math.min(filteredLabor.size(), start + VISIBLE_ROWS);
        for (int i = start; i < end; i++) {
            LaborInfo info = filteredLabor.get(i);

            if (info.status() == LaborStatus.IDLE) {
                // 派遣按钮
                Button btnDispatch = Button.builder(Component.literal("派遣"), btn -> {
                    Minecraft.getInstance().setScreen(new SessionSelectionScreen(session -> {
                        PacketDistributor.sendToServer(new DispatchLaborPayload(info.laborId(), info.sourceType(), session.getSessionId()));
                        // 请求刷新列表
                        PacketDistributor.sendToServer(new RequestLaborListPayload());
                    }));
                }).pos(0, 0).size(40, 16).build();
                btnDispatch.visible = false; // 初始不可见，render 中统一控制
                addRenderableWidget(btnDispatch);
                dispatchButtons.put(info.laborId(), btnDispatch);
            } else if (info.status() == LaborStatus.WORKING) {
                // 召回按钮
                Button btnRecall = Button.builder(Component.literal("召回"), btn -> {
                    PacketDistributor.sendToServer(new RecallLaborPayload(info.laborId(), info.sourceType()));
                    PacketDistributor.sendToServer(new RequestLaborListPayload());
                }).pos(0, 0).size(40, 16).build();
                btnRecall.visible = false;
                addRenderableWidget(btnRecall);
                recallButtons.put(info.laborId(), btnRecall);
            }
            // 离线状态不创建按钮
        }
    }

    // ==================== 渲染 ====================

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // ---- 标题与统计 ----
        String title = "花名册";
        long idleCount = allLabor.stream().filter(i -> i.status() == LaborStatus.IDLE).count();
        long workingCount = allLabor.stream().filter(i -> i.status() == LaborStatus.WORKING).count();
        long offlineCount = allLabor.stream().filter(i -> i.status() == LaborStatus.OFFLINE).count();
        String stats = String.format("(总计: %d, 空闲: %d, 工作中: %d, 离线: %d)",
                allLabor.size(), idleCount, workingCount, offlineCount);
        graphics.drawString(font, title + " " + stats, 10, 10, 0xFFFFFF);

        // 默认命名规则提示
        graphics.drawString(font, "默认命名规则提示: 生物简写-序号（如 Zom-001），可在契约之书中修改", 10, 22, 0xAAAAAA);

        // ---- 搜索框与过滤按钮标签 ----
        graphics.drawString(font, "搜索:", 10, 38, 0xAAAAAA);
        graphics.drawString(font, "来源:", 140, 38, 0xAAAAAA);

        // 列表区域（固定高度，避免半行）
        int listBottom = LIST_TOP + VISIBLE_ROWS * ENTRY_HEIGHT;
        graphics.enableScissor(10, LIST_TOP, width - 10, listBottom);

// 当前页的起始行
        int start = currentPage * VISIBLE_ROWS;
        int end = Math.min(filteredLabor.size(), start + VISIBLE_ROWS);

        for (int i = start; i < end; i++) {
            LaborInfo info = filteredLabor.get(i);
            int y = LIST_TOP + (i - start) * ENTRY_HEIGHT;

            // 背景悬停高亮
            boolean hovered = mouseY >= y && mouseY < y + ENTRY_HEIGHT && mouseX >= 10 && mouseX < width - 10;
            graphics.fill(10, y, width - 10, y + ENTRY_HEIGHT, hovered ? 0x44FFFFFF : 0x22FFFFFF);

            // 图标
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(info.entityType());
            if (entityType != null) {
                IServantModelProvider modelProvider = ServantModelRegistry.getProvider(entityType);
                ResourceLocation icon = modelProvider.getIcon(entityType, null, null);
                // 简化文字占位
                graphics.drawString(font, "[●]", 14, y + 8, 0xFFFFFF);
            }

            // 名称
            graphics.drawString(font, info.displayName(), 28, y + 4, 0xFFFFFF);

            // 生物种类（灰色小字）
            if (entityType != null) {
                String typeName = entityType.getDescription().getString();
                graphics.drawString(font, typeName, 28, y + 18, 0x888888);
            }

            // 来源
            graphics.drawString(font, info.sourceDisplayName(), 100, y + 6, 0xAAAAAA);

            // 状态文字
            String statusText = switch (info.status()) {
                case IDLE -> "空闲";
                case WORKING -> "工作中";
                case OFFLINE -> "离线";
            };
            int statusColor = switch (info.status()) {
                case IDLE -> 0x00FF00;
                case WORKING -> 0xFFAA00;
                case OFFLINE -> 0xFF5555;
            };
            graphics.drawString(font, statusText, 160, y + 6, statusColor);

            // 关联会话名
            info.workingSessionName().ifPresent(sessionName ->
                    graphics.drawString(font, " → " + sessionName, 220, y + 6, 0xAAAAAA)
            );

            // 动态按钮：根据状态展示派遣或召回按钮
            int btnY = y + (ENTRY_HEIGHT - 16) / 2; // 垂直居中
            if (info.status() == LaborStatus.IDLE) {
                Button btnDispatch = dispatchButtons.get(info.laborId());
                if (btnDispatch != null) {
                    btnDispatch.setPosition(width - 60, btnY);
                    btnDispatch.visible = true;
                }
            } else if (info.status() == LaborStatus.WORKING) {
                Button btnRecall = recallButtons.get(info.laborId());
                if (btnRecall != null) {
                    btnRecall.setPosition(width - 60, btnY);
                    btnRecall.visible = true;
                }
            }
        }

        // 确保不可见行的按钮隐藏
        for (Map.Entry<UUID, Button> entry : dispatchButtons.entrySet()) {
            if (!isInCurrentPage(entry.getKey())) {
                entry.getValue().visible = false;
            }
        }
        for (Map.Entry<UUID, Button> entry : recallButtons.entrySet()) {
            if (!isInCurrentPage(entry.getKey())) {
                entry.getValue().visible = false;
            }
        }

        graphics.disableScissor();

        // ---- 页码显示 ----
        String pageInfo = (currentPage + 1) + " / " + totalPages;
        graphics.drawString(font, pageInfo, width / 2 - font.width(pageInfo) / 2, height - 20, 0xCCCCCC);
    }

    // ==================== 输入处理 ====================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && currentPage > 0) {
            currentPage--;
            updatePageButtons();
        } else if (scrollY < 0 && currentPage < totalPages - 1) {
            currentPage++;
            updatePageButtons();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchBox.setFocused(false);
                return true;
            }
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchBox.isFocused() && !isMouseOverSearchBox(mouseX, mouseY)) {
            searchBox.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOverSearchBox(double mouseX, double mouseY) {
        return mouseX >= searchBox.getX() && mouseX <= searchBox.getX() + searchBox.getWidth()
                && mouseY >= searchBox.getY() && mouseY <= searchBox.getY() + searchBox.getHeight();
    }

    @Override
    public void onClose() {
        // 清理动态按钮
        dispatchButtons.values().forEach(this::removeWidget);
        recallButtons.values().forEach(this::removeWidget);
        dispatchButtons.clear();
        recallButtons.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ========================= 工具方法 =========================

    private boolean isInCurrentPage(UUID laborId) {
        int start = currentPage * VISIBLE_ROWS;
        int end = Math.min(filteredLabor.size(), start + VISIBLE_ROWS);
        for (int i = start; i < end; i++) {
            if (filteredLabor.get(i).laborId().equals(laborId)) {
                return true;
            }
        }
        return false;
    }
}