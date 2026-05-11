package com.snowcity.maid_construction_team.network.handler.labor;

import com.snowcity.maid_construction_team.api.labor.LaborInfo;
import com.snowcity.maid_construction_team.client.screen.RosterScreen;
import com.snowcity.maid_construction_team.network.payload.labor.LaborListPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public class LaborListClientHandler {
    public static void handle(LaborListPayload payload, IPayloadContext context) {
        List<LaborInfo> laborList = payload.laborInfos();
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof RosterScreen rosterScreen) {
                rosterScreen.updateLaborList(laborList);
            }
        });
    }
}