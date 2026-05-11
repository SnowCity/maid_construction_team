package com.snowcity.maid_construction_team.client.cache;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class ClientSessionCache {
    private static final Map<UUID, SessionInfo> sessions = new HashMap<>();

    public record SessionInfo(UUID sessionId, String blueprintName) {}

    /** 全量更新缓存（在收到服务端摘要列表时调用） */
    public static void updateSessions(List<SessionInfo> list) {
        sessions.clear();
        for (SessionInfo info : list) {
            sessions.put(info.sessionId(), info);
        }
    }

    /** 获取会话名称，若缓存中没有则返回占位符 */
    public static String getSessionName(UUID sessionId) {
        SessionInfo info = sessions.get(sessionId);
        if (info != null) return info.blueprintName();
        return "会话 " + sessionId.toString().substring(0, 8) + "…";
    }

    /** 退出世界或服务器时清空缓存 */
    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        sessions.clear();
    }

    public static boolean isEmpty() {
        return sessions.isEmpty();
    }
}