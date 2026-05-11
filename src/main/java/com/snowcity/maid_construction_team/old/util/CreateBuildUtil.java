package com.snowcity.maid_construction_team.old.util;


import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.snowcity.maid_construction_team.item.custom.MoriyaIronRingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * 机械动力打印相关
 */
public class CreateBuildUtil {

    // 移动冷却：避免每Tick都寻路（性能优化）
    private static final int MOVE_COOLDOWN_TICKS = 20; // 1秒移动一次
    // 目标距离：距离目标方块多少格以内就不移动了
    private static final double TARGET_DISTANCE = 3.0; // 3格以内

    // 打印功能实现(蓝图需要时刻在女仆背包中)

    /**
     * 检查所有参与会话的女仆的材料综合是否满足需求
     *
     * @param allMaidsInSession 会话中所有女仆实体列表
     * @param req               当前方块需求
     * @return true=材料足够且已分摊消耗, false=材料不足
     */
    public static boolean checkAllMaidInv(List<EntityMaid> allMaidsInSession, ItemRequirement req) {
        // 无需求/需求无效 -> 返回成功(防止空气之类的方块卡死建造流程, 难绷)
        if (req == null || req.isEmpty() || req.isInvalid()) return true;
        if (allMaidsInSession.isEmpty()) return false;

        // 只要有任意一个女仆开启了无限模式，直接返回 true，不检查也不消耗任何材料
        // ==========================================
        for (EntityMaid maid : allMaidsInSession) {
            if (MoriyaIronRingItem.isMaidInfiniteMode(maid)) {
                // 找到了开启无限模式的女仆，直接放行
                return true;
            }
        }

        // ==========================================
        // 以下是原有的正常材料检查逻辑（无限模式下不会执行到这里）
        // ==========================================

        // 所有女仆拥有的材料总和
        for (var stackReq : req.getRequiredItems()) {
            int totalNeeded = stackReq.stack.getCount();
            int totalAvailable = 0;

            // 遍历所有女仆，统计可用材料总量
            for (EntityMaid maid : allMaidsInSession) {
                ItemStackHandler inv = maid.getMaidInv();
                if (inv == null) continue;
                for (int slot = 0; slot < inv.getSlots() && totalAvailable < totalNeeded; slot++) {
                    ItemStack stack = inv.getStackInSlot(slot);
                    if (stack.isEmpty() || !isItemMatch(stackReq, stack)) continue;
                    if (isItemMatch(stackReq, stack)) {
                        totalAvailable += stack.getCount();
                    }
                }
            }

            // 任意一种材料总量不足 → 直接返回失败
            if (totalAvailable < totalNeeded) return false;
        }

        // 遍历所有女仆，分摊扣除
        for (var stackReq : req.getRequiredItems()) {
            int needed = stackReq.stack.getCount();
            var useType = stackReq.usage;

            // 遍历所有女仆，分摊扣除
            for (EntityMaid maid : allMaidsInSession) {
                if (needed <= 0) break; // 已经扣够了，跳过剩余女仆

                ItemStackHandler inv = maid.getMaidInv();
                for (int slot = 0; slot < inv.getSlots() && needed > 0; slot++) {
                    ItemStack stack = inv.getStackInSlot(slot);
                    if (stack.isEmpty()) continue;
                    if (!isItemMatch(stackReq, stack)) continue;

                    // 根据消耗类型处理
                    if (useType == ItemRequirement.ItemUseType.CONSUME) {
                        // 直接消耗物品
                        int extractCount = Math.min(stack.getCount(), needed);
                        inv.extractItem(slot, extractCount, false);
                        needed -= extractCount;
                    } else if (useType == ItemRequirement.ItemUseType.DAMAGE) {
                        // 消耗工具耐久
                        int damageToApply = Math.min(stack.getMaxDamage() - stack.getDamageValue(), needed);
                        stack.setDamageValue(stack.getDamageValue() + damageToApply);
                        needed -= damageToApply;
                        // 耐久耗尽清空物品
                        if (stack.getDamageValue() >= stack.getMaxDamage()) {
                            inv.setStackInSlot(slot, ItemStack.EMPTY);
                        }
                    }
                }
            }
        }
        // 所有材料消耗完成
        return true;
    }


    /**
     * 私有辅助方法：判断物品是否匹配蓝图的材料需求
     * 支持两种匹配规则：严格NBT匹配、仅物品类型匹配
     *
     * @param req   蓝图的材料需求项
     * @param stack 待匹配的物品栈
     * @return true=物品匹配需求，false=不匹配
     */
    private static boolean isItemMatch(ItemRequirement.StackRequirement req, ItemStack stack) {
        // 严格NBT匹配：物品类型+NBT完全一致
        if (req instanceof ItemRequirement.StrictNbtStackRequirement) {
            return ItemStack.isSameItemSameComponents(req.stack, stack);
        } else {
            // 普通匹配：仅物品类型一致（忽略NBT）
            return ItemStack.isSameItem(req.stack, stack);
        }
    }


    /**
          * 获取蓝图打印机当前的材料需求
          * 捕获所有异常，避免打印机状态异常导致的崩溃
          * @param printer 蓝图打印机实例
          * @return 当前材料需求（null=无需求/打印机异常）
          */
    public static ItemRequirement safelyGetRequirement(SchematicPrinter printer) {
        try {
            return printer.getCurrentRequirement();
        }catch (Exception e) {
            // 捕获打印机状态异常、空指针等所有异常，返回null
            return null;
        }
    }

    /**
     * 执行方块/实体放置
     * @param printer
     * @param level
     */
    public static void handlePlacement(SchematicPrinter printer, Level level) {
        // 方块放置处理器
        SchematicPrinter.BlockTargetHandler blockHandler = (pos, state, be) -> {
            try {
                int flags = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS;
                // 放置
                level.setBlock(pos, state, flags);

                if (be != null) {
                    BlockEntity newBE = level.getBlockEntity(pos);
                    if (newBE != null) {
                        CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
                        newBE.loadWithComponents(tag, level.registryAccess());
                        newBE.setChanged();
                    }
                }
            } catch (Exception ignored) {}
        };

        // 实体放置处理器
        SchematicPrinter.EntityTargetHandler entityHandler = (pos, entity) -> {
            try {
                Entity copy = entity.getType().create(level);
                if (copy != null) {
                    CompoundTag tag = new CompoundTag();
                    entity.saveWithoutId(tag);
                    copy.load(tag);
                    copy.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, entity.getYRot(), entity.getXRot());
                    // 放置实体
                    level.addFreshEntity(copy);
                }
            } catch (Exception ignored) {}
        };

        printer.handleCurrentTarget(blockHandler, entityHandler);
    }

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
