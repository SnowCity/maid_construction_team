package com.snowcity.maid_construction_team.old.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.snowcity.maid_construction_team.item.custom.SurveyingToolItem;
import com.snowcity.maid_construction_team.old.util.AreaSchematicCapturer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 蓝图保存GUI界面
 */
@OnlyIn(Dist.CLIENT)
public class SurveyingToolScreen extends Screen {

    private final ItemStack stack;
    private final InteractionHand hand;

    private EditBox nameInput;
    private Button materialStatsButton;
    private Button confirmButton;
    private Button cancelButton;

    private boolean dropdownOpen = false;
    private float scrollOffset = 0.0f;
    private static final int DROPDOWN_WIDTH = 180;
    private static final int DROPDOWN_HEIGHT = 140;
    private static final int ENTRY_HEIGHT = 20;

    /** 翻译后的材料列表 */
    private List<MaterialEntry> materialEntries = new ArrayList<>();

    /** 材料条目内部类 */
    private static class MaterialEntry {
        final String translatedName;
        final int count;

        MaterialEntry(String translatedName, int count) {
            this.translatedName = translatedName;
            this.count = count;
        }
    }

    public SurveyingToolScreen(ItemStack stack, InteractionHand hand) {
        super(Component.literal("蓝图保存"));
        this.stack = stack;
        this.hand = hand;
        // 在客户端初始化时翻译材料
        loadAndTranslateMaterials();
    }

    /**
     * 加载并翻译材料列表
     */
    private void loadAndTranslateMaterials() {
        Map<String, Integer> rawData = AreaSchematicCapturer.getRawMaterialCache();
        materialEntries = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : rawData.entrySet()) {
            String blockId = entry.getKey();
            int count = entry.getValue();

            try {
                // 1. 修正：使用 parse() 方法创建 ResourceLocation
                ResourceLocation resLoc = ResourceLocation.parse(blockId);

                // 2. 从 ID 获取 Block
                var block = BuiltInRegistries.BLOCK.get(resLoc);

                // 3. 获取翻译后的名称
                String translatedName = Component.translatable(block.getDescriptionId()).getString();

                materialEntries.add(new MaterialEntry(translatedName, count));
            } catch (Exception e) {
                // 如果翻译失败，回退到原始 ID
                System.err.println("[GUI] 无法翻译方块: " + blockId);
                materialEntries.add(new MaterialEntry(blockId, count));
            }
        }

        // 按数量降序排序
        materialEntries.sort(Comparator.comparingInt((MaterialEntry e) -> e.count).reversed());

