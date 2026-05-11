package com.snowcity.maid_construction_team.network.handler.session;

import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import com.snowcity.maid_construction_team.core.manager.SessionStateMachine;
import com.snowcity.maid_construction_team.core.schematic.FeedbackMessage;
import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import com.snowcity.maid_construction_team.network.payload.session.ControlSessionPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ControlSessionServerHandler {

    public static void handle(final ControlSessionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            PlacementSessionManager mgr = PlayerSessionManager.of(player);
            switch (payload.action()) {
                case PAUSE -> mgr.pauseSessionManually(payload.sessionId());
                case RESUME -> {
                    PlacementSession session = mgr.getSession(payload.sessionId());
                    if (session != null) {
                        mgr.resumeSession(payload.sessionId());
                    }
                }
                case CANCEL -> mgr.cancelSession(payload.sessionId());
                case SET_STRATEGY -> {
                    PlacementSession session = mgr.getSession(payload.sessionId());
                    if (session != null) {
                        session.setShortageStrategy(payload.strategy());
                        // 可选：添加反馈消息
                        session.addFeedback(new FeedbackMessage(FeedbackMessage.Type.TASK_STARTED,
                                "策略已切换为: " + payload.strategy().name()));
                    }
                }
            }
        });
    }
}