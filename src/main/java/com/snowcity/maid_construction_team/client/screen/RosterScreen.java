package com.snowcity.maid_construction_team.client.screen;

import com.snowcity.maid_construction_team.api.labor.*;
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
 * 花名册 GUI —— 劳动力管理总界面，支持批量派遣。
 * <p>
 * 功能：
 * <ul>
 *   <li>展示所有已注册劳动力提供者扫描出的劳动力，支持分页、搜索、来源过滤。</li>
 *   <li>单条派遣/召回操作（原有功能）。</li>
 *   <li><b>新增批量派遣：</b>
 *       <ul>
 *           <li>多选复选框：点击每个空闲劳动力左侧的方框进行勾选。</li>
 *           <li>全选/取消全选按钮：一键选中所有空闲劳动力或清空选择。</li>
 *           <li>派遣选中按钮：将选中的所有劳动力批量派遣到指定建造会话。</li>
 *       </ul>
 *   </li>
 * </ul>
 */
public class RosterScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(RosterScreen.class);

    // ===== 布局常量 =====
    private static final int ENTRY_HEIGHT = 32;
    private static final int LIST_TOP = 90;
    private static final int VISIBLE_ROWS = 6;

    // ===== 数据 =====
    private final Player player;
    private List<LaborInfo> allLabor = new ArrayList<>();
    private List<LaborInfo> filteredLabor = new ArrayList<>();
    private int currentPage = 0;
    private int totalPages = 0;

    // ===== 搜索与过滤 =====
    private EditBox searchBox;
    private String searchText = "";
    private int selectedSourceIndex = 0;          // 0 = 全部, 1..N = 各提供者
    private final List<String> sourceOptions = new ArrayList<>();

    // ===== 控件 =====
    private Button btnPrevPage;
    private Button btnNextPage;
    private Button btnClose;
    private Button btnFilter;

    // 动态派遣/召回按钮
    private final Map<UUID, Button> dispatchButtons = new HashMap<>();
    private final Map<UUID, Button> recallButtons = new HashMap<>();

    // ===== 批量派遣 =====
    /** 当前选中的劳动力 UUID 集合（多选） */
    private final Set<UUID> selectedLaborIds = new HashSet<>();
    private Button btnSelectAll;           // 全选/取消全选
    private Button btnDispatchSelected;    // 派遣选中

    public RosterScreen() {
        super(Component.translatable("mct.screen.roster.title"));
        this.player = Minecraft.getInstance().player;
    }

    @Override
    protected void init() {
        LOGGER.info("[Roster] Opening RosterScreen...");
        super.init();

        // ---- 搜索框 ----
        searchBox = new EditBox(font, 10, 48, 120, 16, Component.translatable("mct.screen.search"));
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
                Button.builder(Component.literal(sourceOptions.get(selectedSourceIndex)), b -> {
                    selectedSourceIndex = (selectedSourceIndex + 1) % sourceOptions.size();
                    b.setMessage(Component.literal(sourceOptions.get(selectedSourceIndex)));
                    applyFilters();
                }).pos(140, 48).size(60, 16).build()
        );

        // ---- 关闭按钮 ----
        btnClose = addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.close"), btn -> onClose())
                        .pos(width - 60, height - 30).size(50, 20).build()
        );

        // ---- 翻页按钮 ----
        btnPrevPage = addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.previous_page"), btn -> {
                    if (currentPage > 0) {
                        currentPage--;
                        updatePageButtons();
                    }
                }).pos(width / 2 - 80, height - 30).size(60, 20).build()
        );
        btnNextPage = addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.next_page"), btn -> {
                    if (currentPage < totalPages - 1) {
                        currentPage++;
                        updatePageButtons();
                    }
                }).pos(width / 2 + 20, height - 30).size(60, 20).build()
        );

        // ---- 刷新列表按钮 ----
        addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.refresh"), btn -> {
                    PacketDistributor.sendToServer(new RequestLaborListPayload());
                    player.sendSystemMessage(Component.translatable("mct.message.scanning"));
                }).pos(width / 2 - 35, 22).size(60, 16).build()
        );

        // ---- 批量派遣按钮 ----
        btnSelectAll = addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.select_all"), btn -> {
                    // 计算当前显示的空闲劳动力总数
                    long idleCount = allLabor.stream().filter(i -> i.status() == LaborStatus.IDLE).count();
                    if (selectedLaborIds.size() == idleCount) {
                        // 已全选则取消全选
                        selectedLaborIds.clear();
                    } else {
                        // 选中所有空闲劳动力
                        for (LaborInfo info : allLabor) {
                            if (info.status() == LaborStatus.IDLE) {
                                selectedLaborIds.add(info.laborId());
                            }
                        }
                    }
                    updateSelectButtonText();
                }).pos(width - 180, 22).size(60, 16).build()
        );

        btnDispatchSelected = addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.dispatch_selected"), btn -> {
                    if (selectedLaborIds.isEmpty()) {
                        player.sendSystemMessage(Component.translatable("mct.message.no_labor_selected"));
                        return;
                    }
                    // 打开会话选择，选择后批量派遣
                    Minecraft.getInstance().setScreen(new SessionSelectionScreen(session -> {
                        for (UUID laborId : selectedLaborIds) {
                            allLabor.stream()
                                    .filter(i -> i.laborId().equals(laborId))
                                    .findFirst().ifPresent(info -> PacketDistributor.sendToServer(
                                            new DispatchLaborPayload(laborId, info.sourceType(), session.getSessionId())));
                        }
                        selectedLaborIds.clear();
                        // 刷新列表（向服务端请求最新数据）
                        PacketDistributor.sendToServer(new RequestLaborListPayload());
                    }));
                }).pos(width - 100, 22).size(60, 16).build()
        );

        // 初次扫描
        refreshAllLabor();
        applyFilters();
    }

    // ==================== 数据刷新 ====================

    /** 重新扫描所有提供者，重建全部劳动力列表。 */
    private void refreshAllLabor() {
        LOGGER.info("[Roster] Sending RequestLaborListPayload...");
        PacketDistributor.sendToServer(new RequestLaborListPayload());
    }

    /** 收到服务端劳动力列表后更新本地数据 */
    public void updateLaborList(List<LaborInfo> laborInfos) {
        allLabor = laborInfos;
        // 移除已经不在列表中的选中 ID
        Set<UUID> currentIds = allLabor.stream().map(LaborInfo::laborId).collect(Collectors.toSet());
        selectedLaborIds.retainAll(currentIds);
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
                    // 文本搜索
                    if (!lowerSearch.isEmpty()) {
                        return info.displayName().toLowerCase().contains(lowerSearch) ||
                                info.sourceDisplayName().toLowerCase().contains(lowerSearch) ||
                                info.workingSessionName().map(s -> s.toLowerCase().contains(lowerSearch)).orElse(false);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        totalPages = Math.max(1, (int) Math.ceil((double) filteredLabor.size() / VISIBLE_ROWS));
        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }
        updatePageButtons();
        updateSelectButtonText();
    }

    /** 根据当前页更新按钮状态，并为可见行创建派遣/召回按钮。 */
    private void updatePageButtons() {
        btnPrevPage.active = currentPage > 0;
        btnNextPage.active = currentPage < totalPages - 1;

        // 移除所有旧动态按钮
        dispatchButtons.values().forEach(this::removeWidget);
        recallButtons.values().forEach(this::removeWidget);
        dispatchButtons.clear();
        recallButtons.clear();

        int start = currentPage * VISIBLE_ROWS;
        int end = Math.min(filteredLabor.size(), start + VISIBLE_ROWS);
        for (int i = start; i < end; i++) {
            LaborInfo info = filteredLabor.get(i);

            if (info.status() == LaborStatus.IDLE) {
                Button btnDispatch = Button.builder(Component.translatable("mct.screen.dispatch"), btn -> {
                    Minecraft.getInstance().setScreen(new SessionSelectionScreen(session -> {
                        PacketDistributor.sendToServer(new DispatchLaborPayload(info.laborId(), info.sourceType(), session.getSessionId()));
                        PacketDistributor.sendToServer(new RequestLaborListPayload());
                    }));
                }).pos(0, 0).size(40, 16).build();
                btnDispatch.visible = false;
                addRenderableWidget(btnDispatch);
                dispatchButtons.put(info.laborId(), btnDispatch);
            } else if (info.status() == LaborStatus.WORKING) {
                Button btnRecall = Button.builder(Component.translatable("mct.screen.recall"), btn -> {
                    PacketDistributor.sendToServer(new RecallLaborPayload(info.laborId(), info.sourceType()));
                    PacketDistributor.sendToServer(new RequestLaborListPayload());
                }).pos(0, 0).size(40, 16).build();
                btnRecall.visible = false;
                addRenderableWidget(btnRecall);
                recallButtons.put(info.laborId(), btnRecall);
            }
        }
    }

    // ==================== 渲染 ====================

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // 标题与统计
        String title = "花名册";
        long idleCount = allLabor.stream().filter(i -> i.status() == LaborStatus.IDLE).count();
        long workingCount = allLabor.stream().filter(i -> i.status() == LaborStatus.WORKING).count();
        long offlineCount = allLabor.stream().filter(i -> i.status() == LaborStatus.OFFLINE).count();
        String stats = String.format("(总计: %d, 空闲: %d, 工作中: %d, 离线: %d)",
                allLabor.size(), idleCount, workingCount, offlineCount);
        graphics.drawString(font, title + " " + stats, 10, 10, 0xFFFFFF);
