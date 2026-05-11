package com.snowcity.maid_construction_team.api.contract;

import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public interface IContractValidator {
    @Nullable
    String canActivate(UUID contractId, PlacementSession session, Player player);
}