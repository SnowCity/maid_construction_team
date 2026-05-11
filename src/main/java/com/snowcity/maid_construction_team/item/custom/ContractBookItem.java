package com.snowcity.maid_construction_team.item.custom;

import com.snowcity.maid_construction_team.component.ContractBookData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.client.screen.ContractBookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 契约之书物品。
 * <p>
 * 右键打开管理模式 GUI（列表视图、搜索、重命名、激活/召回等）。
 * 不覆盖任何容器交互方法，仅禁止被放入其他容器中。
 */
public class ContractBookItem extends Item {

    /** 最大容量 */
    public static final int MAX_CAPACITY = 64;

    public ContractBookItem(Properties properties) {
        super(properties.stacksTo(1)); // 不可堆叠
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            Minecraft.getInstance().setScreen(new ContractBookScreen(stack, hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * 获取或创建契约之书的数据组件。
     */
    public static ContractBookData getData(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CONTRACT_BOOK_DATA.get(), ContractBookData.EMPTY);
    }

    /**
     * 设置契约之书的数据组件。
     */
    public static void setData(ItemStack stack, ContractBookData data) {
        stack.set(ModDataComponents.CONTRACT_BOOK_DATA.get(), data);
    }

    /**
     * 获取当前存储的契约数量。
     */
    public static int getCount(ItemStack stack) {
        return getData(stack).entries().size();
    }

    /**
     * 判断是否已满。
     */
    public static boolean isFull(ItemStack stack) {
        return getCount(stack) >= MAX_CAPACITY;
    }

}