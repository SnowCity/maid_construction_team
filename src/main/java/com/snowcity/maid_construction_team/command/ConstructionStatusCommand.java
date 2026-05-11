package com.snowcity.maid_construction_team.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import com.snowcity.maid_construction_team.core.schematic.ProgressivePlacer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class ConstructionStatusCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mct")
                .then(Commands.literal("status")
                        .executes(ConstructionStatusCommand::execute)
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("该命令只能由玩家执行"));
            return 0;
        }

        PlacementSessionManager mgr = PlayerSessionManager.of(player);
        Collection<PlacementSession> activeSessions = mgr.getActiveSessions();

        if (activeSessions.isEmpty()) {
            player.sendSystemMessage(Component.literal("§e你当前没有正在进行的建造会话"));
            return 1;
        }

        for (PlacementSession session : activeSessions) {
            ProgressivePlacer placer = session.getPlacer();
            if (placer == null) {
                player.sendSystemMessage(Component.literal("§c会话 " + session.getSessionId().toString().substring(0, 8) + "… 还没有关联的放置器"));
                continue;
            }

            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§6===== 建造状态 (会话 " + session.getSessionId().toString().substring(0, 8) + "…) ====="));
            player.sendSystemMessage(Component.literal(
                    String.format("§7劳动力数量: §f%d  |  每劳动力块数: §b+%d",
                            placer.getLastLaborerCount(),
                            MaidConstructionTeamConfig.getInstance().getLaborerBonus())
            ));
            player.sendSystemMessage(Component.literal(
                    String.format("§7契约加成块: §b+%d  |  间隔减少: §b-%d 刻",
                            placer.getLastContractBlocks(),
                            placer.getLastContractInterval())
            ));
            player.sendSystemMessage(Component.literal(
                    String.format("§7实际每 %d 刻放置 §f%d §7个方块",
                            placer.getLastActualInterval(),
                            placer.getLastEffectiveBlocksPerTick())
            ));

            player.sendSystemMessage(Component.literal(
                    String.format("§7工具耐久节省: §b%.0f%%", placer.getLastDurabilitySave() * 100)
            ));

            player.sendSystemMessage(Component.literal("§7激活的契约: " + session.getActiveContractIds().size() + " 个"));

        }
        return 1;
    }
}