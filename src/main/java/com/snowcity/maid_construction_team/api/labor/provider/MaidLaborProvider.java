package com.snowcity.maid_construction_team.api.labor.provider;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.snowcity.maid_construction_team.api.labor.*;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MaidLaborProvider implements ILaborProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaidLaborProvider.class);
    private static final String PROVIDER_ID = "touhou_maid";
    private static final String DISPLAY_NAME = "女仆";

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public List<LaborInfo> scanLabor(Player player) {
        List<LaborInfo> list = new ArrayList<>();
        if (!(player.level() instanceof ServerLevel serverLevel)) return list;

        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof EntityMaid maid) {
                if (maid.getOwnerUUID() != null && maid.getOwnerUUID().equals(player.getUUID())) {
                    // 获取生物类型的注册名作为 ResourceLocation
                    ResourceLocation entityType = EntityType.getKey(maid.getType());
                    LaborInfo info = new LaborInfo(
                            maid.getUUID(),                       // laborId
                            maid.getDisplayName().getString(),    // displayName
                            entityType,                          // entityType (ResourceLocation)
                            PROVIDER_ID,                         // sourceType (String)
                            DISPLAY_NAME,                        // sourceDisplayName (String)
                            LaborStatus.IDLE,                    // status
                            Optional.empty(),                    // workingSessionId
                            Optional.empty()                     // workingSessionName
                    );
                    list.add(info);
                }
            }
        }
        return list;
    }

    @Override
    public boolean dispatch(UUID laborId, PlacementSession session, Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return false;
        Entity entity = serverLevel.getEntity(laborId);
        if (entity instanceof EntityMaid maid) {
            if (maid.getOwnerUUID() != null && maid.getOwnerUUID().equals(player.getUUID())) {
                session.addParticipant(laborId);
                LOGGER.debug("[MaidLabor] Dispatched maid {}", laborId);
            }
        }
        return false;
    }

    @Override
    public boolean recall(UUID laborId, Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return false;
        var mgr = PlayerSessionManager.of(player);
        for (PlacementSession session : mgr.getActiveSessions()) {
            if (session.getParticipantUuids().contains(laborId)) {
                session.removeParticipant(laborId);
                LOGGER.debug("[MaidLabor] Recalled maid {}", laborId);
            }
        }
        return false;
    }

    @Override
    public boolean isEnabled() {
        // 启用女仆劳动力
        return true;
    }
}