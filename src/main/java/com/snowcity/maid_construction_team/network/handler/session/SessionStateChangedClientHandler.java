package com.snowcity.maid_construction_team.network.handler.session;

import com.snowcity.maid_construction_team.client.screen.ScheduleScreen;
import com.snowcity.maid_construction_team.client.screen.SessionDetailScreen;
import com.snowcity.maid_construction_team.core.manager.SessionStateMachine;
import com.snowcity.maid_construction_team.network.payload.session.RequestSessionsPayload;
import com.snowcity.maid_construction_team.network.payload.session.SessionStateChangedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * 客户端处理器：接收服务端推送的会话状态变更通知。
 * <p>
 * 当会话状态发生改变（完成、取消、暂停、恢复等）时，服务端会主动发送
 * {@link SessionStateChangedPayload} 到创建者客户端。本处理器根据当前打开的
 * 界面类型执行相应的刷新或关闭操作：
 * <ul>
 *   <li>若详情界面正显示该会话且会话已结束（COMPLETED 或 CANCELLED）：
 *       自动关闭详情界面，返回规划表主界面。</li>
 *   <li>若详情界面正显示该会话但未结束：向服务端请求最新详情数据以刷新界面。</li>
 *   <li>若规划表主界面打开：请求最新的会话摘要列表以刷新列表。</li>
 * </ul>
 */
public class SessionStateChangedClientHandler {

    public static void handle(final SessionStateChangedPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Screen currentScreen = mc.screen;

            // ----- 处理详情界面 -----
            if (currentScreen instanceof SessionDetailScreen detailScreen) {
                // 检查当前详情界面的会话 ID 是否与通知中的一致
                if (detailScreen.getSessionId().equals(payload.sessionId())) {
                    // 如果会话已结束，自动关闭详情界面，并返回到规划表主界面
                    if (payload.newState() == SessionStateMachine.State.COMPLETED ||
                            payload.newState() == SessionStateMachine.State.CANCELLED) {
                        // 直接打开新的 ScheduleScreen，会自动请求最新的会话列表
                        mc.setScreen(new ScheduleScreen());
                        return; // 处理完毕，无需继续检查主界面
                    } else {
                        // 会话状态变更但未结束（例如从 RUNNING 变为 WAITING_MATERIALS，
                        // 或从 PAUSED 恢复为 RUNNING），向服务端请求最新详情以刷新界面显示
                        // 注意：这里通过发送 RequestSessionsPayload 携带 sessionId 来获取最新数据
                        PacketDistributor.sendToServer(
                                new RequestSessionsPayload(Optional.of(payload.sessionId())));
                    }
                }
            }

            // ----- 处理主界面 -----
            if (currentScreen instanceof ScheduleScreen scheduleScreen) {
                // 会话状态变更会影响列表显示（例如完成/取消的会话应移除，状态标签应更新）
                // 请求最新的会话摘要列表以刷新主界面
                scheduleScreen.requestSessionList();
            }
        });
    }
}