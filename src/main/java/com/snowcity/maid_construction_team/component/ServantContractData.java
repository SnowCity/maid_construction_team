package com.snowcity.maid_construction_team.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;

public record ServantContractData(
        UUID contractId,
        ResourceLocation entityType,
        String servantName,
        ResourceLocation modelVariant,
        Optional<UUID> dispatchedSessionId
) {
    public static final Codec<ServantContractData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("contract_id").forGetter(ServantContractData::contractId),
                    ResourceLocation.CODEC.fieldOf("entity_type").forGetter(ServantContractData::entityType),
                    Codec.STRING.fieldOf("slave_name").forGetter(ServantContractData::servantName),
                    ResourceLocation.CODEC.fieldOf("model_variant").forGetter(ServantContractData::modelVariant),
                    Codec.STRING.xmap(UUID::fromString, UUID::toString).optionalFieldOf("dispatched_session_id").forGetter(ServantContractData::dispatchedSessionId)
            ).apply(instance, ServantContractData::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServantContractData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(Codec.STRING.xmap(UUID::fromString, UUID::toString)),
                    ServantContractData::contractId,
                    ResourceLocation.STREAM_CODEC,
                    ServantContractData::entityType,
                    ByteBufCodecs.STRING_UTF8,
                    ServantContractData::servantName,
                    ResourceLocation.STREAM_CODEC,
                    ServantContractData::modelVariant,
                    ByteBufCodecs.optional(ByteBufCodecs.fromCodec(Codec.STRING.xmap(UUID::fromString, UUID::toString))),
                    ServantContractData::dispatchedSessionId,
                    ServantContractData::new
            );
}