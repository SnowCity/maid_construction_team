package com.snowcity.maid_construction_team.network.payload.labor;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import com.snowcity.maid_construction_team.api.labor.LaborInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/**
 * 服务端→客户端：返回玩家所有劳动力列表
 */
public record LaborListPayload(List<LaborInfo> laborInfos) implements CustomPacketPayload {

    public static final Type<LaborListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "labor_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LaborListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    LaborInfo.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()),
                    LaborListPayload::laborInfos,
                    LaborListPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}