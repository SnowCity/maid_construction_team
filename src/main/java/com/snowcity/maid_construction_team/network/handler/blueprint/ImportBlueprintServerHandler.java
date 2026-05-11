package com.snowcity.maid_construction_team.network.handler.blueprint;

import com.snowcity.maid_construction_team.component.BlueprintData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.item.MaidConstructionTeamItems;
import com.snowcity.maid_construction_team.network.payload.blueprint.ImportBlueprintPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ImportBlueprintServerHandler {

    public static void handle(final ImportBlueprintPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            int slot = payload.slot();
            if (slot < 0 || slot >= player.getInventory().items.size()) return;

            ItemStack stack = player.getInventory().items.get(slot);
            if (!stack.is(MaidConstructionTeamItems.BLUEPRINT_PAPER.get())) return;

            CompoundTag nbt = payload.schematicNbt();
            String fileName = payload.fileName();
            // 存储蓝图数据
            BlueprintData blueprintData = new BlueprintData(nbt, fileName);
            stack.set(ModDataComponents.BLUEPRINT_DATA.get(), blueprintData);
            player.getInventory().setChanged();
        });
    }
}