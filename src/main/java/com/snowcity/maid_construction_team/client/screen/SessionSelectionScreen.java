package com.snowcity.maid_construction_team.client.screen;

import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;

/**
 * 派遣时使用的会话选择界面。
 * <p>
 * 列出玩家当前所有活跃的蓝图放置会话，
 * 选择后回调指定的 Consumer，并关闭界面。
 */
public class SessionSelectionScreen extends Screen {

    private final Consumer<PlacementSession> onSelect;
    private final List<PlacementSession> activeSessions = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int ENTRY_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 10;
    private static final int LIST_TOP = 30;

    private final Screen parent;

    public SessionSelectionScreen(Consumer<PlacementSession> onSelect) {
        this(onSelect, null);
    }

    // 新构造器，接收父屏幕
    public SessionSelectionScreen(Consumer<PlacementSession> onSelect, Screen parent) {
        super(Component.translatable("mct.screen.session_selection.title"));
        this.onSelect = onSelect;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            activeSessions.addAll(PlayerSessionManager.of(player).getActiveSessions());
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font, "选择会话", 10, 10, 0xFFFFFF);

        int listBottom = LIST_TOP + VISIBLE_ROWS * ENTRY_HEIGHT;
        graphics.enableScissor(10, LIST_TOP, width - 10, listBottom);

        int start = Math.max(0, -scrollOffset / ENTRY_HEIGHT);
        int end = Math.min(activeSessions.size(), start + VISIBLE_ROWS);

        for (int i = start; i < end; i++) {
            PlacementSession session = activeSessions.get(i);
            int y = LIST_TOP + (i - start) * ENTRY_HEIGHT;

            boolean hovered = mouseY >= y && mouseY < y + ENTRY_HEIGHT && mouseX >= 10 && mouseX < width - 10;
            graphics.fill(10, y, width - 10, y + ENTRY_HEIGHT, hovered ? 0x44FFFFFF : 0x22FFFFFF);

            String text = session.getBlueprintName() + " [" + session.getSessionId().toString().substring(0, 8) + "] - " +
                    session.getParticipantUuids().size() + "人";
            graphics.drawString(font, text, 14, y + 4, 0xFFFFFF);
        }

        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int start = Math.max(0, -scrollOffset / ENTRY_HEIGHT);
            for (int i = start; i < Math.min(activeSessions.size(), start + VISIBLE_ROWS); i++) {
                int y = LIST_TOP + (i - start) * ENTRY_HEIGHT;
                if (mouseY >= y && mouseY < y + ENTRY_HEIGHT && mouseX >= 10 && mouseX < width - 10) {
                    onSelect.accept(activeSessions.get(i));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, activeSessions.size() - VISIBLE_ROWS) * ENTRY_HEIGHT;
        scrollOffset = Math.clamp(scrollOffset + (int) scrollY * ENTRY_HEIGHT, -maxScroll, 0);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        // 返回到父屏幕（通常是契约之书）
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
        } else {
            super.onClose();
        }
    }
}