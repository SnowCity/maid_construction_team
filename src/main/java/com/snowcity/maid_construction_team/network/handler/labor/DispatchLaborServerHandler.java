package com.snowcity.maid_construction_team.network.handler.labor;

import com.snowcity.maid_construction_team.api.labor.ILaborProvider;
import com.snowcity.maid_construction_team.api.labor.LaborProviderRegistry;
import com.snowcity.maid_construction_team.api.contract.ContractBonusManager;
import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import com.snowcity.maid_construction_team.network.payload.labor.DispatchLaborPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端处理器：处理派遣/激活请求。
 * 根据 providerId 分流：
 *   - "servant_contract" → 激活契约加成
 *   - 其他 → 派遣劳动力（宠物等）
 */
public class DispatchLaborServerHandler {

    public static void handle(final DispatchLaborPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            PlacementSessionManager mgr = PlayerSessionManager.of(player);
            PlacementSession session = mgr.getSession(payload.sessionId());
            if (session == null) return;

            if ("servant_contract".equals(payload.providerId())) {
                // 契约加成激活
                ContractBonusManager.activate(payload.laborId(), session, player);
            } else {
                // 劳动力派遣
                ILaborProvider provider = LaborProviderRegistry.getProvider(payload.providerId()).orElse(null);
                if (provider != null) {
                    provider.dispatch(payload.laborId(), session, player);
                }
            }
        });
    }
}