//        graphics.drawString(font, "默认命名规则提示: 生物简写-序号（如 Zom-001），可在契约之书中修改", 10, 22, 0xAAAAAA);

        // 搜索与过滤标签
        graphics.drawString(font, "搜索:", 10, 38, 0xAAAAAA);
        graphics.drawString(font, "来源:", 140, 38, 0xAAAAAA);

        // 列表区域
        int listBottom = LIST_TOP + VISIBLE_ROWS * ENTRY_HEIGHT;
        graphics.enableScissor(10, LIST_TOP, width - 10, listBottom);

        int start = currentPage * VISIBLE_ROWS;
        int end = Math.min(filteredLabor.size(), start + VISIBLE_ROWS);

        for (int i = start; i < end; i++) {
            LaborInfo info = filteredLabor.get(i);
            int y = LIST_TOP + (i - start) * ENTRY_HEIGHT;

            // 背景悬停高亮
            boolean hovered = mouseY >= y && mouseY < y + ENTRY_HEIGHT && mouseX >= 10 && mouseX < width - 10;
            graphics.fill(10, y, width - 10, y + ENTRY_HEIGHT, hovered ? 0x44FFFFFF : 0x22FFFFFF);

            // ---- 复选框（仅空闲劳动力可勾选） ----
            if (info.status() == LaborStatus.IDLE) {
                int checkX = 14, checkY = y + 8, checkSize = 10;
                boolean checked = selectedLaborIds.contains(info.laborId());
                // 绘制复选框背景
                graphics.fill(checkX, checkY, checkX + checkSize, checkY + checkSize, checked ? 0xFF00FF00 : 0xFF888888);
                if (checked) {
                    // 白色勾号
                    graphics.drawString(font, "✓", checkX - 1, checkY - 2, 0xFFFFFFFF);
                }
            }

            // ---- 图标（占位） ----
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(info.entityType());
            if (entityType != null) {
                // 这里可以使用 ServantModelRegistry 获取图标，为简化绘制 "[●]"
                graphics.drawString(font, "[●]", 28, y + 8, 0xFFFFFF);
            }

            // ---- 名称 ----
            graphics.drawString(font, info.displayName(), 42, y + 4, 0xFFFFFF);

            // ---- 生物种类 ----
            if (entityType != null) {
                String typeName = entityType.getDescription().getString();
                graphics.drawString(font, typeName, 42, y + 18, 0x888888);
            }

            // ---- 来源 ----
            graphics.drawString(font, info.sourceDisplayName(), 110, y + 6, 0xAAAAAA);

            // ---- 状态文字 ----
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
            graphics.drawString(font, statusText, 170, y + 6, statusColor);

            // ---- 关联会话名 ----
            info.workingSessionName().ifPresent(sessionName ->
                    graphics.drawString(font, " → " + sessionName, 230, y + 6, 0xAAAAAA)
            );

            // ---- 动态派遣/召回按钮 ----
            int btnY = y + (ENTRY_HEIGHT - 16) / 2;
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

        // 隐藏不可见行的按钮
        Set<UUID> visibleIds = new HashSet<>();
        for (int i = start; i < end; i++) {
            visibleIds.add(filteredLabor.get(i).laborId());
        }
        dispatchButtons.forEach((id, btn) -> { if (!visibleIds.contains(id)) btn.visible = false; });
        recallButtons.forEach((id, btn) -> { if (!visibleIds.contains(id)) btn.visible = false; });

        graphics.disableScissor();

        // 页码
        String pageInfo = (currentPage + 1) + " / " + totalPages;
        graphics.drawString(font, pageInfo, width / 2 - font.width(pageInfo) / 2, height - 20, 0xCCCCCC);
    }

    // ==================== 输入处理 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // 左键
            int start = currentPage * VISIBLE_ROWS;
            int end = Math.min(filteredLabor.size(), start + VISIBLE_ROWS);
            for (int i = start; i < end; i++) {
                int y = LIST_TOP + (i - start) * ENTRY_HEIGHT;
                // 复选框点击判定（14~24, y+8~y+18）
                if (mouseX >= 14 && mouseX <= 24 && mouseY >= y + 8 && mouseY <= y + 18) {
                    LaborInfo info = filteredLabor.get(i);
                    if (info.status() == LaborStatus.IDLE) {
                        if (selectedLaborIds.contains(info.laborId())) {
                            selectedLaborIds.remove(info.laborId());
                        } else {
                            selectedLaborIds.add(info.laborId());
                        }
                        updateSelectButtonText();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

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
    public void onClose() {
        dispatchButtons.values().forEach(this::removeWidget);
        recallButtons.values().forEach(this::removeWidget);
        dispatchButtons.clear();
        recallButtons.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ==================== 工具方法 ====================

    /** 更新全选按钮的文本 */
    private void updateSelectButtonText() {
        long idleCount = allLabor.stream().filter(i -> i.status() == LaborStatus.IDLE).count();
        if (selectedLaborIds.size() == idleCount && idleCount > 0) {
            btnSelectAll.setMessage(Component.translatable("mct.screen.deselect_all"));
        } else {
            btnSelectAll.setMessage(Component.translatable("mct.screen.select_all"));
        }
    }

    public Button getBtnClose() {
        return btnClose;
    }

    public Button getBtnFilter() {
        return btnFilter;
    }

    public Button getBtnDispatchSelected() {
        return btnDispatchSelected;
    }
}