package com.snowcity.maid_construction_team.api.compat.create;

import com.simibubi.create.content.schematics.cannon.MaterialChecklist;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 利用 Create 的内部工具生成机械动力蓝图大炮可用的材料清单剪贴板。
 * 仅在 Create 模组存在时使用。
 */
public class CreateClipboardBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateClipboardBuilder.class);

    /**
     * 根据方块需求表生成一个剪贴板物品栈。
     *
     * @param materials 方块 → 所需数量
     * @param title     剪贴板标题（如 "材料清单 - 建筑名"）
     * @param author    作者（可为空）
     * @return 已写入材料的剪贴板物品栈
     */
    public static ItemStack build(Map<Block, Integer> materials, String title, String author) {
        // 1. 创建 MaterialChecklist 并填充需求
        MaterialChecklist checklist = new MaterialChecklist();
        for (var entry : materials.entrySet()) {
            Item item = entry.getKey().asItem();
            int count = entry.getValue();
            // 使用 merge 避免弃用 put，同时自动合并相同物品的数量
            checklist.required.merge(item, count, Integer::sum);
        }

        // 2. 生成剪贴板物品（内部会读取 required 和 gathered 等字段）
        ItemStack clipboard = checklist.createWrittenClipboard();

        // 3. 自定义名称（覆盖 Create 默认的 “Material Checklist”）
        if (title != null && !title.isEmpty()) {
            clipboard.set(DataComponents.CUSTOM_NAME, Component.literal(title));
        }

        return clipboard;
    }
}