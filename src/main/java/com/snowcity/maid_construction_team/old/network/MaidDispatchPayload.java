package com.snowcity.maid_construction_team.old.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.snowcity.maid_construction_team.MaidConstructionTeam;
import com.snowcity.maid_construction_team.old.manager.MaidBuildManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record MaidDispatchPayload(UUID sessionId, UUID playerUuid, UUID maidUuid, boolean isDispatched) implements CustomPacketPayload {

    // 1. 定义 ID
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "maid_dispatch");

    // 2. 【关键修正】定义 Type 实例
    public static final Type<MaidDispatchPayload> TYPE = new Type<>(ID);

    // 3. 定义 StreamCodec
    public static final StreamCodec<FriendlyByteBuf, MaidDispatchPayload> CODEC = CustomPacketPayload.codec(
            MaidDispatchPayload::write,
            MaidDispatchPayload::new
    );

    // 反序列化构造函数
    public MaidDispatchPayload(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUUID(), buf.readUUID(), buf.readBoolean());
    }

    // 序列化方法
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.sessionId);   // 新增：先写 sessionId
        buf.writeUUID(this.playerUuid);
        buf.writeUUID(this.maidUuid);
        buf.writeBoolean(this.isDispatched);
    }

    // ============================ 4. 【核心修正】重写 type()，删除 id() ============================
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 服务端处理逻辑（保持不变）
    public static void handle(final MaidDispatchPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = (ServerPlayer) context.player();
            if (serverPlayer == null) return;

            if (!serverPlayer.getUUID().equals(payload.playerUuid)) return;

            var entity = serverPlayer.serverLevel().getEntity(payload.maidUuid);
            // 注意：这里的 EntityMaid 需要替换为你实际 import 的女仆类
            if (!(entity instanceof EntityMaid maid)) return;

            if (!maid.getOwnerUUID().equals(serverPlayer.getUUID())) return;

            if (payload.isDispatched) {
                MaidConstructionTeam.LOGGER.info("服务端：派遣女仆 {}", payload.maidUuid);
                MaidBuildManager.addMaidToSession(payload.sessionId, maid, serverPlayer);
            } else {
                MaidConstructionTeam.LOGGER.info("服务端：召回女仆 {}", payload.maidUuid);
                MaidBuildManager.removeMaidToSession(payload.sessionId, maid, serverPlayer);
            }
        });
    }
}