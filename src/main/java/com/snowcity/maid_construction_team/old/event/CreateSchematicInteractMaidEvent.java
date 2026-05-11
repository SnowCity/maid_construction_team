package com.snowcity.maid_construction_team.old.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Sets;
import com.simibubi.create.content.schematics.cannon.MaterialChecklist;
import com.snowcity.maid_construction_team.old.util.MaidBookUtil;
import com.snowcity.maid_construction_team.old.util.MaidCreateItemUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.*;

/**
 * 蓝图交互事件类 - 处理玩家与女仆的蓝图交互
 * <p>
 * 这个类监听玩家与女仆的交互事件，当玩家手持书或剪贴板与女仆交互时
 * 会生成材料清单
 */
@EventBusSubscriber(modid = "create")
public class CreateSchematicInteractMaidEvent {

    // 手持剪贴板(书与笔)右键蓝图加农炮工作模式的女仆, 女仆检索身上是否存在蓝图, 没有提示没有; 有提示有, 并且确认是否部署, 之后打印在交互物品上(替换)

    /**
     * 判断女仆是否是 蓝图加农炮(schematicannon_task) 工作模式
     * @param maid 女仆实体
     * @return 如果是建筑师任务则返回true
     */
    private boolean isMaidInSchematicannonTask(EntityMaid maid){
        String path = maid.getTask().getUid().getPath();
        return path.equals("schematicannon_task");
    }


    /**
     * 当玩家手持书或剪贴板与女仆交互时，生成蓝图的材料清单
     * @param event 交互事件
     */
    @SubscribeEvent
    public void onMaidRightInteract(InteractMaidEvent event){
        // 仅在服务端运行
        if (event.getMaid().level().isClientSide) return;

        // 获取交互的玩家
        Player player = event.getPlayer();
        // 获取游戏世界
        Level level = event.getWorld();
        // 获取被交互的女仆
        EntityMaid maid = event.getMaid();

        // 1.获取玩家用来交互的物品
        ItemStack inHand = event.getStack();

        // 2.判定: 女仆处于 schematicannon_task 工作模式且互动物品为书或者剪贴板
        if (MaidCreateItemUtil.isTargetItem(inHand) && isMaidInSchematicannonTask(maid)) {

            // 取消交互事件，防止触发其他交互
            event.setCanceled(true);

            // 3.女仆检索身上是否存在蓝图
            // 检查蓝图数量
            int schematicCount = MaidCreateItemUtil.countSchematics(maid);
            // 没有蓝图, 聊天栏提示玩家
            if (schematicCount == 0) {
                player.sendSystemMessage(Component.literal("女仆没有蓝图"));
                event.setCanceled(true);
                return;
            }
            // 携带多个蓝图, 聊天栏提示玩家
            if (schematicCount > 1) {
                player.sendSystemMessage(Component.literal("女仆有太多蓝图"));
                event.setCanceled(true);
                return;
            }

            // 4.获取女仆携带的蓝图
            ItemStack MaidSchematic = MaidCreateItemUtil.getMaidSchematicStack(maid);
            // 遍历蓝图得到材料清单
            MaterialChecklist checklist = MaidCreateItemUtil.collectRequirements(level, MaidSchematic, player);
            // 材料列表为空, 说明蓝图未部署, 聊天栏提示玩家
            if (checklist == null) {
                player.sendSystemMessage(Component.literal("§c❌ 女仆背包里没有已部署的蓝图！"));
                return;
            }

            // 运行到这里说明蓝图正确, 开始计算材料
            player.sendSystemMessage(Component.literal("开始计算材料"));
            // 统计身上的物品是否满足材料清单
            boolean enough = areMaterialsEnough(maid, checklist);
            if (enough) {
                // 提示材料足够
                player.sendSystemMessage(Component.literal("材料充足"));
            }

            // 6.打印材料清单
            // 根据交互物品来生成清单
            ItemStack BOQ = ItemStack.EMPTY;
            // 如果是剪贴板，生成剪贴板形式的清单
            if (MaidCreateItemUtil.isCreateClipboard(inHand)) {
                BOQ = checklist.createWrittenClipboard();
                BOQ.setCount(inHand.getCount());
                // 提示材料已打印
                player.sendSystemMessage(Component.literal("材料列表已打印在剪贴板中"));
                // 如果是书，生成书形式的清单
            } else if (inHand.is(Items.BOOK) || inHand.is(Items.WRITTEN_BOOK)) {
                // 1. 调用提取的方法构建 Create 风格书页
                List<Component> pages = buildCreateStyleChecklistPages(checklist);

                // 2. 调用工具类生成书
                BOQ = MaidBookUtil.createCustomWithComponents(
                        "蓝图材料清单",
                        maid.getName().getString(), // 作者
                        pages,
                        inHand.getCount()
                );
                // 提示材料已打印
                player.sendSystemMessage(Component.literal("材料列表已打印在书中"));
            }
            // 如果成功生成的清单不为空，替换玩家用来交互的物品
            if (!BOQ.isEmpty()) {
                for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
                    if (player.getItemInHand(hand) == inHand) {
                        player.setItemInHand(hand, BOQ);
                        break;
                    }
                }
            }
        }
    }





