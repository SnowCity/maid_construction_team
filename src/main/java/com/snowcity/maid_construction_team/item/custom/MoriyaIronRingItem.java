package com.snowcity.maid_construction_team.item.custom;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MoriyaIronRingItem extends Item {
    public MoriyaIronRingItem(Properties properties) {
        super(properties);
    }

    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, Player player, @NotNull LivingEntity entity, @NotNull InteractionHand hand) {
        // 只在服务端处理
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (entity instanceof EntityMaid maid) {
            // 校验主人
            if (!maid.getOwnerUUID().equals(player.getUUID())) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你不是这个女仆的主人！"));
                return InteractionResult.FAIL;
            }

            // 切换状态
            boolean isInfinite = !isMaidInfiniteMode(maid);
            setMaidInfiniteMode(maid, isInfinite);

            String msg = isInfinite ? "§a已开启女仆的无限建造模式！" : "§c已关闭女仆的无限建造模式";
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // ============================ 辅助方法 ============================
    public static boolean isMaidInfiniteMode(EntityMaid maid) {
        return maid.getPersistentData().getBoolean("MaidCT_InfiniteBuild");
    }

    public static void setMaidInfiniteMode(EntityMaid maid, boolean isInfinite) {
        maid.getPersistentData().putBoolean("MaidCT_InfiniteBuild", isInfinite);
    }
}
