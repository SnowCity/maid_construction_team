package com.snowcity.maid_construction_team.api.labor.provider;

import com.snowcity.maid_construction_team.api.labor.ILaborProvider;
import com.snowcity.maid_construction_team.api.labor.LaborInfo;
import com.snowcity.maid_construction_team.api.labor.LaborStatus;
import com.snowcity.maid_construction_team.api.labor.PetTracker;
import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/**
 * 宠物劳动力提供者。
 * <p>
 * 从玩家附近的已驯服宠物中扫描劳动力信息。
 * 宠物通过 OwnerUUID 标签识别，首次扫描+事件驱动维护列表。
 */
public class PetLaborProvider implements ILaborProvider {

    @Override
    public String getProviderId() { return "pet"; }

    @Override
    public String getDisplayName() { return "驯服宠物"; }

    @Override
    public List<LaborInfo> scanLabor(Player player) {

        // 强制刷新宠物列表，以适应实体替换（如魂符存储）
        PetTracker.forceRefresh(player);

        List<LaborInfo> list = new ArrayList<>();

        // 从 DataAttachment 读取宠物列表
        List<UUID> petIds = PetTracker.getPets(player);
        ServerLevel level = (ServerLevel) player.level();

        for (UUID petId : petIds) {
            Entity entity = level.getEntity(petId);
            if (entity instanceof TamableAnimal tamable) {
                // 判断状态：是否被派遣到某个会话
                LaborStatus status = LaborStatus.IDLE;
                UUID workingSessionId = null;
                String workingSessionName = null;

                // 遍历玩家所有活跃会话，查找该宠物是否在参与者列表中
                PlacementSessionManager mgr = PlayerSessionManager.of(player);
                for (PlacementSession session : mgr.getActiveSessions()) {
                    if (session.getParticipantUuids().contains(petId)) {
                        status = LaborStatus.WORKING;
                        workingSessionId = session.getSessionId();
                        workingSessionName = session.getBlueprintName();
                        break;
                    }
                }

                list.add(new LaborInfo(
                        entity.getUUID(),
                        entity.getName().getString(),
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                        getProviderId(),
                        getDisplayName(),
                        status,
                        Optional.ofNullable(workingSessionId),
                        Optional.ofNullable(workingSessionName)
                ));
            } else if (entity == null) {
                // 实体不在加载范围内，标记为离线，但保留基本数据
                list.add(new LaborInfo(
                        petId,
                        "离线宠物", // 临时显示名，可后续从 DataAttachment 获取原名
                        ResourceLocation.parse("minecraft:pig"), // 占位类型
                        getProviderId(),
                        getDisplayName(),
                        LaborStatus.OFFLINE,
                        Optional.empty(),
                        Optional.empty()
                ));
            }
        }

        return list;
    }

    @Override
    public boolean dispatch(UUID laborId, PlacementSession session, Player player) {
        session.addParticipant(laborId);
        return true;
    }

    @Override
    public boolean recall(UUID laborId, Player player) {
        PlacementSessionManager mgr = PlayerSessionManager.of(player);
        for (PlacementSession session : mgr.getActiveSessions()) {
            if (session.getParticipantUuids().contains(laborId)) {
                session.removeParticipant(laborId);
            }
        }
        return true;
    }

    @Override
    public boolean isEnabled() { return true; }
}