// ========== 修改后的书页构建方法（物品名蓝色版） ==========
    /**
     * 完全复刻 Create 剪贴板风格，物品名显示为蓝色
     */
    private static List<Component> buildCreateStyleChecklistPages(MaterialChecklist checklist) {
        List<Component> pages = new ArrayList<>();
        net.minecraft.network.chat.MutableComponent currentPage = Component.empty();

        final int MATERIALS_PER_PAGE = 6;
        int currentMaterialCount = 0;

        Set<Item> keys = Sets.union(checklist.required.keySet(), checklist.damageRequired.keySet());
        for (Item item : keys) {
            int need = checklist.getRequiredAmount(item);
            int have = checklist.gathered.getInt(item);
            int missing = Math.max(need - have, 0);

            // 物品名设为蓝色
            Component itemNameBlue = item.getName(item.getDefaultInstance())
                    .copy()
                    .withStyle(net.minecraft.ChatFormatting.BLUE);

            // 数量行第一部分：x需求（白色，默认）
            Component quantityPart = Component.literal(String.format("x%d", need));

            // 数量行第二部分：| 拥有 +缺额（灰色）
            Component grayInfoPart = Component.literal(String.format(" | %d +%d\n", have, missing))
                    .withStyle(net.minecraft.ChatFormatting.GRAY);

            if (currentMaterialCount >= MATERIALS_PER_PAGE) {
                pages.add(currentPage);
                currentPage = Component.empty();
                currentMaterialCount = 0;
            }

            // 拼接：蓝色物品名 + 换行 + 白色x需求 + 灰色信息 + 换行
            currentPage = currentPage.append(itemNameBlue).append("\n");
            currentPage = currentPage.append(quantityPart).append(grayInfoPart);

            currentMaterialCount++;
        }

        if (!currentPage.getSiblings().isEmpty()) {
            pages.add(currentPage);
        }

        return pages;
    }


    /**
     * 检查材料是否足够建造蓝图
     *
     * 这个方法会统计女仆身上所有可用的材料，并与蓝图需求进行比较
     *
     * @param maid 女仆实体
     * @param checklist 材料清单，包含蓝图所需的所有材料
     * @return 如果材料足够则返回true
     */
    public static boolean areMaterialsEnough(EntityMaid maid, MaterialChecklist checklist) {
        // 获取女仆的物品栏
        ItemStackHandler inv = maid.getMaidInv();
        // 清空已收集的材料统计
        checklist.gathered.clear();

        // 获取主手和副手的物品
        ItemStack main = maid.getMainHandItem();
        ItemStack off = maid.getOffhandItem();

        // 收集主手的材料
        if (!main.isEmpty()) {
            checklist.collect(main);
        }
        // 收集副手的材料（确保不重复收集）
        if (!off.isEmpty() && off != main) {
            checklist.collect(off);
        }

        // 收集物品栏中的材料
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack != main && stack != off) {
                checklist.collect(stack);
            }
        }

        // 获取所有需要的材料类型
        Set<Item> keys = Sets.union(checklist.required.keySet(), checklist.damageRequired.keySet());
        Object2IntMap<Item> gathered = checklist.gathered;

        // 检查每种材料是否足够
        for (Item item : keys) {
            int need = checklist.getRequiredAmount(item);  // 需要的数量
            int have = gathered.getInt(item);              // 拥有的数量
            if (have < need) {
                // 有材料不足，返回false
                return false;
            }
        }
        // 所有材料都足够
        return true;
    }
}
