package com.snowcity.maid_construction_team.client.screen;

import com.snowcity.maid_construction_team.network.payload.blueprint.ImportBlueprintPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BlueprintSelectionScreen extends Screen {

    private final Player player;

    // 布局常量
    private static final int VISIBLE_ROWS = 10;
    private static final int ENTRY_HEIGHT = 22;
    private static final int LIST_TOP = 65;
    private static final int LIST_BOTTOM_OFFSET = 30;

    // 数据
    private List<Path> schematics = new ArrayList<>();
    private List<Path> filteredSchematics = new ArrayList<>();

    // 搜索
    private EditBox searchBox;
    private String searchText = "";

    // 滚动
    private int scrollOffset = 0;

    // 导入状态
    private boolean isImporting = false;
    private String importMessage = "";
    private boolean importSuccess = false; // 记录是否刚成功导入

    private Button btnCancel;

    public BlueprintSelectionScreen(Player player) {
        super(Component.literal("选择蓝图"));
        this.player = player;
    }

    @Override
    protected void init() {
        super.init();

        searchBox = new EditBox(font, 10, 42, 150, 16, Component.literal("搜索蓝图..."));
        searchBox.setValue(searchText);
        searchBox.setResponder(text -> {
            searchText = text;
            applyFilters();
        });
        addRenderableWidget(searchBox);

        btnCancel = addRenderableWidget(
                Button.builder(Component.literal("取消"), btn -> onClose())
                        .pos(width - 60, height - 30).size(50, 20).build()
        );

        CompletableFuture.supplyAsync(this::scanSchematics)
                .thenAcceptAsync(files -> {
                    schematics = files;
                    applyFilters();
                }, minecraft);
    }

    private List<Path> scanSchematics() {
        Path schemsDir = Minecraft.getInstance().gameDirectory.toPath().resolve("schematics");
        if (Files.notExists(schemsDir)) return List.of();
        try (var stream = Files.list(schemsDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".nbt"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    private void applyFilters() {
        String lower = searchText.toLowerCase();
        filteredSchematics = schematics.stream()
                .filter(p -> p.getFileName().toString().toLowerCase().contains(lower))
                .collect(Collectors.toList());

        importSuccess = false; // 搜索或过滤时重置成功标记

        int maxScrollRows = Math.max(0, filteredSchematics.size() - VISIBLE_ROWS);
        int currentRow = (int) Math.round((double) -scrollOffset / ENTRY_HEIGHT);
        currentRow = Math.clamp(currentRow, 0, maxScrollRows);
        scrollOffset = -currentRow * ENTRY_HEIGHT;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, "选择蓝图文件", width / 2, 10, 0xFFFFFF);
        graphics.drawString(font, "搜索:", 10, 32, 0xAAAAAA);

        int listBottom = height - LIST_BOTTOM_OFFSET;
        if (listBottom - LIST_TOP < VISIBLE_ROWS * ENTRY_HEIGHT) {
            listBottom = LIST_TOP + VISIBLE_ROWS * ENTRY_HEIGHT;
        }
        graphics.enableScissor(10, LIST_TOP, width - 10, listBottom);

        int startRow = (int) Math.round((double) -scrollOffset / ENTRY_HEIGHT);
        int endRow = Math.min(filteredSchematics.size(), startRow + VISIBLE_ROWS);

        for (int i = startRow; i < endRow; i++) {
            Path path = filteredSchematics.get(i);
            int y = LIST_TOP + (i - startRow) * ENTRY_HEIGHT;

            boolean hovered = mouseY >= y && mouseY < y + ENTRY_HEIGHT && mouseX >= 10 && mouseX < width - 10;
            graphics.fill(10, y, width - 10, y + ENTRY_HEIGHT, hovered ? 0x44FFFFFF : 0x22FFFFFF);

            String fileName = path.getFileName().toString();
            graphics.drawString(font, fileName, 14, y + 4, 0xFFFFFF);

            try {
                long size = Files.size(path);
                String sizeStr = formatFileSize(size);
                graphics.drawString(font, sizeStr, width - 100, y + 4, 0x888888);
            } catch (IOException ignored) {}
        }

        graphics.disableScissor();

        // 持久显示导入状态，直到下一次文件选择或搜素
        if (isImporting || importSuccess) {
            graphics.drawString(font, importMessage, 10, height - 20, 0xFFAA00);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchBox.isFocused() && !isMouseOverSearchBox(mouseX, mouseY)) {
            searchBox.setFocused(false);
        }

        if (button == 0 && !isImporting) { // 防止重复导入
            int startRow = (int) Math.round((double) -scrollOffset / ENTRY_HEIGHT);
            for (int i = startRow; i < Math.min(filteredSchematics.size(), startRow + VISIBLE_ROWS); i++) {
                int y = LIST_TOP + (i - startRow) * ENTRY_HEIGHT;
                if (mouseY >= y && mouseY < y + ENTRY_HEIGHT && mouseX >= 10 && mouseX < width - 10) {
                    onBlueprintSelected(filteredSchematics.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScrollRows = Math.max(0, filteredSchematics.size() - VISIBLE_ROWS);
        int currentRow = (int) Math.round((double) -scrollOffset / ENTRY_HEIGHT);
        currentRow -= (int) scrollY;
        currentRow = Math.clamp(currentRow, 0, maxScrollRows);
        scrollOffset = -currentRow * ENTRY_HEIGHT;
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

    private boolean isMouseOverSearchBox(double mouseX, double mouseY) {
        return mouseX >= searchBox.getX() && mouseX <= searchBox.getX() + searchBox.getWidth()
                && mouseY >= searchBox.getY() && mouseY <= searchBox.getY() + searchBox.getHeight();
    }

    /**
     * 选中一个蓝图文件，异步读取并发送到服务端。
     * 导入成功后提示常驻，直到用户选择下一个文件或搜索。
     */
    private void onBlueprintSelected(Path path) {
        if (isImporting) return;

        isImporting = true;
        importSuccess = false; // 重置上一轮成功状态
        importMessage = "正在导入 " + path.getFileName().toString() + "...";

        CompletableFuture.supplyAsync(() -> {
            try {
                CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                if (!root.contains("size", 9) || !root.contains("palette", 9) || !root.contains("blocks", 9)) {
                    throw new IllegalStateException("无效蓝图文件：缺少必要字段");
                }
                return root;
            } catch (Exception e) {
                throw new RuntimeException("读取失败: " + e.getMessage(), e);
            }
        }).thenAcceptAsync(rootTag -> {
            int slot = player.getInventory().selected;
            String fileName = path.getFileName().toString();
            PacketDistributor.sendToServer(new ImportBlueprintPayload(slot, rootTag, fileName));
            importMessage = "✅ 已导入: " + fileName;
            importSuccess = true;  // 标记成功，使提示持久
            isImporting = false;
        }, minecraft).exceptionallyAsync(throwable -> {
            importMessage = "❌ " + throwable.getCause().getMessage();
            importSuccess = true;  // 错误信息也持久
            isImporting = false;
            return null;
        }, minecraft);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}