        System.out.println("[GUI] 翻译完成，共 " + materialEntries.size() + " 种材料");
    }

    @Override
    protected void init() {
        int centerX = this.width / 2 - 128;
        int centerY = this.height / 2 - 83;

        // 蓝图名称输入框
        this.nameInput = new EditBox(this.font, centerX + 80, centerY + 20, 100, 20, Component.literal("蓝图名称"));
        this.nameInput.setMaxLength(32);
        this.nameInput.setValue("blueprint_" + System.currentTimeMillis());
        this.addRenderableWidget(this.nameInput);
        this.setInitialFocus(this.nameInput);

        // 材料统计按钮
        String btnText = "材料统计 (" + materialEntries.size() + "种)";
        this.materialStatsButton = Button.builder(Component.literal(btnText), (btn) -> {
            this.dropdownOpen = !this.dropdownOpen;
            this.scrollOffset = 0;
        }).bounds(centerX + 30, centerY + 35, 140, 20).build();
        this.addRenderableWidget(this.materialStatsButton);

        // 确认保存按钮
        this.confirmButton = Button.builder(Component.literal("确认保存"), (btn) -> {
            String name = this.nameInput.getValue();
            this.minecraft.player.closeContainer();

            if (this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("maid_save " + name);
            }
        }).bounds(centerX + 190, centerY + 35, 80, 20).build();
        this.addRenderableWidget(this.confirmButton);

        // 取消按钮
        this.cancelButton = Button.builder(Component.literal("取消"), (btn) -> {
            this.minecraft.player.closeContainer();
        }).bounds(centerX + 190, centerY + 65, 80, 20).build();
        this.addRenderableWidget(this.cancelButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2 - 128;
        int centerY = this.height / 2 - 83;

        // 绘制标题和名称标签
        guiGraphics.drawString(this.font, this.title, centerX + (256 - this.font.width(this.title)) / 2, centerY + 5, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, "名称:", centerX + 30, centerY + 25, 0xAAAAAA, false);

        // 绘制坐标范围预览
        var pos1 = SurveyingToolItem.getStoredPos(this.stack, "FirstPos");
        var pos2 = SurveyingToolItem.getStoredPos(this.stack, "SecondPos");
        if (pos1 != null && pos2 != null) {
            String posStr = String.format("范围: (%d, %d, %d) -> (%d, %d, %d)",
                    pos1.getX(), pos1.getY(), pos1.getZ(),
                    pos2.getX(), pos2.getY(), pos2.getZ());
            guiGraphics.drawString(this.font, posStr, centerX + 30, centerY + 120, 0x55FF55, false);
        }

        // 渲染材料统计下拉列表
        if (this.dropdownOpen) {
            renderDropdown(guiGraphics, centerX + 30, centerY + 60, mouseX, mouseY);
        }
    }

    private void renderDropdown(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        // 绘制下拉列表背景和边框
        guiGraphics.fill(x, y, x + DROPDOWN_WIDTH, y + DROPDOWN_HEIGHT, 0xFF333333);
        guiGraphics.renderOutline(x, y, DROPDOWN_WIDTH, DROPDOWN_HEIGHT, 0xFF555555);

        // 空数据提示
        if (materialEntries.isEmpty()) {
            guiGraphics.drawString(this.font, "先选择两个点", x + 5, y + 5, 0xAAAAAA, false);
            return;
        }

        // 启用裁剪
        RenderSystem.enableScissor(x * 2, (this.height - (y + DROPDOWN_HEIGHT)) * 2, DROPDOWN_WIDTH * 2, DROPDOWN_HEIGHT * 2);

        // 计算可见范围
        int visibleStart = (int) (scrollOffset / ENTRY_HEIGHT);
        int visibleEnd = Math.min(visibleStart + (DROPDOWN_HEIGHT / ENTRY_HEIGHT) + 1, materialEntries.size());

        // 绘制条目
        for (int i = visibleStart; i < visibleEnd; i++) {
            MaterialEntry entry = materialEntries.get(i);
            int entryY = y + (i * ENTRY_HEIGHT) - (int) scrollOffset;

            // 鼠标悬停高亮
            if (mouseX >= x && mouseX < x + DROPDOWN_WIDTH && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT) {
                guiGraphics.fill(x, entryY, x + DROPDOWN_WIDTH, entryY + ENTRY_HEIGHT, 0xFF555555);
            }

            // 绘制翻译后的名称和数量
            String displayText = entry.translatedName + " x" + entry.count;
            guiGraphics.drawString(this.font, displayText, x + 5, entryY + 5, 0xFFFFFF, false);
        }

        RenderSystem.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.dropdownOpen) {
            int maxScroll = Math.max(0, materialEntries.size() * ENTRY_HEIGHT - DROPDOWN_HEIGHT);
            this.scrollOffset -= (float) (scrollY * 15);
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2 - 128;
        int centerY = this.height / 2 - 83;

        if (this.dropdownOpen) {
            int x = centerX + 30;
            int y = centerY + 60;
            if (!(mouseX >= x && mouseX < x + DROPDOWN_WIDTH && mouseY >= y && mouseY < y + DROPDOWN_HEIGHT)) {
                this.dropdownOpen = false;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}