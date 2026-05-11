package com.snowcity.maid_construction_team.old.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class SessionDetailGUI extends Screen {
    private final UUID sessionId;
    private final Screen parentScreen;

    // 这些字段和女仆列表保持一致
    private int leftPos;
    private int topPos;
    private static final int WIDTH = 220; // 和女仆列表一样宽
    private static final int HEIGHT = 200; // 和女仆列表一样高

    public SessionDetailGUI(UUID sessionId, Screen parentScreen) {
        super(Component.literal("会话详情"));
        this.sessionId = sessionId;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        // 初始化顺序和女仆列表完全一致
        this.leftPos = (this.width - WIDTH) / 2;
        this.topPos = (this.height - HEIGHT) / 2;
        this.clearWidgets(); // 和女仆列表一样，先清空

        // 1. 左上角返回按钮
        Button backBtn = Button.builder(Component.literal("← 返回"), (btn) -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
        }).bounds(leftPos + 5, topPos + 5, 60, 20).build();
        this.addRenderableWidget(backBtn);

        // 2. 右上角女仆列表按钮
        Button maidListBtn = Button.builder(Component.literal("女仆列表 →"), (btn) -> {
            // 打开MaidListGUI
            if (this.minecraft != null) {
                this.minecraft.setScreen(new MaidListGUI(this.sessionId, this));
            }
        }).bounds(leftPos + WIDTH - 75, topPos + 5, 70, 20).build();
        this.addRenderableWidget(maidListBtn);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 空实现，和女仆列表一样
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染逻辑也尽量简单
        guiGraphics.fill(leftPos, topPos, leftPos + WIDTH, topPos + HEIGHT, 0xFF2C2C2C);
        guiGraphics.renderOutline(leftPos, topPos, WIDTH, HEIGHT, 0xFFFFFFFF);

        guiGraphics.drawString(this.font, "会话详情", leftPos + 10, topPos + 35, 0xFFFFFFFF, false);
        guiGraphics.drawString(this.font, "ID: " + this.sessionId.toString().substring(0, 10), leftPos + 10, topPos + 55, 0xFFAAAAAA, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}