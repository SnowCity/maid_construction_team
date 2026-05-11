package com.snowcity.maid_construction_team.old.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable; // 【正确导入】终于找到了！
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版《已写的书》生成工具类（最终完美适配版）
 * <p>
 * 1. 正确导入 Filterable：net.minecraft.server.network.Filterable
 * 2. 严格匹配 WrittenBookContent record 定义
 * 3. 提供双模式支持
 */
public final class MaidBookUtil {
    private MaidBookUtil() {}

    // ==========================================
    // 模式1：通用模式（完全自定义内容）
    // ==========================================

    /**
     * 生成完全自定义的已写的书
     *
     * @param title  书的标题
     * @param author 书的作者
     * @param pages  书页内容列表（每一项对应一页，纯文本）
     * @param count  书的数量
     * @return 符合原版规范、可正常打开的已写的书
     */
    public static ItemStack createCustom(String title, String author, List<String> pages, int count) {
        List<Component> componentPages = new ArrayList<>();
        for (String page : pages) {
            componentPages.add(Component.literal(page));
        }
        return createCustomWithComponents(title, author, componentPages, count);
    }

    // 【新增方法】直接接收带样式的 Component 列表
    public static ItemStack createCustomWithComponents(String title, String author, List<Component> pages, int count) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK, count);

        // 转换书页类型
        List<Filterable<Component>> filterablePages = new ArrayList<>();
        for (Component page : pages) {
            filterablePages.add(Filterable.passThrough(page));
        }

        // 构建 WrittenBookContent
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(title),
                author,
                0,
                filterablePages,
                true
        );

        // 设置 Data Components
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        book.set(DataComponents.CUSTOM_NAME, Component.literal(title));

        return book;
    }
}