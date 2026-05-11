package com.snowcity.maid_construction_team.api.labor;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.*;

/**
 * 玩家宠物 UUID 列表的数据组件。
 * 存储在该玩家的 DataAttachment 上，随玩家存档自动保存和加载。
 */
public record PetList(List<UUID> pets) {

    public static final PetList EMPTY = new PetList(List.of());

    public static final Codec<PetList> CODEC =
            Codec.STRING
                    .xmap(UUID::fromString, UUID::toString)
                    .listOf()
                    .xmap(PetList::new, PetList::pets);

    public static final StreamCodec<RegistryFriendlyByteBuf, PetList> STREAM_CODEC =
            StreamCodec.of(
                    (buf, petList) -> {
                        buf.writeVarInt(petList.pets().size());
                        for (UUID uuid : petList.pets()) {
                            buf.writeUUID(uuid);
                        }
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<UUID> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            list.add(buf.readUUID());
                        }
                        return new PetList(list);
                    }
            );
}