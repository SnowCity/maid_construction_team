//package com.snowcity.maid_construction_team.command;
//
//import com.mojang.brigadier.CommandDispatcher;
//import com.mojang.brigadier.arguments.StringArgumentType;
//import com.mojang.brigadier.context.CommandContext;
//import com.mojang.brigadier.exceptions.CommandSyntaxException;
//import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
//import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
//import com.snowcity.maid_construction_team.core.schematic.*;
//import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
//import net.minecraft.commands.CommandSourceStack;
//import net.minecraft.commands.Commands;
//import net.minecraft.network.chat.Component;
//import net.minecraft.server.level.ServerPlayer;
//import net.minecraft.server.level.ServerLevel;
//import net.minecraft.world.level.block.Rotation;
//import net.minecraft.world.item.ItemStack;
//import net.minecraft.world.level.block.state.BlockState;
//
//import java.util.Set;
//import java.util.UUID;
//
///**
// * 会话管理器的测试命令。
// * 使用前缀 /mct test_session 避免与其它模组或原版命令冲突。
// */
//public class TestSessionCommand {
//
//    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
//        dispatcher.register(
//                Commands.literal("mct")                                    // 模组前缀
//                        .then(Commands.literal("test_session")                // 子命令组
//                                .then(Commands.literal("create")
//                                        .executes(TestSessionCommand::createSession))
//                                .then(Commands.literal("list")
//                                        .executes(TestSessionCommand::listSessions))
//                                .then(Commands.literal("cancel")
//                                        .then(Commands.argument("sessionId", StringArgumentType.word())
//                                                .executes(TestSessionCommand::cancelSession)))
//                                .then(Commands.literal("pause")
//                                        .then(Commands.argument("sessionId", StringArgumentType.word())
//                                                .executes(TestSessionCommand::pauseSession)))
//                                .then(Commands.literal("resume")
//                                        .then(Commands.argument("sessionId", StringArgumentType.word())
//                                                .executes(TestSessionCommand::resumeSession)))
//                        )
//        );
//    }
//
//    private static int createSession(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
//        ServerPlayer player = ctx.getSource().getPlayerOrException();
//        ServerLevel level = player.serverLevel();
//        PlacementSessionManager mgr = PlayerSessionManager.of(player);
//
//        // 创建一个空的测试蓝图数据
//        SchematicData dummyData = new SchematicData.Builder()
//                .setSize(10, 10, 10)
//                .build();
//
//        // 构建放置上下文（使用一个临时的材料供应器，永远返回成功）
//        PlacementContext context = new PlacementContext(
//                dummyData,
//                level,
//                player.blockPosition(),
//                Rotation.NONE,
//                player,
//                MaidConstructionTeamConfig.getInstance(),
//                new IMaterialProvider() {
//                    @Override
//                    public boolean tryConsume(BlockState state) { return true; }
//                    @Override
//                    public boolean deposit(ItemStack stack) { return true; }
//
//                    @Override
//                    public ItemStack findTool(BlockState targetState) {
//                        return null;
//                    }
//
//                    @Override
//                    public void returnTool(ItemStack tool) {
//
//                    }
//
//                    @Override
//                    public int getStock(BlockState state) {
//                        return 0;
//                    }
//                },
//                Set.of(player.getUUID())
//        );
//
//        UUID sessionId = mgr.startSession(context);
//        ctx.getSource().sendSuccess(
//                () -> Component.literal("Session created: " + sessionId), false);
//        return 1;
//    }
//
//    private static int listSessions(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
//        ServerPlayer player = ctx.getSource().getPlayerOrException();
//        PlacementSessionManager mgr = PlayerSessionManager.of(player);
//        if (mgr.getActiveSessions().isEmpty()) {
//            ctx.getSource().sendSuccess(
//                    () -> Component.literal("No active sessions."), false);
//            return 1;
//        }
//        for (PlacementSession s : mgr.getActiveSessions()) {
//            ctx.getSource().sendSuccess(() -> Component.literal(
//                    s.getSessionId() + " state=" + s.getState() +
//                            " blocks=" + s.getProgress().getCurrentBlockIndex() +
//                            "/" + s.getProgress().getTotalBlocks()
//            ), false);
//        }
//        return 1;
//    }
//
//    private static int cancelSession(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
//        ServerPlayer player = ctx.getSource().getPlayerOrException();
//        String idStr = StringArgumentType.getString(ctx, "sessionId");
//        UUID sessionId;
//        try {
//            sessionId = UUID.fromString(idStr);
//        } catch (IllegalArgumentException e) {
//            ctx.getSource().sendFailure(Component.literal("Invalid UUID format."));
//            return 0;
//        }
//        PlacementSessionManager mgr = PlayerSessionManager.of(player);
//        boolean ok = mgr.cancelSession(sessionId);
//        ctx.getSource().sendSuccess(
//                () -> Component.literal(ok ? "Session cancelled." : "Cancel failed (maybe already finished?)."), false);
//        return 1;
//    }
//
//    private static int pauseSession(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
//        ServerPlayer player = ctx.getSource().getPlayerOrException();
//        String idStr = StringArgumentType.getString(ctx, "sessionId");
//        UUID sessionId;
//        try {
//            sessionId = UUID.fromString(idStr);
//        } catch (IllegalArgumentException e) {
//            ctx.getSource().sendFailure(Component.literal("Invalid UUID format."));
//            return 0;
//        }
//        PlacementSessionManager mgr = PlayerSessionManager.of(player);
//        mgr.transitionToWaitingMaterials(sessionId, "test lack");
//        ctx.getSource().sendSuccess(
//                () -> Component.literal("Session paused to WAITING_MATERIALS."), false);
//        return 1;
//    }
//
//    private static int resumeSession(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
//        ServerPlayer player = ctx.getSource().getPlayerOrException();
//        String idStr = StringArgumentType.getString(ctx, "sessionId");
//        UUID sessionId;
//        try {
//            sessionId = UUID.fromString(idStr);
//        } catch (IllegalArgumentException e) {
//            ctx.getSource().sendFailure(Component.literal("Invalid UUID format."));
//            return 0;
//        }
//        PlacementSessionManager mgr = PlayerSessionManager.of(player);
//        boolean ok = mgr.resumeSession(sessionId);
//        ctx.getSource().sendSuccess(
//                () -> Component.literal(ok ? "Session resumed." : "Resume failed."), false);
//        return 1;
//    }
//}