package com.snowcity.maid_construction_team.client.screen;

import com.snowcity.maid_construction_team.component.MaterialChecklistData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.network.ModifyChecklistPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class MaterialChecklistScreen extends Screen {

    private ItemStack stack;
    private final InteractionHand hand;
    private MaterialChecklistData data;
    private ListTag containers;

    // 搜索
    private EditBox searchBox;
    private String searchText = "";

    // 列表参数
    private static final int ENTRY_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 10;
    private static final int LIST_TOP = 60;

    // 过滤后的可见条目索引
    private final List<Integer> filteredIndices = new ArrayList<>();
    private int scrollOffset = 0;

    // 动态按钮映射：索引 -> [重命名, 取消登记]
    private final Map<Integer, List<Button>> entryButtons = new HashMap<>();

    // 重命名覆盖层
    private boolean isRenaming = false;
    private int renamingIndex = -1;
    private EditBox renameEditBox;
    private Button renameConfirmBtn;
    private Button renameCancelBtn;

    public MaterialChecklistScreen(ItemStack stack, InteractionHand hand) {
        super(Component.translatable("mct.screen.material_checklist.title"));
        this.stack = stack;
        this.hand = hand;
        refreshData();
    }

    public void refreshFromServer(ItemStack freshStack) {
        this.stack = freshStack;          // 替换引用
        refreshData();                    // 重新读取 NBT
        rebuildFilteredList();            // 刷新 UI
    }

    private void refreshData() {
        this.data = stack.getOrDefault(ModDataComponents.MATERIAL_CHECKLIST.get(),
                new MaterialChecklistData(new CompoundTag()));
        this.containers = data.getContainerList();
    }

    @Override
    protected void init() {
        super.init();

        // 搜索框
        searchBox = new EditBox(font, 10, 42, 150, 16, Component.translatable("mct.screen.search"));
        searchBox.setValue(searchText);
        searchBox.setResponder(text -> {
            searchText = text;
            rebuildFilteredList();
        });
        addRenderableWidget(searchBox);

        // 重命名控件
        renameEditBox = new EditBox(font, width / 2 - 80, height / 2 - 10, 160, 20, Component.translatable("mct.screen.new_name"));
        renameEditBox.setVisible(false);
        addRenderableWidget(renameEditBox);

        renameConfirmBtn = addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.confirm"), btn -> confirmRename())
                        .pos(width / 2 - 85, height / 2 + 15).size(60, 20).build());
        renameConfirmBtn.visible = false;

        renameCancelBtn = addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.cancel"), btn -> cancelRename())
                        .pos(width / 2 - 20, height / 2 + 15).size(60, 20).build());
        renameCancelBtn.visible = false;

        rebuildFilteredList();
    }

    private void rebuildFilteredList() {
        // 移除旧按钮
        for (List<Button> btns : entryButtons.values()) {
            for (Button btn : btns) removeWidget(btn);
        }
        entryButtons.clear();
        filteredIndices.clear();

        String lowerSearch = searchText.toLowerCase();
        for (int i = 0; i < containers.size(); i++) {
            final int index = i; // 捕获有效 final 变量
            CompoundTag entry = containers.getCompound(i);
            String name = getDisplayName(entry);
            BlockPos pos = getPos(entry);
            String posStr = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            if (lowerSearch.isEmpty() || name.toLowerCase().contains(lowerSearch) || posStr.contains(lowerSearch)) {
                filteredIndices.add(i);

                // 创建操作按钮
                List<Button> btns = new ArrayList<>();
                Button renameBtn = Button.builder(Component.translatable("mct.screen.rename"), btn -> {
                    isRenaming = true;
                    renamingIndex = index;
                    renameEditBox.setValue(getDisplayName(containers.getCompound(index)));
                    renameEditBox.setVisible(true);
                    renameEditBox.setFocused(true);
                    renameConfirmBtn.visible = true;
                    renameCancelBtn.visible = true;
                }).pos(0, 0).size(40, 16).build();
                renameBtn.visible = false;
                btns.add(renameBtn);
                addRenderableWidget(renameBtn);

                Button removeBtn = Button.builder(Component.translatable("mct.screen.cancel"), btn -> {
                    PacketDistributor.sendToServer(new ModifyChecklistPayload(
                            (byte)0, pos, Optional.empty(), hand
                    ));
                    // 不修改本地物品，等待服务端同步
                }).pos(0, 0).size(40, 16).build();
                removeBtn.visible = false;
                btns.add(removeBtn);
                addRenderableWidget(removeBtn);

                entryButtons.put(i, btns);
            }
        }

        // 对齐滚动
        int maxRows = Math.max(0, filteredIndices.size() - VISIBLE_ROWS);
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxRows) scrollOffset = maxRows;

        updateVisibleButtons();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font, "物资登记表 (" + containers.size() + "个容器)", 10, 10, 0xFFFFFF);
        graphics.drawString(font, "登记容器以便建造时从中获取材料", 10, 22, 0xAAAAAA);
        graphics.drawString(font, "搜索:", 10, 32, 0xAAAAAA);

        int listBottom = LIST_TOP + VISIBLE_ROWS * ENTRY_HEIGHT;
        graphics.enableScissor(10, LIST_TOP, width - 10, listBottom);

        int start = scrollOffset;
        int end = Math.min(filteredIndices.size(), start + VISIBLE_ROWS);

        for (int i = start; i < end; i++) {
            int idx = filteredIndices.get(i);
            CompoundTag entry = containers.getCompound(idx);
            String name = getDisplayName(entry);
            BlockPos pos = getPos(entry);
            int y = LIST_TOP + (i - start) * ENTRY_HEIGHT;

            // 悬停背景
            boolean hovered = mouseY >= y && mouseY < y + ENTRY_HEIGHT && mouseX >= 10 && mouseX < width - 10;
            graphics.fill(10, y, width - 10, y + ENTRY_HEIGHT, hovered ? 0x44FFFFFF : 0x22FFFFFF);

            graphics.drawString(font, name, 14, y + 4, 0xFFFFFF);
            String posText = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            graphics.drawString(font, posText, 14, y + 14, 0x888888);
        }

        graphics.disableScissor();

        // 重命名覆盖层
        if (isRenaming) {
            graphics.fill(0, 0, width, height, 0x88000000);
            graphics.drawString(font, "重命名容器", width / 2 - 30, height / 2 - 25, 0xFFFFFF);
        }
    }

    /** 更新所有按钮的可见性和位置 */
    private void updateVisibleButtons() {
        int start = scrollOffset;
        int end = Math.min(filteredIndices.size(), start + VISIBLE_ROWS);
        for (int i = start; i < end; i++) {
            int idx = filteredIndices.get(i);
            List<Button> btns = entryButtons.get(idx);
            if (btns != null) {
                int y = LIST_TOP + (i - start) * ENTRY_HEIGHT;
                for (Button btn : btns) {
                    btn.visible = true;
                    if (btn.getMessage().getString().equals("重命名")) {
                        btn.setPosition(width - 120, y + 2);
                    } else { // 取消登记
                        btn.setPosition(width - 60, y + 2);
                    }
                }
            }
        }
        // 隐藏不可见行的按钮
        for (int idx : entryButtons.keySet()) {
            boolean visible = false;
            for (int i = start; i < end; i++) {
                if (filteredIndices.get(i) == idx) {
                    visible = true;
                    break;
                }
            }
            if (!visible) {
                for (Button btn : entryButtons.get(idx)) {
                    btn.visible = false;
                }
            }
        }
    }

    private String getDisplayName(CompoundTag entry) {
        String custom = MaterialChecklistData.getDisplayName(entry);
        if (custom != null) return custom;
        BlockPos pos = getPos(entry);
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private BlockPos getPos(CompoundTag entry) {
        return new BlockPos(entry.getInt(MaterialChecklistData.TAG_X),
                entry.getInt(MaterialChecklistData.TAG_Y),
                entry.getInt(MaterialChecklistData.TAG_Z));
    }

    private void confirmRename() {
        if (renamingIndex >= 0) {
            String newName = renameEditBox.getValue().trim();
            if (!newName.isEmpty()) {
                BlockPos pos = getPos(containers.getCompound(renamingIndex));
                PacketDistributor.sendToServer(new ModifyChecklistPayload(
                        (byte)1, pos, Optional.of(newName), hand
                ));
            }
        }
        cancelRename();
    }

    private void cancelRename() {
        isRenaming = false;
        renamingIndex = -1;
        renameEditBox.setVisible(false);
        renameConfirmBtn.visible = false;
        renameCancelBtn.visible = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxRows = Math.max(0, filteredIndices.size() - VISIBLE_ROWS);
        scrollOffset = Math.clamp(scrollOffset + (int) scrollY, 0, maxRows);
        updateVisibleButtons();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isRenaming) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { cancelRename(); return true; }
            return renameEditBox.keyPressed(keyCode, scanCode, modifiers);
        }
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
        if (searchBox.isFocused()) return searchBox.charTyped(codePoint, modifiers);
        if (isRenaming && renameEditBox.isFocused()) return renameEditBox.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}