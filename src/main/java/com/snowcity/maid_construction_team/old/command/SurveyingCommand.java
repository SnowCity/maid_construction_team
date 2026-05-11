package com.snowcity.maid_construction_team.old.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.snowcity.maid_construction_team.item.custom.SurveyingToolItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 测绘仪命令注册
 * 用于处理蓝图保存的服务端逻辑
 */
public class SurveyingCommand {

    /**
     * 注册命令
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("maid_save")
                .requires(source -> source.isPlayer()) // 仅玩家可执行
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> {
                            Player player = context.getSource().getPlayerOrException();
                            String fileName = StringArgumentType.getString(context, "name");

                            // 尝试从主手获取测绘仪
                            ItemStack mainHand = player.getMainHandItem();
                            if (mainHand.getItem() instanceof SurveyingToolItem) {
                                executeSave(player, mainHand, fileName);
                                return 1;
                            }

                            // 尝试从副手获取测绘仪
                            ItemStack offHand = player.getOffhandItem();
                            if (offHand.getItem() instanceof SurveyingToolItem) {
                                executeSave(player, offHand, fileName);
                                return 1;
                            }

                            // 未持有测绘仪
                            player.displayClientMessage(
                                    net.minecraft.network.chat.Component.literal("§c请手持测绘仪再执行保存！"),
                                    true
                            );
                            return 0;
                        })
                )
        );
    }

    /**
     * 执行保存逻辑
     */
    private static void executeSave(Player player, ItemStack stack, String fileName) {
        // 直接调用 SurveyingToolItem 的静态保存方法
        SurveyingToolItem.saveSchematic(player, stack, fileName);
    }
}