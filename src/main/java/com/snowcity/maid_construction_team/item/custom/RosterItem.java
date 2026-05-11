package com.snowcity.maid_construction_team.item.custom;

import com.snowcity.maid_construction_team.client.screen.RosterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 花名册物品。
 * <p>
 * 右键打开劳动力管理 GUI（花名册），用于查看和管理所有劳动力。
 */
public class RosterItem extends Item {

    public RosterItem(Properties properties) {
        super(properties.stacksTo(1)); // 不可堆叠
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide()) {
            Minecraft.getInstance().setScreen(new RosterScreen());
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}