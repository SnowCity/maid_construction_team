package com.snowcity.maid_construction_team.client.screen;

import com.snowcity.maid_construction_team.client.cache.ClientSessionCache;
import com.snowcity.maid_construction_team.component.ContractBookData;
import com.snowcity.maid_construction_team.component.ServantContractData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.item.MaidConstructionTeamItems;
import com.snowcity.maid_construction_team.item.custom.ContractBookItem;
import com.snowcity.maid_construction_team.network.payload.ModifyContractBookPayload;
import com.snowcity.maid_construction_team.network.payload.labor.DispatchLaborPayload;
import com.snowcity.maid_construction_team.network.payload.labor.RecallLaborPayload;
import com.snowcity.maid_construction_team.network.payload.session.RequestSessionsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 契约之书的管理模式 GUI。
 * <p>
 * 提供契约列表、搜索过滤、重命名、单张取出/存入/召回、
 * 全部存入、全部取出等功能。
 * <p>
 * 列表固定显示 10 行，整行滚动，所有按钮仅当对应行在可视区域内时才显示和激活。
 */
public class ContractBookScreen extends Screen {

    private ItemStack bookStack;
    private final InteractionHand hand;          // 手持的交互手，用于同步物品栈
    private ContractBookData bookData;

    // 搜索
    private EditBox searchBox;
    private String searchText = "";

    // 可见参数
    private static final int VISIBLE_ROWS = 10;              // 固定可见行数
    private static final int ENTRY_HEIGHT = 22;               // 每行高度
    private static final int LIST_TOP = 65;                   // 列表顶部 Y 坐标

    // 列表数据（过滤后的）
    private final List<ContractBookData.ContractEntry> filteredEntries = new ArrayList<>();

    // 滚动偏移量（像素，负值表示向上滚动）
    private int scrollOffset = 0;

    // 全部存入 / 全部取出按钮
    private Button btnStoreAll;
    private Button btnTakeAll;

    // 每条契约对应的按钮集合：key = contractId, value = [取出/召回按钮, 重命名按钮]
    private final Map<UUID, List<Button>> entryButtons = new HashMap<>();
    private final Map<UUID, Boolean> pendingChanges = new HashMap<>();

    // 重命名覆盖层
    private boolean isRenaming = false;
    private ContractBookData.ContractEntry renamingEntry;
    private EditBox renameEditBox;
    private Button renameConfirmBtn;
    private Button renameCancelBtn;

    public ContractBookScreen(ItemStack bookStack, InteractionHand hand) {
        super(Component.translatable("mct.screen.contract_book.title"));
        this.bookStack = bookStack;
        this.hand = hand;
        this.bookData = ContractBookItem.getData(bookStack);
    }

    @Override
    protected void init() {
        super.init();

        // 搜索框
        searchBox = new EditBox(font, 10, 46, 150, 16, Component.translatable("mct.screen.search"));
        searchBox.setValue(searchText);
        searchBox.setResponder(text -> {
            searchText = text;
            rebuildFilteredList();
        });
        addRenderableWidget(searchBox);

        // 全部存入按钮
        btnStoreAll = addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.store_all"), btn -> PacketDistributor.sendToServer(new ModifyContractBookPayload((byte)0, UUID.randomUUID(), Optional.empty(), hand))).pos(width - 80, 30).size(70, 20).build()
        );

