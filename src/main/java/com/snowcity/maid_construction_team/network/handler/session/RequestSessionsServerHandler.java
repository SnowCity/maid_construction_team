package com.snowcity.maid_construction_team.network.handler.session;

import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import com.snowcity.maid_construction_team.core.schematic.*;
import com.snowcity.maid_construction_team.core.schematic.provider.ContainerMarkMaterialProvider;
import com.snowcity.maid_construction_team.item.MaidConstructionTeamItems;
import com.snowcity.maid_construction_team.network.payload.session.RequestSessionsPayload;
import com.snowcity.maid_construction_team.network.payload.session.SessionsResponsePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 处理客户端请求会话数据的服务端处理器。
 */
public class RequestSessionsServerHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestSessionsServerHandler.class);

    public static void handle(final RequestSessionsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            PlacementSessionManager mgr = PlayerSessionManager.of(player);

            if (payload.sessionId().isPresent()) {
                UUID sid = payload.sessionId().get();
                PlacementSession session = mgr.getSession(sid);
                if (session == null) {
                    PacketDistributor.sendToPlayer(player,
                            new SessionsResponsePayload(List.of(), Optional.empty()));
                    return;
                }
                SessionsResponsePayload.SessionDetail detail = buildSessionDetail(session, player);
                PacketDistributor.sendToPlayer(player,
                        new SessionsResponsePayload(List.of(), Optional.of(detail)));
                LOGGER.info("[Sessions] Sent detail for session {}", sid);
            } else {
                Collection<PlacementSession> activeSessions = mgr.getActiveSessions();
                List<SessionsResponsePayload.SessionSummary> summaries = new ArrayList<>();
                for (PlacementSession s : activeSessions) {
                    summaries.add(new SessionsResponsePayload.SessionSummary(
                            s.getSessionId(),
                            s.getBlueprintName(),
                            s.getState(),
                            s.getParticipantUuids().size(),
                            s.getProgress().getCurrentBlockIndex(),
                            s.getProgress().getTotalBlocks()
                    ));
                }
                PacketDistributor.sendToPlayer(player,
                        new SessionsResponsePayload(summaries, Optional.empty()));
                LOGGER.info("[Sessions] Sent summary list with {} active sessions", summaries.size());
            }
        });
    }

    private static SessionsResponsePayload.SessionDetail buildSessionDetail(
            PlacementSession session, ServerPlayer player) {

        PlacementProgress progress = session.getProgress();
        ProgressivePlacer placer = session.getPlacer();
        SchematicData schematic = placer != null ? placer.getSchematicData() : null;

        String blueprintName = session.getBlueprintName();
        int totalBlocks = progress.getTotalBlocks();
        int currentBlocks = progress.getCurrentBlockIndex();
        int totalEntities = progress.getTotalEntities();
        int placedEntities = progress.getPlacedEntityCount();

        // 材料清单
        List<SessionsResponsePayload.SessionDetail.MaterialItemInfo> materials = new ArrayList<>();
        if (schematic != null) {
            Map<Block, Integer> totalRequired = new HashMap<>();
            for (BlockInfo info : schematic.getBlocks()) {
                Block block = info.getState().getBlock();
                totalRequired.merge(block, 1, Integer::sum);
            }

            Map<Block, Integer> consumed = progress.getMaterialConsumed();
            ContainerMarkMaterialProvider provider =
                    new ContainerMarkMaterialProvider(player, session.getParticipantUuids());
            boolean hasRing = hasMoriyaIronRing(player);
            boolean includePlayer = MaidConstructionTeamConfig.getInstance().isIncludePlayerInventory();

            for (Map.Entry<Block, Integer> entry : totalRequired.entrySet()) {
                Block block = entry.getKey();
                int required = entry.getValue();
                int cons = consumed.getOrDefault(block, 0);
                BlockState state = block.defaultBlockState();
                int stock = provider.getStock(state);

                String status;
                if (hasRing) {
                    status = "∞ 无限";
                } else if (cons >= required) {
                    status = "✔ 已完成";
                } else if (stock + cons >= required) {
                    status = "✔ 充足";
                } else {
                    int lack = required - cons - stock;
                    status = "✘ 缺少 " + lack;
                }
                materials.add(new SessionsResponsePayload.SessionDetail.MaterialItemInfo(
                        Block.getId(state), required, cons, stock, status));
            }
        }

        // 参与者（仅劳动力实体）
        List<SessionsResponsePayload.SessionDetail.ParticipantInfo> participants = new ArrayList<>();
        for (UUID uuid : session.getParticipantUuids()) {
            String name = uuid.equals(player.getUUID())
                    ? player.getName().getString()
                    : uuid.toString().substring(0, 8);
            participants.add(new SessionsResponsePayload.SessionDetail.ParticipantInfo(uuid, name));
        }

        // 缺失方块列表
        List<BlockPos> missedBlocks = progress.getMissedBlocks();
        MaterialShortageStrategy strategy = session.getShortageStrategy();

        return new SessionsResponsePayload.SessionDetail(
                session.getSessionId(),
                session.getState(),
                blueprintName,
                totalBlocks, currentBlocks,
                totalEntities, placedEntities,
                materials,
                participants,
                missedBlocks,
                strategy
        );
    }

    private static boolean hasMoriyaIronRing(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(MaidConstructionTeamItems.MORIYA_IRON_RING.get())) return true;
        }
        return false;
    }
}