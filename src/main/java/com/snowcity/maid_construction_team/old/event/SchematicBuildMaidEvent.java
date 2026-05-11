package com.snowcity.maid_construction_team.old.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAttackEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.snowcity.maid_construction_team.old.config.MaidBuildConfig;
import com.snowcity.maid_construction_team.old.manager.MaidBuildManager;
import com.snowcity.maid_construction_team.old.util.CreateBuildUtil;
import com.snowcity.maid_construction_team.old.util.MaidCreateItemUtil;
import com.snowcity.maid_construction_team.old.util.SchematicCheckUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆蓝图建筑事件类 - 处理女仆根据蓝图建筑的核心逻辑
 * <P>
 * 这个类监听女仆被攻击的事件，当玩家用特定物品攻击女仆时，触发建筑功能
 * 看起来就像女仆收到了建筑指令，开始根据蓝图工作
 */
@EventBusSubscriber(modid = "create")
public class SchematicBuildMaidEvent {

    // 玩家使用特定物品左键女仆(取消伤害)，若女仆处于schematicannon_task工作模式触发，
    // 女仆遍历背包读取蓝图，对蓝图进行校验
    // 创建蓝图打印会话，在会话监控下进行打印
    // 开始打印蓝图，蓝图打印完成后将蓝图变为空白蓝图并结束会话

    // 蓝图校验：空白，未部署，已部署，无效

    // 会话：创建一个蓝图打印的会话后，这个会话将会监控这张蓝图的整个打印流程
    // 在这个会话中，我可以随时加入新的女仆进行协调打印(每个女仆都会进行一次打印(具体实现可以为每加入一个女仆使每次建造的方块加倍))

    /**
     * 判断女仆是否是 蓝图加农炮(schematicannon_task) 工作模式
     * @param maid 女仆实体
     * @return 如果是建筑师任务则返回true
     */
    private static boolean isMaidInSchematicannonTask(EntityMaid maid){
        String path = maid.getTask().getUid().getPath();
        return path.equals("schematicannon_task");
    }


    /**
     * 左键触发蓝图打印事件
     * @param event
     */
    @SubscribeEvent
    public void onPlayerInteractMaid(MaidAttackEvent event){
        // 双端隔离
        if (event.getMaid().level().isClientSide) return;

        try {// 获取被攻击的女仆
            EntityMaid maid = event.getMaid();
            // 获取伤害来源
            DamageSource source = event.getSource();
            // 获取攻击者
            Entity attacker = source.getEntity();


            // 只有玩家攻击才触发，其他生物攻击不触发
            if (!(attacker instanceof Player player)) return;

            // 获取发动攻击的物品
            ItemStack inHand = player.getMainHandItem();

            // 校验触发物品
            if (!MaidCreateItemUtil.isTargetItem(inHand)) return;

            // 取消女仆受到的伤害，仅作为交互指令(打是亲这块)
            event.setCanceled(true);

            // 判断女仆此时的状态，如果有任务在身就暂停女仆的工作，如果无任务就开始建造
            if (MaidBuildManager.isMaidBusy(maid.getUUID())) {
                // 女仆有任务在身
                UUID sessionId = MaidBuildManager.getMaidSessionId(maid.getUUID());

                //  蹲下左键：取消任务
                if (player.isCrouching()) {
                    MaidBuildManager.cancelSession(sessionId, player);
                }
                // 正常左键：切换暂停/恢复状态
                else {
                    // 如果已暂停 → 恢复
                    if (MaidBuildManager.isSessionPaused(sessionId)) {
                        MaidBuildManager.resumeSession(sessionId, player);
                    }
                    // 如果正在运行 → 暂停
                    else {
                        MaidBuildManager.pauseSession(sessionId, player);
                    }
                }
            } else {
                // 女仆没有参与任何会话
                tryStartBuilding(maid, player);
            }
        }catch (Exception e){
            e.printStackTrace();
            if (event.getSource().getEntity() instanceof Player player) {
                player.sendSystemMessage(Component.literal("测试：建造任务创建失败,内部已崩溃"));
            }
        }
    }


    /**
     * 创建建筑会话前的全链路校验
     * @param maid
     * @param player
     */
    private static void tryStartBuilding(EntityMaid maid, Player player) {
        // 女仆的工作模式，背包里是否有蓝图， 蓝图校验(是否部署，是否有实际内容，是否数据损坏)

        // 工作模式校验
        if (!isMaidInSchematicannonTask(maid)) return;

        // 蓝图校验
        //1.存在校验
        ItemStack maidSchematic = MaidCreateItemUtil.getMaidSchematicStack(maid);

        // 2.数量校验
        int schematicCount = MaidCreateItemUtil.countSchematics(maid);
        // 没有蓝图, 聊天栏提示玩家
        if (schematicCount == 0) {
            player.sendSystemMessage(Component.literal("女仆没有蓝图"));
            return;
        }
        // 携带多个蓝图, 聊天栏提示玩家
        if (schematicCount > 1) {
            player.sendSystemMessage(Component.literal("女仆有太多蓝图"));
            return;
        }

        // 3.其他校验
        if (!SchematicCheckUtil.isCreateSchematicDeployed(player.level(), maidSchematic, player)) return;

        // 创建建筑会话
        UUID sessionId = MaidBuildManager.createSession(player, maid);
        if (sessionId == null){
            player.sendSystemMessage(Component.literal("建筑任务创建失败"));
            return;
        }

        // 初始化 Create 打印机
        SchematicPrinter printer = new SchematicPrinter();

        // 蓝图物品，世界实例，是否处理nbt(true保留方块实体数)
        printer.loadSchematic(maidSchematic, player.level(), true);

        // 传入Create对象,
        MaidBuildManager.attachContext(sessionId, printer);

        // 打印机状态校验
        if (!printer.isLoaded() || printer.isErrored()){
            player.sendSystemMessage(Component.literal("蓝图数据损坏，无法建造"));
            MaidBuildManager.cancelSession(sessionId, player);
            player.sendSystemMessage(Component.literal("建筑任务已取消"));
            return;
        }

        // 将打印机注入会话
        MaidBuildManager.attachContext(sessionId, printer);
        player.sendSystemMessage(Component.literal("§a✅ 女仆收到指令，开始建造蓝图！\n放心吧主人, 一定完成任务"));
    }

