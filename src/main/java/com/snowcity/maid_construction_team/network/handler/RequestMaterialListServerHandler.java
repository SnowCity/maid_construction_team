package com.snowcity.maid_construction_team.network.handler;

import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import com.snowcity.maid_construction_team.core.schematic.SchematicData;
import com.snowcity.maid_construction_team.network.payload.MaterialListPayload;
import com.snowcity.maid_construction_team.network.payload.RequestMaterialListPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public class RequestMaterialListServerHandler {
    public static void handle(final RequestMaterialListPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            PlacementSessionManager mgr = PlayerSessionManager.of(player);
            PlacementSession session = mgr.getSession(payload.sessionId());
            if (session == null) return;

            SchematicData schematic = session.getPlacer().getSchematicData();
            Map<Block, Integer> materials = new HashMap<>();
            for (var info : schematic.getBlocks()) {
                materials.merge(info.state().getBlock(), 1, Integer::sum);
            }

            PacketDistributor.sendToPlayer(player, new MaterialListPayload(payload.sessionId(), materials));
        });
    }
}