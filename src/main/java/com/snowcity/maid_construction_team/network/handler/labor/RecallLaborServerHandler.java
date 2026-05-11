package com.snowcity.maid_construction_team.network.handler.labor;

import com.snowcity.maid_construction_team.api.labor.ILaborProvider;
import com.snowcity.maid_construction_team.api.labor.LaborProviderRegistry;
import com.snowcity.maid_construction_team.api.contract.ContractBonusManager;
import com.snowcity.maid_construction_team.network.payload.labor.RecallLaborPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端处理器：处理召回/取消激活请求。
 * 根据 providerId 分流：
 *   - "servant_contract" → 取消契约加成
 *   - 其他 → 召回劳动力（宠物等）
 */
public class RecallLaborServerHandler {

    public static void handle(final RecallLaborPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();

            if ("servant_contract".equals(payload.providerId())) {
                // 契约加成取消
                ContractBonusManager.deactivate(payload.laborId(), player);
            } else {
                // 劳动力召回
                ILaborProvider provider = LaborProviderRegistry.getProvider(payload.providerId()).orElse(null);
                if (provider != null) {
                    provider.recall(payload.laborId(), player);
                }
            }
        });
    }
}