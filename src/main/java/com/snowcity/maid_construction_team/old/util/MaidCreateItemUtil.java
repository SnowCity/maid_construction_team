package com.snowcity.maid_construction_team.old.util;


import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.cannon.MaterialChecklist;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 女仆物品工具类 - 处理女仆与机械动力物品的交互
 * <P>
 * 这个类提供了检查和处理女仆物品栏中机械动力相关物品的功能
 */
public class MaidCreateItemUtil {

    /**
     * 私有构造函数 - 防止创建实例
     */
    private MaidCreateItemUtil() {
    }

    // 机械动力蓝图物品的ID
    private static final ResourceLocation SCHEMATIC_ID =
            ResourceLocation.fromNamespaceAndPath("create", "schematic");
    // 机械动力空蓝图物品的ID
    private static final ResourceLocation EMPTY_SCHEMATIC_ID =
            ResourceLocation.fromNamespaceAndPath("create", "empty_schematic");
    // 机械动力剪贴板物品的ID
    private static final ResourceLocation CLIPBOARD_ID =
            ResourceLocation.fromNamespaceAndPath("create", "clipboard");


    /**
     * 检查物品是否是机械动力的剪贴板
     *
     * @param stack 要检查的物品
     * @return 如果是剪贴板则返回true
     */
    public static boolean isCreateClipboard(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // 获取物品的注册ID
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        // 检查ID是否匹配剪贴板ID
        return CLIPBOARD_ID.equals(key);
    }

    /**
     * 检查物品是否是目标物品（书或剪贴板）
     *
     * @param stack 要检查的物品
     * @return 如果是书或剪贴板则返回true
     */
    public static boolean isTargetItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // 可写的书或已写的书都可以
        if (stack.is(Items.BOOK) || stack.is(Items.WRITTEN_BOOK)) {
            return true;
        }
        // 机械动力的剪贴板也可以
        return MaidCreateItemUtil.isCreateClipboard(stack);
    }

    /**
     * 获取女仆携带的蓝图
     *<P>
     * 按顺序检查：主手 -> 副手 -> 物品栏
     * @param maid 女仆实体
     * @return 蓝图物品，如果没有则返回空物品
     */
    public static ItemStack getMaidSchematicStack(EntityMaid maid) {
        // 先检查主手
        ItemStack main = maid.getMainHandItem();
        if (isCreateSchematic(main)) {
            return main;
        }
        // 再检查副手
        ItemStack off = maid.getOffhandItem();
        if (isCreateSchematic(off)) {
            return off;
        }

        // 最后检查物品栏
        ItemStackHandler inv = maid.getMaidInv();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (isCreateSchematic(stack)) {
                return stack;
            }
        }
        // 没找到蓝图，返回空物品栈
        return ItemStack.EMPTY;
    }

    /**
     * 检查物品是否是机械动力的蓝图
     *
     * @param stack 要检查的物品
     * @return 如果是蓝图则返回true
     */
    public static boolean isCreateSchematic(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // 获取物品的注册ID
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        // 检查ID是否匹配蓝图ID
        return SCHEMATIC_ID.equals(key);
    }


    /**
     * 统计女仆携带的蓝图数量
     *
     * @param maid 女仆实体
     * @return 蓝图的数量
     */
    public static int countSchematics(EntityMaid maid) {
        int count = 0;

        // 检查主手
        ItemStack main = maid.getMainHandItem();
        if (isCreateSchematic(main)) {
            count++;
        }
        // 检查副手（确保不重复计算）
        ItemStack off = maid.getOffhandItem();
        if (isCreateSchematic(off) && off != main) {
            count++;
        }

        // 检查物品栏
        ItemStackHandler inv = maid.getMaidInv();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            // 确保不重复计算主手和副手的物品
            if (isCreateSchematic(stack) && stack != main && stack != off) {
                count++;
            }
        }

        return count;
    }


    /**
     * 检查物品是否是机械动力的空白蓝图
     *
     * @param stack 要检查的物品
     * @return 如果是蓝图则返回true
     */
    public static boolean isCreateEmptySchematic(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // 获取物品的注册ID
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        // 检查ID是否匹配蓝图ID
        return EMPTY_SCHEMATIC_ID.equals(key);
    }


    /**
     * 收集蓝图所需的材料清单
     * <P>
     * 这个方法会读取蓝图数据，分析需要哪些材料来建造
     *
     * @param level 游戏世界
     * @param MaidSchematic 蓝图物品
     * @return 材料清单，如果蓝图无效则返回null
     */
    public static MaterialChecklist collectRequirements(Level level, ItemStack MaidSchematic, Player player) {

        // 判断蓝图是否部署
        if (!SchematicCheckUtil.isCreateSchematicDeployed(level, MaidSchematic, player)) return null;

        // 测试
        player.sendSystemMessage(Component.literal("测试: 蓝图已部署"));

        // 创建蓝图打印机来读取蓝图数据
        SchematicPrinter printer = new SchematicPrinter();
        printer.loadSchematic(MaidSchematic, level, true);
        // 校验加载状态
        if (!printer.isLoaded() || printer.isErrored()) return null;

        player.sendSystemMessage(Component.literal("测试: 蓝图已加载"));

        // 创建材料清单
        MaterialChecklist checklist = new MaterialChecklist();

        // 创建放置条件，这里允许放置所有方块
        SchematicPrinter.PlacementPredicate alwaysPlace =
                (pos, state, be, toReplace, toReplaceOther, isNormalCube) -> true;

        // 标记所有方块所需的材料
        printer.markAllBlockRequirements(checklist, level, alwaysPlace);
        // 标记所有实体所需的材料
        printer.markAllEntityRequirements(checklist);

        return checklist;
    }


    /**
     * 把蓝图替换为空蓝图（适配 ItemStackHandler）
     */
    public static void replaceSchematicWithEmpty(EntityMaid maid, ItemStack blueprint) {
        ItemStackHandler inv = maid.getMaidInv();
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (ItemStack.isSameItem(stack, blueprint)) {
                inv.setStackInSlot(slot, BuiltInRegistries.ITEM.get(EMPTY_SCHEMATIC_ID).getDefaultInstance());
                break;
            }
        }
    }

}
