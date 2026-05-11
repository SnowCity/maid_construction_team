package com.snowcity.maid_construction_team.network.handler.session;

import com.snowcity.maid_construction_team.client.cache.ClientSessionCache;
import com.snowcity.maid_construction_team.client.screen.ScheduleScreen;
import com.snowcity.maid_construction_team.client.screen.SessionDetailScreen;
import com.snowcity.maid_construction_team.network.payload.session.SessionsResponsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端处理器：接收服务端返回的会话数据。
 * <p>
 * 根据响应包的内容：
 * <ul>
 *   <li>如果包含 {@link SessionsResponsePayload#detail()} (详情的 Optional 不为空)，
 *       则打开 {@link SessionDetailScreen} 展示该会话的详细信息。</li>
 *   <li>否则，将 {@link SessionsResponsePayload#sessions()} (摘要列表) 传递给
 *       当前打开的 {@link ScheduleScreen} 进行列表更新。</li>
 * </ul>
 */
public class SessionsResponseClientHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionsResponseClientHandler.class);

    public static void handle(final SessionsResponsePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 情况1：服务端返回了会话详情 → 打开详情界面
            if (payload.detail().isPresent()) {
                SessionsResponsePayload.SessionDetail detail = payload.detail().get();
                // 检查当前屏幕是否为详情屏幕，是则直接更新，否则打开新屏幕
                Screen currentScreen = Minecraft.getInstance().screen;
                if (currentScreen instanceof SessionDetailScreen detailScreen) {
                    detailScreen.updateDetail(detail);
                } else {
                    Minecraft.getInstance().setScreen(new SessionDetailScreen(detail));
                }
                return;
            }

            // 情况2：服务端返回了会话摘要列表 → 更新主界面列表
            if (payload.detail().isEmpty()) {
                // 摘要列表
                List<SessionsResponsePayload.SessionSummary> summaries = payload.sessions(); // 假设类型
                // 更新全局缓存
                List<ClientSessionCache.SessionInfo> cacheList = new ArrayList<>();
                for (var s : summaries) {
                    cacheList.add(new ClientSessionCache.SessionInfo(s.sessionId(), s.blueprintName()));
                }
                ClientSessionCache.updateSessions(cacheList);

                // 原有逻辑…
                ScheduleScreen screen = ScheduleScreen.getCurrentInstance();
                if (screen != null) {
                    screen.updateSessions(payload.sessions());
                }
            }
        });
    }
}