        // 全部取出按钮
        btnTakeAll = addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.take_all"), btn -> PacketDistributor.sendToServer(new ModifyContractBookPayload((byte)1, UUID.randomUUID(), Optional.empty(), hand))).pos(width / 2 - 35, height - 30).size(70, 20).build()
        );

        // 重命名编辑框（初始隐藏）
        renameEditBox = new EditBox(font, width / 2 - 80, height / 2 - 10, 160, 20, Component.translatable("mct.screen.new_name"));
        renameEditBox.setVisible(false);
        addRenderableWidget(renameEditBox);

        // 重命名确认按钮
        renameConfirmBtn = addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.enter"), btn -> confirmRename())
                        .pos(width / 2 - 85, height / 2 + 15).size(60, 20).build()
        );
        renameConfirmBtn.visible = false;

        // 重命名取消按钮
        renameCancelBtn = addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.esc"), btn -> cancelRename())
                        .pos(width / 2 - 20, height / 2 + 15).size(60, 20).build()
        );
        renameCancelBtn.visible = false;

        // 初始刷新列表
        rebuildFilteredList();

        // 在 init() 末尾添加
        addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.division_responsibilities_manual"), btn -> Minecraft.getInstance().setScreen(new ContractGuideScreen(this)))
                        .pos(10, height - 50) // 左下角
                        .size(60, 20)
                        .build()
        );

        // 请求会话列表，用于填充缓存（仅当缓存为空时请求，避免重复）
        if (ClientSessionCache.isEmpty()) {
            PacketDistributor.sendToServer(new RequestSessionsPayload(Optional.empty()));
        }
    }

    /**
     * 重建过滤后的列表，并为每个条目创建操作按钮。
     * 调用后会自动对齐滚动偏移，并更新按钮可见性。
     */
    private void rebuildFilteredList() {
        // 确保 bookStack 与服务端同步后的物品一致
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof ContractBookItem) {
                this.bookStack = held;
            }
        }
        // 重新读取数据，确保与服务端同步
        this.bookData = ContractBookItem.getData(bookStack);
        // 移除所有旧按钮，并清空映射
        for (List<Button> btns : entryButtons.values()) {
            for (Button btn : btns) {
                removeWidget(btn);
            }
        }
        entryButtons.clear();
        filteredEntries.clear();

        String lowerSearch = searchText.toLowerCase();

        for (ContractBookData.ContractEntry entry : bookData.entries()) {
            // 获取生物类型的本地化显示名称，用于搜索匹配
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entry.entityType());
            String typeDisplayName = entityType != null ? entityType.getDescription().getString().toLowerCase() : "";

            // 判断是否符合搜索条件
            if (lowerSearch.isEmpty() ||
                    entry.servantName().toLowerCase().contains(lowerSearch) ||
                    entry.entityType().toString().toLowerCase().contains(lowerSearch) ||
                    typeDisplayName.contains(lowerSearch)) {

                filteredEntries.add(entry);

                // 创建操作按钮（与之前逻辑一致）
                List<Button> btns = new ArrayList<>();

                // 获取真实派遣状态
                boolean actuallyDispatched = entry.dispatchedSessionId().isPresent();
                UUID contractId = entry.contractId();

                // 查看是否有本地乐观覆盖
                Boolean optimistic = pendingChanges.get(contractId);
                boolean displayAsActivated  = isDisplayedAsActivated(entry);

                // 根据 displayAsActivated 创建按钮
                if (displayAsActivated) {
                    // 激活中 → 取消激活按钮
                    Button recallBtn = Button.builder(Component.translatable("mct.screen.activate"), btn -> {
                        // 立即更新本地映射，不修改物品栈
                        pendingChanges.put(contractId, false);
                        PacketDistributor.sendToServer(new RecallLaborPayload(contractId, "servant_contract"));
                        rebuildFilteredList(); // 刷新界面
                    }).pos(0, 0).size(40, 16).build();
                    recallBtn.visible = false;
                    btns.add(recallBtn);
                    addRenderableWidget(recallBtn);
                } else {
                    // 空闲 → 激活按钮
                    Button activateBtn = Button.builder(Component.translatable("mct.screen.deactivate"), btn -> {
                        // 立即更新本地映射
                        pendingChanges.put(contractId, true);
                        // 打开会话选择
                        Minecraft.getInstance().setScreen(new SessionSelectionScreen(session -> {
                            PacketDistributor.sendToServer(new DispatchLaborPayload(contractId, "servant_contract", session.getSessionId()));

                            // 但我们仍然需要稍后刷新以显示正确的会话绑定信息，由于不修改物品栈，我们可以简单地重建列表。
//                            rebuildFilteredList();

                        }, this));
                    }).pos(0, 0).size(40, 16).build();
                    activateBtn.visible = false;
                    btns.add(activateBtn);
                    addRenderableWidget(activateBtn);
                }

                Button renameBtn = Button.builder(Component.translatable("mct.screen.rename"), btn -> {
                    isRenaming = true;
                    renamingEntry = entry;
                    renameEditBox.setValue(entry.servantName());
                    renameEditBox.setVisible(true);
                    renameEditBox.setFocused(true);
                    renameConfirmBtn.visible = true;
                    renameCancelBtn.visible = true;
                }).pos(0, 0).size(40, 16).build();
                renameBtn.visible = false;
                btns.add(renameBtn);
                addRenderableWidget(renameBtn);

                addRenderableWidget(
                        Button.builder(Component.translatable("mct.screen.refresh"), btn -> rebuildFilteredList())
                                .pos(width - 50, height - 30).size(40, 20).build()
                );

                entryButtons.put(entry.contractId(), btns);
            }
        }

        // 对齐滚动偏移量
        int totalRows = filteredEntries.size();
        int maxScrollRows = Math.max(0, totalRows - VISIBLE_ROWS);
        int currentRow = (int) Math.round((double) -scrollOffset / ENTRY_HEIGHT);
        currentRow = Math.clamp(currentRow, 0, maxScrollRows);
        scrollOffset = -currentRow * ENTRY_HEIGHT;

        updateVisibleButtons();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // 标题与容量
        String title = "契约之书 (" + bookData.entries().size() + "/" + ContractBookItem.MAX_CAPACITY + ")";
        graphics.drawString(font, title, 10, 10, 0xFFFFFF);
        // 提示
        graphics.drawString(font, "命名格式：生物简写-序号（如 Zom-001），可点击重命名修改", 10, 22, 0xAAAAAA);
        // 搜索框
        graphics.drawString(font, "搜索:", 10, 36, 0xAAAAAA);

        // 裁剪区域
        int listBottom = LIST_TOP + VISIBLE_ROWS * ENTRY_HEIGHT;
        graphics.enableScissor(10, LIST_TOP, width - 10, listBottom);

        // 计算起始行
        int startRow = (int) Math.round((double) -scrollOffset / ENTRY_HEIGHT);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int rowIndex = startRow + i;
            if (rowIndex >= filteredEntries.size()) break;

            ContractBookData.ContractEntry entry = filteredEntries.get(rowIndex);
            int y = LIST_TOP + i * ENTRY_HEIGHT;

            // 背景（悬停高亮）
            boolean hovered = mouseY >= y && mouseY < y + ENTRY_HEIGHT && mouseX >= 10 && mouseX < width - 10;
            graphics.fill(10, y, width - 10, y + ENTRY_HEIGHT, hovered ? 0x44FFFFFF : 0x22FFFFFF);

            // 图标（暂时用文字代替）
            graphics.drawString(font, "[●]", 14, y + 4, 0xFFFFFF);

            // 仆从名称
            graphics.drawString(font, entry.servantName(), 28, y + 2, 0xFFFFFF);

            // 生物类型
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entry.entityType());
            String typeName = entityType != null ? entityType.getDescription().getString() : entry.entityType().toString();
            graphics.drawString(font, "(" + typeName + ")", 28, y + 12, 0x888888);

            // 状态
            boolean displayAsActivated = isDisplayedAsActivated(entry);
            String statusLine;
            if (displayAsActivated && entry.dispatchedSessionId().isPresent()) {
                String sessionName = ClientSessionCache.getSessionName(entry.dispatchedSessionId().get());
                statusLine = "激活中 → " + sessionName;
            } else {
                statusLine = displayAsActivated ? "激活中" : "未激活";
            }
            int statusColor = displayAsActivated ? 0xFFAA00 : 0x00FF00;
            graphics.drawString(font, statusLine, 120, y + 2, statusColor);
        }

        graphics.disableScissor();

        // 底部提示
        graphics.drawString(font, "使用按钮操作 | 滚动查看全部", 10, height - 20, 0xAAAAAA);

        // 重命名覆盖层
        if (isRenaming) {
            graphics.fill(0, 0, width, height, 0x88000000);
            graphics.drawString(font, "重命名契约", width / 2 - 30, height / 2 - 25, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int totalRows = filteredEntries.size();
        if (totalRows == 0) return true;
        int maxScrollRows = Math.max(0, totalRows - VISIBLE_ROWS);
        int currentRow = (int) Math.round((double) -scrollOffset / ENTRY_HEIGHT);
        currentRow -= (int) scrollY;
        currentRow = Math.clamp(currentRow, 0, maxScrollRows);
        scrollOffset = -currentRow * ENTRY_HEIGHT;
        updateVisibleButtons();
        return true;
    }

    /**
     * 根据当前滚动偏移，更新所有条目的按钮位置和可见性。
     */
    private void updateVisibleButtons() {
        int startRow = (int) Math.round((double) -scrollOffset / ENTRY_HEIGHT);
        int endRow = Math.min(filteredEntries.size(), startRow + VISIBLE_ROWS);

        for (int i = 0; i < filteredEntries.size(); i++) {
            ContractBookData.ContractEntry entry = filteredEntries.get(i);
            List<Button> btns = entryButtons.get(entry.contractId());
            if (btns != null) {
                boolean visible = (i >= startRow && i < endRow);
                for (Button btn : btns) {
                    if (btn != null) {
                        btn.visible = visible;
                        if (visible) {
                            int y = LIST_TOP + (i - startRow) * ENTRY_HEIGHT;
                            if (btn.getMessage().getString().equals("重命名")) {
                                btn.setPosition(width - 150, y + 2);
                            } else {
                                btn.setPosition(width - 100, y + 2);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 重命名模式下，ESC 取消重命名
        if (isRenaming) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }
            if (renameEditBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return true;
        }

        // 搜索框处理
        if (searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchBox.setFocused(false);
                return true;
            }
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }

        // 非搜索框时，ESC 关闭 GUI
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
        if (isRenaming && renameEditBox.isFocused()) {
            return renameEditBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    /**
     * 点击搜索框以外的区域时，让搜索框失去焦点。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isRenaming) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
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
        // 清理动态按钮，防止内存泄漏
        pendingChanges.clear();
        for (List<Button> btns : entryButtons.values()) {
            for (Button btn : btns) {
                removeWidget(btn);
            }
        }
        entryButtons.clear();
        super.onClose();
    }

    // ==================== 业务逻辑 ====================

    private void takeOutSingle(ContractBookData.ContractEntry entry) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        if (entry.dispatchedSessionId().isPresent()) {
            player.sendSystemMessage(Component.translatable("mct.message.contract_already_active"));
            return;
        }

        ItemStack contractStack = new ItemStack(MaidConstructionTeamItems.SERVANT_CONTRACT.get());
        ServantContractData data = new ServantContractData(
                entry.contractId(), entry.entityType(), entry.servantName(),
                entry.modelVariant(), entry.dispatchedSessionId()
        );
        contractStack.set(ModDataComponents.SERVANT_CONTRACT_DATA.get(), data);

        if (!player.getInventory().add(contractStack)) {
            player.drop(contractStack, false);
        }

        List<ContractBookData.ContractEntry> newEntries = new ArrayList<>(bookData.entries());
        newEntries.remove(entry);
        bookData = new ContractBookData(newEntries);
        ContractBookItem.setData(bookStack, bookData);
        rebuildFilteredList();
    }

    private void storeAllFromInventory() {
        PacketDistributor.sendToServer(new ModifyContractBookPayload((byte)0, UUID.randomUUID(), Optional.empty(), hand));
    }

    private void takeAllFromBook() {
        PacketDistributor.sendToServer(new ModifyContractBookPayload((byte)1, UUID.randomUUID(), Optional.empty(), hand));
    }

    private void confirmRename() {
        if (renamingEntry == null || renameEditBox == null) return;
        String newName = renameEditBox.getValue().trim();
        if (!newName.isEmpty() && !newName.equals(renamingEntry.servantName())) {
            PacketDistributor.sendToServer(new ModifyContractBookPayload((byte)3, renamingEntry.contractId(), Optional.of(newName), hand));
        }
        cancelRename();
    }

    /**
     * 计算该条目在 UI 中应显示为“激活中”还是“未激活”，
     * 考虑本地乐观映射，用于即时反馈而无需修改物品栈。
     */
    private boolean isDisplayedAsActivated(ContractBookData.ContractEntry entry) {
        boolean actuallyDispatched = entry.dispatchedSessionId().isPresent();
        Boolean optimistic = pendingChanges.get(entry.contractId());
        if (optimistic != null) {
            // 如果乐观值与真实值一致，说明服务端已确认，清除映射
            if (optimistic == actuallyDispatched) {
                pendingChanges.remove(entry.contractId());
            }
            return optimistic;
        }
        return actuallyDispatched;
    }

    public void refreshFromServer(ItemStack freshStack) {
        this.bookStack = freshStack;
        this.bookData = ContractBookItem.getData(freshStack);
        rebuildFilteredList();
    }

    private void cancelRename() {
        isRenaming = false;
        renamingEntry = null;
        renameEditBox.setVisible(false);
        renameConfirmBtn.visible = false;
        renameCancelBtn.visible = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public Button getBtnStoreAll() {
        return btnStoreAll;
    }

    public Button getBtnTakeAll() {
        return btnTakeAll;
    }
}