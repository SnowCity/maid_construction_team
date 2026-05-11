package com.snowcity.maid_construction_team.old.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.schematics.SchematicPrinter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

public class lookAtMove {

    // 移动冷却：避免每Tick都寻路（性能优化）
    private static final int MOVE_COOLDOWN_TICKS = 20; // 1秒移动一次
    // 目标距离：距离目标方块多少格以内就不移动了
    private static final double TARGET_DISTANCE = 3.0; // 3格以内

    /**
     * 让女仆面朝建造的地点
     * @param maid
     * @param printer
     */
    public static void faceMaidToBlock(EntityMaid maid, SchematicPrinter printer){
        if (maid == null || printer == null) return;
        if (maid.level().isClientSide) return;

        // 即将建造的位置
        BlockPos targetPos = printer.getCurrentTarget();
        if (targetPos == null) return;

        // 让女仆面朝这个位置
        lookAt(maid, Vec3.atCenterOf(targetPos));
    }


    private static void lookAt(LivingEntity entity, Vec3 target){

        // 眼睛
        Vec3 eyePos = entity.getEyePosition();

        // 朝向向量
        double dx = target.x - eyePos.x;
        double dy = target.y - eyePos.y;
        double dz = target.z - eyePos.z;

        // 计算水平距离
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // 计算左右朝向
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F );

        // 计算上下旋转(俯仰)
        float pitch = (float) ( -Math.toDegrees(Math.atan2(dy, horizontalDist)) );

        // 设置旋转角
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.yHeadRot = yaw;
        entity.yBodyRot = yaw;
    }

    /**
     * 让女仆靠近即将建造的方块位置
     * @param maid 女仆实体
     * @param printer 蓝图打印机
     * @param sessionTickAccumulator 会话的Tick计数器（用于冷却）
     */
    public static void moveMaidToBlock(EntityMaid maid, SchematicPrinter printer, int sessionTickAccumulator) {
        if (maid == null || printer == null) return;
        if (maid.level().isClientSide) return; // 必须在服务端

        // 1. 冷却控制：避免每Tick都寻路
        if (sessionTickAccumulator % MOVE_COOLDOWN_TICKS != 0) {
            return;
        }

        // 2. 获取目标位置
        BlockPos targetPos = printer.getCurrentTarget();
        if (targetPos == null) return;

        // 3. 检查距离：如果已经很近了，就不移动了
        double distance = maid.distanceToSqr(Vec3.atCenterOf(targetPos));
        if (distance < TARGET_DISTANCE * TARGET_DISTANCE) {
            return; // 距离小于3格，不移动
        }

        // 4. 【核心】让女仆移动到目标位置
        tryMoveToPosition(maid, targetPos);
    }

    /**
     * 尝试让女仆移动到指定位置
     * 优先使用原版寻路系统
     */
    private static void tryMoveToPosition(EntityMaid maid, BlockPos targetPos) {
        try {
            // 方案A：尝试使用女仆自己的导航管理器
            java.lang.reflect.Field navField = EntityMaid.class.getDeclaredField("navigationManager");
            navField.setAccessible(true);
            Object navManager = navField.get(maid);

            // 尝试调用导航管理器的 moveTo 方法
            java.lang.reflect.Method moveMethod = navManager.getClass().getMethod("moveTo", BlockPos.class, double.class);
            moveMethod.setAccessible(true);
            moveMethod.invoke(navManager, targetPos, 1.0);

        } catch (Exception e) {
            // 方案A失败，尝试方案B
        }

        try {
            // 方案B：尝试获取女仆的导航系统(原版)
            PathNavigation navigation = maid.getNavigation();
            if (navigation != null) {
                // 移动到目标位置，速度倍率1.0
                navigation.moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.0);
                return;
            }
        } catch (Exception e) {
            // 所有方案都失败，不做任何事
            // System.out.println("女仆移动失败：" + e.getMessage());
        }
    }
}
