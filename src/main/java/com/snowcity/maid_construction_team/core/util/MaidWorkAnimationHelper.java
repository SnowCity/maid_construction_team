package com.snowcity.maid_construction_team.core.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "touhou_little_maid")
public class MaidWorkAnimationHelper {

    private static final int MOVE_COOLDOWN_TICKS = 20;
    private static final double TARGET_DISTANCE = 3.0;

    /**
     * 让女仆面朝目标并挥臂，每隔 moveCooldown 尝试寻路靠近。
     *
     * @param maid       女仆实体
     * @param targetPos  目标方块坐标
     * @param tickCounter 当前放置器的 tick 计数（用于冷却）
     */
    public static void animateMaid(EntityMaid maid, BlockPos targetPos, int tickCounter) {
        if (maid.level().isClientSide) return;

        // 面朝目标
        lookAt(maid, Vec3.atCenterOf(targetPos));
        // 挥臂（每 tick 可挥，或按需控制）
        maid.swing(InteractionHand.MAIN_HAND, true);

        // 移动冷却
        if (tickCounter % MOVE_COOLDOWN_TICKS != 0) return;

        double distanceSq = maid.distanceToSqr(Vec3.atCenterOf(targetPos));
        if (distanceSq < TARGET_DISTANCE * TARGET_DISTANCE) return;

        tryMoveToTarget(maid, targetPos);
    }

    private static void lookAt(EntityMaid maid, Vec3 target) {
        Vec3 eyePos = maid.getEyePosition();
        double dx = target.x - eyePos.x;
        double dy = target.y - eyePos.y;
        double dz = target.z - eyePos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDist)));
        maid.setYRot(yaw);
        maid.setXRot(pitch);
        maid.yHeadRot = yaw;
        maid.yBodyRot = yaw;
    }

    private static void tryMoveToTarget(EntityMaid maid, BlockPos targetPos) {
        try {
            // 尝试使用 Touhou Little Maid 的导航管理器
            java.lang.reflect.Field navField = EntityMaid.class.getDeclaredField("navigationManager");
            navField.setAccessible(true);
            Object navManager = navField.get(maid);
            java.lang.reflect.Method moveMethod = navManager.getClass().getMethod("moveTo", BlockPos.class, double.class);
            moveMethod.setAccessible(true);
            moveMethod.invoke(navManager, targetPos, 1.0);
        } catch (Exception e) {
            // 回退到原版导航
            PathNavigation navigation = maid.getNavigation();
            if (navigation != null) {
                navigation.moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.0);
            }
        }
    }
}