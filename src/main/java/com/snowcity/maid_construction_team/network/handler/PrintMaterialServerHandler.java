package com.snowcity.maid_construction_team.network.handler;

import com.snowcity.maid_construction_team.api.compat.create.CreateClipboardBuilder;
import com.snowcity.maid_construction_team.api.compat.create.CreateMaidCompat;
import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import com.snowcity.maid_construction_team.core.schematic.SchematicData;
import com.snowcity.maid_construction_team.network.payload.PrintMaterialPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class PrintMaterialServerHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrintMaterialServerHandler.class);

    public static final ResourceLocation CLIPBOARD_ID = ResourceLocation.fromNamespaceAndPath("create", "clipboard");

    public static void handle(final PrintMaterialPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            PlacementSessionManager mgr = PlayerSessionManager.of(player);
            PlacementSession session = mgr.getSession(payload.sessionId());
            if (session == null) return;

            // 统计蓝图所需方块
            SchematicData schematic = session.getPlacer().getSchematicData();
            Map<Block, Integer> materials = new HashMap<>();
            for (var info : schematic.getBlocks()) {
                materials.merge(info.state().getBlock(), 1, Integer::sum);
            }

            String blueprintName = session.getBlueprintName();
            String title = "材料清单 - " + (blueprintName.isEmpty() ? "未命名" : blueprintName);
            String author = player.getName().getString();

            // 处理副手物品
            ItemStack offhand = player.getOffhandItem();
            boolean handled = false;

            // 1. 处理剪贴板（需要 Create）
            if (CreateMaidCompat.init()) {
                Item clipboardItem = BuiltInRegistries.ITEM.get(CLIPBOARD_ID);
                if (clipboardItem != Items.AIR && offhand.getItem() == clipboardItem) {
                    // 调用 Create 专用工具生成剪贴板物品
                    ItemStack clipboard = CreateClipboardBuilder.build(materials, title, author);
                    // 替换副手的剪贴板
                    player.setItemInHand(InteractionHand.OFF_HAND, clipboard);
                    player.sendSystemMessage(Component.literal("§a材料清单已写入剪贴板，机械动力可识别"));
                    handled = true;
                }
            }

            if (!handled) {
                // 2. 处理书本：消耗副手书，生成新材料书到背包
                if (offhand.getItem() == Items.WRITABLE_BOOK || offhand.getItem() == Items.WRITTEN_BOOK) {
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                }
                ItemStack book = createMaterialBook(materials, title, author);
                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
                String msg = offhand.getItem() == Items.WRITABLE_BOOK || offhand.getItem() == Items.WRITTEN_BOOK
                        ? "§a书已消耗，新材料清单书已放入背包"
                        : "§a材料清单书已放入背包";
                player.sendSystemMessage(Component.literal(msg));
            }

            LOGGER.info("{} printed material book/clipboard for session {}", player.getName().getString(), payload.sessionId());
        });
    }

    private static ItemStack createMaterialBook(Map<Block, Integer> materials, String title, String author) {
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