package com.snowcity.maid_construction_team.network.payload.session;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import com.snowcity.maid_construction_team.core.manager.SessionStateMachine;
import com.snowcity.maid_construction_team.core.schematic.MaterialShortageStrategy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 服务端返回会话数据的统一响应包。
 * <p>
 * 根据请求类型（摘要列表或单个详情），服务端会填充不同字段：
 * <ul>
 *   <li>请求摘要列表时：填充 {@link #sessions}，{@link #detail} 为空。</li>
 *   <li>请求单个会话详情时：填充 {@link #detail}，{@link #sessions} 可为空。</li>
 * </ul>
 */
public record SessionsResponsePayload(
        List<SessionSummary> sessions,
        Optional<SessionDetail> detail
) implements CustomPacketPayload {

    public static final Type<SessionsResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "sessions_response"));

    // ==================== 顶层编解码（手动实现） ====================

    public static final StreamCodec<RegistryFriendlyByteBuf, SessionsResponsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        // 1. 编码会话摘要列表
                        buf.writeVarInt(payload.sessions().size());
                        for (SessionSummary summary : payload.sessions()) {
                            SessionSummary.STREAM_CODEC.encode(buf, summary);
                        }
                        // 2. 编码可选详情
                        buf.writeBoolean(payload.detail().isPresent());
                        if (payload.detail().isPresent()) {
                            SessionDetail.STREAM_CODEC.encode(buf, payload.detail().get());
                        }
                    },
                    buf -> {
                        // 1. 解码会话摘要列表
                        int size = buf.readVarInt();
                        List<SessionSummary> sessions = new ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            sessions.add(SessionSummary.STREAM_CODEC.decode(buf));
                        }
                        // 2. 解码可选详情
                        boolean hasDetail = buf.readBoolean();
                        Optional<SessionDetail> detail = hasDetail
                                ? Optional.of(SessionDetail.STREAM_CODEC.decode(buf))
                                : Optional.empty();
                        return new SessionsResponsePayload(sessions, detail);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ==================== 会话摘要 ====================

    /**
     * 单个会话的摘要信息，用于主界面列表展示。
     */
    public record SessionSummary(
            UUID sessionId,
            String blueprintName,
            SessionStateMachine.State state,
            int participantCount,
            int currentBlocks,
            int totalBlocks
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SessionSummary> STREAM_CODEC =
                StreamCodec.of(
                        (buf, s) -> {
                            buf.writeUUID(s.sessionId());
                            buf.writeUtf(s.blueprintName());
                            buf.writeEnum(s.state());
                            buf.writeVarInt(s.participantCount());
                            buf.writeVarInt(s.currentBlocks());
                            buf.writeVarInt(s.totalBlocks());
                        },
                        buf -> new SessionSummary(
                                buf.readUUID(),
                                buf.readUtf(),
                                buf.readEnum(SessionStateMachine.State.class),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readVarInt()
                        )
                );
    }

    // ==================== 会话详情 ====================

    /**
     * 单个会话的详细信息，用于详情界面展示。
     * 包含蓝图基本信息、材料清单、缺失方块列表、参与者列表以及当前材料不足策略。
     */
    public record SessionDetail(
            UUID sessionId,
            SessionStateMachine.State state,
            String blueprintName,
            int totalBlocks,
            int currentBlocks,
            int totalEntities,
            int placedEntities,
            List<MaterialItemInfo> materials,
            List<ParticipantInfo> participants,
            List<BlockPos> missedBlocks,
            MaterialShortageStrategy strategy
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SessionDetail> STREAM_CODEC =
                StreamCodec.of(
                        (buf, d) -> {
                            // 基本信息
                            buf.writeUUID(d.sessionId());
                            buf.writeEnum(d.state());
                            buf.writeUtf(d.blueprintName());
                            buf.writeVarInt(d.totalBlocks());
                            buf.writeVarInt(d.currentBlocks());
                            buf.writeVarInt(d.totalEntities());
                            buf.writeVarInt(d.placedEntities());

                            // 材料清单列表
                            buf.writeVarInt(d.materials().size());
                            for (MaterialItemInfo mat : d.materials()) {
                                MaterialItemInfo.STREAM_CODEC.encode(buf, mat);
                            }

                            // 参与者列表
                            buf.writeVarInt(d.participants().size());
                            for (ParticipantInfo p : d.participants()) {
                                ParticipantInfo.STREAM_CODEC.encode(buf, p);
                            }

                            // 缺失方块列表
                            buf.writeVarInt(d.missedBlocks().size());
                            for (BlockPos pos : d.missedBlocks()) {
                                buf.writeBlockPos(pos);
                            }

                            // 材料不足策略
                            buf.writeEnum(d.strategy());
                        },
                        buf -> {
                            UUID id = buf.readUUID();
                            SessionStateMachine.State state = buf.readEnum(SessionStateMachine.State.class);
                            String name = buf.readUtf();
                            int totalB = buf.readVarInt();
                            int currB = buf.readVarInt();
                            int totalE = buf.readVarInt();
                            int placedE = buf.readVarInt();

                            int matCount = buf.readVarInt();
                            List<MaterialItemInfo> materials = new ArrayList<>();
                            for (int i = 0; i < matCount; i++) {
                                materials.add(MaterialItemInfo.STREAM_CODEC.decode(buf));
                            }

                            int partCount = buf.readVarInt();
                            List<ParticipantInfo> participants = new ArrayList<>();
                            for (int i = 0; i < partCount; i++) {
                                participants.add(ParticipantInfo.STREAM_CODEC.decode(buf));
                            }

                            int missedCount = buf.readVarInt();
                            List<BlockPos> missed = new ArrayList<>();
                            for (int i = 0; i < missedCount; i++) {
                                missed.add(buf.readBlockPos());
                            }

                            MaterialShortageStrategy strategy = buf.readEnum(MaterialShortageStrategy.class);

                            return new SessionDetail(id, state, name, totalB, currB, totalE, placedE,
                                    materials, participants, missed, strategy);
                        }
                );

        /**
         * 材料清单中的单条轻量信息。
         * 服务端传输时只包含数值和状态字符串，客户端根据 blockStateId 自行查找图标和显示名。
         */
        public record MaterialItemInfo(int blockStateId, int totalRequired, int consumed, int inStock, String status) {
            public static final StreamCodec<RegistryFriendlyByteBuf, MaterialItemInfo> STREAM_CODEC =
                    StreamCodec.of(
                            (buf, m) -> {
                                buf.writeVarInt(m.blockStateId());
                                buf.writeVarInt(m.totalRequired());
                                buf.writeVarInt(m.consumed());
                                buf.writeVarInt(m.inStock());
                                buf.writeUtf(m.status());
                            },
                            buf -> new MaterialItemInfo(
                                    buf.readVarInt(),
                                    buf.readVarInt(),
                                    buf.readVarInt(),
                                    buf.readVarInt(),
                                    buf.readUtf()
                            )
                    );
        }

        /**
         * 参与者信息。
         */
        public record ParticipantInfo(UUID uuid, String displayName) {
            public static final StreamCodec<RegistryFriendlyByteBuf, ParticipantInfo> STREAM_CODEC =
                    StreamCodec.of(
                            (buf, p) -> {
                                buf.writeUUID(p.uuid());
                                buf.writeUtf(p.displayName());
                            },
                            buf -> new ParticipantInfo(
                                    buf.readUUID(),
                                    buf.readUtf()
                            )
                    );
        }
    }
}