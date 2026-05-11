package com.snowcity.maid_construction_team.api.labor;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;

/**
 * 统一的劳动力信息记录。
 * 花名册仅依赖此记录，不关心劳动力来源。
 *
 * @param laborId            劳动力唯一标识（仆从 contractId 或宠物 UUID）
 * @param displayName        显示名称（如 "α"、"Zom-001"、宠物名）
 * @param entityType         生物注册名，用于获取图标
 * @param sourceType         来源标识，与 {@link ILaborProvider#getProviderId()} 一致
 * @param sourceDisplayName  来源显示名称（如 "契约"、"宠物"）
 * @param status              当前状态
 * @param workingSessionId    派遣中的会话ID（若空闲则为空）
 * @param workingSessionName  派遣中的会话名称（若空闲则为空）
 */
public record LaborInfo(
        UUID laborId,
        String displayName,
        ResourceLocation entityType,
        String sourceType,
        String sourceDisplayName,
        LaborStatus status,
        Optional<UUID> workingSessionId,
        Optional<String> workingSessionName
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, LaborInfo> STREAM_CODEC =
            StreamCodec.of(
                    (buf, info) -> {
                        ByteBufCodecs.fromCodec(UUIDUtil.CODEC).encode(buf, info.laborId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, info.displayName());
                        ResourceLocation.STREAM_CODEC.encode(buf, info.entityType());
                        ByteBufCodecs.STRING_UTF8.encode(buf, info.sourceType());
                        ByteBufCodecs.STRING_UTF8.encode(buf, info.sourceDisplayName());
                        buf.writeEnum(info.status());
                        ByteBufCodecs.optional(ByteBufCodecs.fromCodec(UUIDUtil.CODEC)).encode(buf, info.workingSessionId());
                        ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, info.workingSessionName());
                    },
                    buf -> new LaborInfo(
                            ByteBufCodecs.fromCodec(UUIDUtil.CODEC).decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ResourceLocation.STREAM_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            buf.readEnum(LaborStatus.class),
                            ByteBufCodecs.optional(ByteBufCodecs.fromCodec(UUIDUtil.CODEC)).decode(buf),
                            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf)
                    )
            );
}