    /**
     * 服务器后置 Tick :驱动所有会话
     * 实现多女仆协同工作
     * @param event
     */
    @SubscribeEvent
    public void MaidCreateSchematicBuildingTick(ServerTickEvent.Post event){

        // 遍历所有活跃的建筑会话
        for (Map.Entry<UUID, MaidBuildManager.BuildSession> entry : MaidBuildManager.getAllSessions().entrySet()){
            UUID sessionId = entry.getKey();
            MaidBuildManager.BuildSession session = entry.getValue();
            try {
                // 会话暂停 -> 跳过
                if (session.isPaused) continue;

                // 获取服务器世界和会话内所有存活女仆
                if (!(session.player instanceof ServerPlayer serverPlayer)) continue;
                ServerLevel level = serverPlayer.serverLevel();
                List<EntityMaid> sessionMaids = MaidBuildManager.getSessionMaids(sessionId);
                if (sessionMaids.isEmpty()) continue;

                // 获取共享的蓝图打印机
                SchematicPrinter printer = (SchematicPrinter) session.context;
                if (printer == null || !printer.isLoaded() || printer.isErrored()) continue;

                // 建造速度, 可配置（全局共享，同一会话统一间隔）
                session.tickAccumulator++;
                // 读取建筑速度配置
                int buildInterval = MaidBuildConfig.buildInterval.get();

                // 最终建造间隔(配置文件-女仆数量)
                int buildIntervalSpeed = (1 + buildInterval) - MaidBuildManager.getSessionMaids(sessionId).size();

                if (session.tickAccumulator < buildIntervalSpeed) continue;
                session.tickAccumulator = 0;

                // 5. 核心建造循环（单Tick最多放置N个方块）(配置文件+女仆数量)
                int maxBlocksPerTick = (MaidBuildConfig.blocksPerTick.get() - 1) + MaidBuildManager.getSessionMaids(sessionId).size();

                int placedCount = 0;
                boolean hasMoreWork = true;
                while (placedCount < maxBlocksPerTick && hasMoreWork) {
                    // 检查当前位置是否需要放置
                    if (!printer.shouldPlaceCurrent(level)) {
                        hasMoreWork = printer.advanceCurrentPos();
                        continue;
                    }

                    // 获取当前方块的材料需求
                    var requirement = CreateBuildUtil.safelyGetRequirement(printer);
                    if (requirement == null) {
                        hasMoreWork = printer.advanceCurrentPos();
                        continue;
                    }

                    // 靠近建造位置
                    if (!sessionMaids.isEmpty()){
                        for (EntityMaid maid : sessionMaids){
                            if (maid.level().isClientSide)return;
                            CreateBuildUtil.moveMaidToBlock(maid, printer, session.tickAccumulator);
                        }
                    }

                    // 面朝建造地点
                    if (!sessionMaids.isEmpty()){
                        for (EntityMaid maid : sessionMaids){
                            if (maid.level().isClientSide)return;
                            CreateBuildUtil.faceMaidToBlock(maid, printer);
                        }
                    }

                    // 【核心协同】从所有女仆背包中分摊消耗材料
                    if (!CreateBuildUtil.checkAllMaidInv(sessionMaids, requirement)) {
                        // 材料总和不足 → 终止本次Tick，给玩家发提示
                         session.player.sendSystemMessage(Component.literal("§e⚠️ 所有女仆的材料总和不足！"));
                        return;
                    }

                    // 执行方块/实体放置
                    CreateBuildUtil.handlePlacement(printer, level);

                    // 女仆执行放置动画
                    for (EntityMaid maid : sessionMaids){
                        if (maid.level().isClientSide) return;
                        maid.swing(InteractionHand.MAIN_HAND);
                    }

                    placedCount++;

                    // 【关键】全局推进蓝图进度，所有女仆下一次Tick共享新位置
                    hasMoreWork = printer.advanceCurrentPos();
                }

                // 建造完成判断
                if (!hasMoreWork && placedCount == 0) {
                    if (!printer.advanceCurrentPos()) {
                        // 建造完成：将蓝图转为空白蓝图，并注销会话
                        // 找到初始女仆（或任意女仆），转换蓝图
                        for (EntityMaid maid : sessionMaids) {
                            ItemStack schematic = MaidCreateItemUtil.getMaidSchematicStack(maid);
                            if (!schematic.isEmpty()) {
                                MaidCreateItemUtil.replaceSchematicWithEmpty(maid, schematic);
                                break;
                            }
                        }
                        // 注销会话
                        MaidBuildManager.completeSession(sessionId);
                    }
                }
            }catch (Exception e){}
        }
    }
}
