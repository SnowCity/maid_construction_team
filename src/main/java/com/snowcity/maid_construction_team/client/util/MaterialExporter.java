package com.snowcity.maid_construction_team.client.util;

import com.snowcity.maid_construction_team.api.compat.create.CreateMaidCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class MaterialExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaterialExporter.class);

    /**
     * 将材料列表导出到玩家副手书本/剪贴板，若无则自动查找或创建新书。
     */
    public static void export(Map<Block, Integer> materials, String blueprintName) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack offhand = player.getOffhandItem();
        LOGGER.info("[MaterialExporter] Starting export: {} materials, blueprint={}", materials.size(), blueprintName);

        // 1. 副手是书与笔或已签名的书 → 直接替换为新书
        if (offhand.getItem() == Items.WRITABLE_BOOK || offhand.getItem() == Items.WRITTEN_BOOK) {
            ItemStack newBook = createMaterialBook(materials, blueprintName);
            player.setItemInHand(InteractionHand.OFF_HAND, newBook);
            player.displayClientMessage(Component.literal("§a材料清单已写入副手书本"), false);
            return;
        }

        // 2. 副手是 Create 剪贴板（需加载 Create）
        if (CreateMaidCompat.init() && isCreateClipboard(offhand)) {
            boolean success = exportToClipboard(materials, blueprintName, offhand);
            if (success) {
                player.setItemInHand(InteractionHand.OFF_HAND, offhand);
                player.displayClientMessage(Component.literal("§a材料清单已写入剪贴板"), false);
            } else {
                player.displayClientMessage(Component.literal("§c剪贴板写入失败，已生成书本"), false);
                giveNewBookToPlayer(materials, blueprintName, player);
            }
            return;
        }

        // 3. 副手无目标，查找背包中的第一本书并替换，否则生成新书
        boolean found = false;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.getItem() == Items.WRITABLE_BOOK || stack.getItem() == Items.WRITTEN_BOOK) {
                ItemStack newBook = createMaterialBook(materials, blueprintName);
                player.getInventory().setItem(i, newBook);
                player.displayClientMessage(Component.literal("§a材料清单已写入背包中的书本"), false);
                found = true;
                break;
            }
        }
        if (!found) {
            giveNewBookToPlayer(materials, blueprintName, player);
            player.displayClientMessage(Component.literal("§a已生成新的材料清单书"), false);
        }
    }

    /**
     * 创建一本全新的已签名材料清单书。
     */
    private static ItemStack createMaterialBook(Map<Block, Integer> materials, String blueprintName) {
        String title = "材料清单 - " + (blueprintName.isEmpty() ? "未命名" : blueprintName);
        String author = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getName().getString()
                : "建筑队";
        List<Component> pages = formatMaterialPages(materials, title);

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(title),
                author,
                0,
                pages.stream().map(Filterable::passThrough).collect(Collectors.toList()),
                true
        );

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    /**
     * 生成一本新书并放入玩家背包或掉落。
     */
    private static void giveNewBookToPlayer(Map<Block, Integer> materials, String blueprintName, Player player) {
        ItemStack newBook = createMaterialBook(materials, blueprintName);
        if (!player.getInventory().add(newBook)) {
            player.drop(newBook, false);
        }
    }

    /** 判断是否为 Create 剪贴板 */
    private static boolean isCreateClipboard(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
                .equals(ResourceLocation.fromNamespaceAndPath("create", "clipboard"));
    }

    /** 调用 Create 反射方法写入剪贴板 */
    private static boolean exportToClipboard(Map<Block, Integer> materials, String blueprintName, ItemStack clipboardStack) {
        try {
            Class<?> clipboardItem = Class.forName("com.simibubi.create.content.schematics.ClipboardItem");
            var method = clipboardItem.getMethod("setItems", ItemStack.class, List.class);
            List<ItemStack> items = new ArrayList<>();
            for (var entry : materials.entrySet()) {
                items.add(new ItemStack(entry.getKey().asItem(), entry.getValue()));
            }
            method.invoke(null, clipboardStack, items);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to invoke ClipboardItem.setItems", e);
            return false;
        }
    }

    /** 将材料 Map 格式化为书页 Component 列表 */
    private static List<Component> formatMaterialPages(Map<Block, Integer> materials, String title) {
        List<String> lines = new ArrayList<>();
        List<Map.Entry<Block, Integer>> sorted = new ArrayList<>(materials.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getKey().getName().getString()));

        for (var entry : sorted) {
            lines.add("§6" + entry.getKey().getName().getString() + "§r ×" + entry.getValue());
        }

        List<Component> pages = new ArrayList<>();
        int pageSize = 14;
        for (int i = 0; i < lines.size(); i += pageSize) {
            int end = Math.min(i + pageSize, lines.size());
            StringBuilder pageBuilder = new StringBuilder();
            if (i > 0) {
                pageBuilder.append("§l").append(title).append("§r\n");
            }
            for (int j = i; j < end; j++) {
                pageBuilder.append(lines.get(j)).append("\n");
            }
            pages.add(Component.literal(pageBuilder.toString().trim()));
        }
        return pages;
    }
}