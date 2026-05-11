package com.snowcity.maid_construction_team.network.handler.blueprint;

import com.snowcity.maid_construction_team.component.BlueprintData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import com.snowcity.maid_construction_team.core.schematic.*;
import com.snowcity.maid_construction_team.core.schematic.provider.ContainerMarkMaterialProvider;
import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
import com.snowcity.maid_construction_team.network.payload.blueprint.StartPlacementPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Set;

public class StartPlacementServerHandler {

    public static void handle(final StartPlacementPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ItemStack stack = payload.blueprintStack();

            BlueprintData data = stack.get(ModDataComponents.BLUEPRINT_DATA.get());
            if (data == null) return;

            // 获取蓝图文件名
            String name = data.fileName();
            if (name == null || name.isEmpty()) {
                name = "未命名蓝图";
            }

            SchematicData schematic = data.toSchematicData();

            IMaterialProvider materialProvider = new ContainerMarkMaterialProvider(
                    player,
                    Set.of(player.getUUID())
            );

            PlacementContext placementContext = new PlacementContext(
                    schematic,
                    player.serverLevel(),
                    payload.anchor(),
                    payload.rotation(),
                    player,
                    MaidConstructionTeamConfig.getInstance(),
                    materialProvider,
                    Set.of(player.getUUID()),
                    name   // 把文件名传入
            );

            PlayerSessionManager.of(player).startSession(placementContext);
        });
    }
}