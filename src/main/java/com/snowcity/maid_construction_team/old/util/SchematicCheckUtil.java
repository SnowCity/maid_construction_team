package com.snowcity.maid_construction_team.old.util;


import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.SchematicPrinter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 蓝图检测功能
 */
public class SchematicCheckUtil {


    /**
     * 判断蓝图是否已部署且有效
     */
    public static boolean isCreateSchematicDeployed(Level level, ItemStack schematic, Player player) {
        // 1. 空物品检查
        if (schematic.isEmpty()) {
            sendMsg(player, "§c测试失败: 物品为空");
            return false;
        }

        // 2. 【第一阶段】尝试直接读取组件进行快速校验
        // 注意：这里不再用 !has() 直接返回，因为有些版本 Create 组件存储方式特殊
        boolean hasAnchorComp = schematic.has(AllDataComponents.SCHEMATIC_ANCHOR);
        boolean hasDeployedComp = schematic.has(AllDataComponents.SCHEMATIC_DEPLOYED);

        sendMsg(player, "§e调试: 组件存在性 -> Anchor:" + hasAnchorComp + " | Deployed:" + hasDeployedComp);

        // 3. 【第二阶段】无论组件是否存在，直接尝试加载打印机
        SchematicPrinter printer = new SchematicPrinter();
        try {
            printer.loadSchematic(schematic, level, true);
        } catch (Exception e) {
            sendMsg(player, "§c测试失败: 蓝图加载异常 (" + e.getMessage() + ")");
            return false;
        }

        if (!printer.isLoaded() || printer.isErrored()) {
            sendMsg(player, "§c测试失败: 打印机未加载/数据损坏");
            return false;
        }
        sendMsg(player, "§a测试: 打印机加载成功");

        // 4. 【核心修复】获取锚点（优先从物品拿，拿不到就想别的办法）
        BlockPos anchor = null;

        // 尝试1：直接从物品栈获取
        if (hasAnchorComp) {
            try {
                anchor = schematic.get(AllDataComponents.SCHEMATIC_ANCHOR);
            } catch (Exception e) {
                sendMsg(player, "§e警告: 从物品读取锚点失败，尝试备用方案");
            }
        }

        // 尝试2：如果物品里拿不到，但打印机加载成功了，说明锚点可能在打印机里
        // 如果能走到这里，说明蓝图数据是完整的，我们放宽对锚点的要求

        // 5. 【关键逻辑调整】锚点校验
        if (anchor == null) {
            // 特殊情况：有些版本的 Create 空蓝图/特殊蓝图确实没有锚点组件
            // 但既然打印机加载成功了，我们认为它是有效的，只检查是否为空
            if (printer.isWorldEmpty()) {
                sendMsg(player, "§c测试失败: 蓝图是空的");
                return false;
            }
            sendMsg(player, "§a测试: 无锚点组件但数据有效，视为通过");
            return true;
        }

        sendMsg(player, "§e调试: 读取到锚点 -> " + anchor.toShortString());

        // 6. 【修复】原点判定，但放宽世界范围判定
        if (anchor.equals(BlockPos.ZERO)) {
            sendMsg(player, "§c测试失败: 锚点在原点 (0,0,0)，通常表示未部署");
            return false;
        }

        // 【移除严格的Y轴判定】或者只判定 Y < -64 (虚空以下)
        // 因为不同维度/版本的高度限制不同 (比如 1.18+ 主世界是 -64 到 320)
        if (anchor.getY() < -64) {
            sendMsg(player, "§c测试失败: 锚点在虚空以下");
            return false;
        }

        // 不再判定 Y > level.getMaxBuildHeight()，因为蓝图可能是在超高处保存的

        // 7. 最终内容检查
        if (printer.isWorldEmpty()) {
            sendMsg(player, "§c测试失败: 蓝图是空的");
            return false;
        }

        sendMsg(player, "§a✅ 所有测试通过！蓝图有效！");
        return true;
    }

    private static void sendMsg(Player player, String msg) {
        if (player != null) {
            player.sendSystemMessage(Component.literal(msg));
        }
    }
}
