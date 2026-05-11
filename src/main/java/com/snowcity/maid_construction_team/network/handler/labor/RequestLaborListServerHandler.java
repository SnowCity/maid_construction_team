package com.snowcity.maid_construction_team.network.handler.labor;

import com.snowcity.maid_construction_team.api.labor.ILaborProvider;
import com.snowcity.maid_construction_team.api.labor.LaborInfo;
import com.snowcity.maid_construction_team.api.labor.LaborProviderRegistry;
import com.snowcity.maid_construction_team.api.contract.ContractBonusManager;
import com.snowcity.maid_construction_team.network.payload.labor.LaborListPayload;
import com.snowcity.maid_construction_team.network.payload.labor.RequestLaborListPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RequestLaborListServerHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLaborListServerHandler.class);

    public static void handle(final RequestLaborListPayload payload, final IPayloadContext context) {
        LOGGER.info("[RosterServer] Received request from {}", context.player().getName().getString());
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            List<LaborInfo> allLabor = new ArrayList<>();
            for (ILaborProvider provider : LaborProviderRegistry.getEnabledProviders()) {
                if (provider instanceof ContractBonusManager) continue; // 安全跳过
                allLabor.addAll(provider.scanLabor(player)); //
            }
            PacketDistributor.sendToPlayer(player, new LaborListPayload(allLabor));
        });
    }
}