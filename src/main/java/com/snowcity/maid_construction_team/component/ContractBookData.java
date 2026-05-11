package com.snowcity.maid_construction_team.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 契约之书的数据组件。
 * 内部存储所有已签订契约的快照列表。
 */
public record ContractBookData(List<ContractEntry> entries) {

    /**
     * 单条契约条目，包含恢复契约物品所需的所有信息。
     */
    public record ContractEntry(
            UUID contractId,
            ResourceLocation entityType,
            String servantName,
            ResourceLocation modelVariant,
            Optional<UUID> dispatchedSessionId
    ) {
        public static final Codec<ContractEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("contract_id").forGetter(ContractEntry::contractId),
                        ResourceLocation.CODEC.fieldOf("entity_type").forGetter(ContractEntry::entityType),
                        Codec.STRING.fieldOf("servant_name").forGetter(ContractEntry::servantName),
                        ResourceLocation.CODEC.fieldOf("model_variant").forGetter(ContractEntry::modelVariant),
                        Codec.STRING.xmap(UUID::fromString, UUID::toString).optionalFieldOf("dispatched_session_id").forGetter(ContractEntry::dispatchedSessionId)
                ).apply(instance, ContractEntry::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, ContractEntry> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.fromCodec(Codec.STRING.xmap(UUID::fromString, UUID::toString)),
                        ContractEntry::contractId,
                        ResourceLocation.STREAM_CODEC,
                        ContractEntry::entityType,
                        ByteBufCodecs.STRING_UTF8,
                        ContractEntry::servantName,
                        ResourceLocation.STREAM_CODEC,
                        ContractEntry::modelVariant,
                        ByteBufCodecs.optional(ByteBufCodecs.fromCodec(Codec.STRING.xmap(UUID::fromString, UUID::toString))),
                        ContractEntry::dispatchedSessionId,
                        ContractEntry::new
                );
    }

    public static final Codec<ContractBookData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ContractEntry.CODEC.listOf().fieldOf("entries").forGetter(ContractBookData::entries)
            ).apply(instance, ContractBookData::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ContractBookData> STREAM_CODEC =
            StreamCodec.composite(
                    ContractEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    ContractBookData::entries,
                    ContractBookData::new
            );

    /** 空书 */
    public static final ContractBookData EMPTY = new ContractBookData(List.of());
}