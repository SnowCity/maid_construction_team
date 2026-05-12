package com.snowcity.maid_construction_team.network.handler;

import com.snowcity.maid_construction_team.client.screen.SessionDetailScreen;
import com.snowcity.maid_construction_team.network.payload.MaterialListPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MaterialListClientHandler {
    public static void handle(final MaterialListPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SessionDetailScreen screen) {
                screen.onMaterialListReceived(payload.materials());
            }
        });
    }
}