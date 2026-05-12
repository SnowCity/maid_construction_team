package com.snowcity.maid_construction_team.component;

import com.mojang.serialization.Codec;
import com.snowcity.maid_construction_team.compat.schematic.CreateSchematicReader;
import com.snowcity.maid_construction_team.core.schematic.SchematicData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 蓝图纸数据组件值，内部存储原始蓝图 NBT 以及蓝图文件名。
 * @param data CompoundTag 类型
 * @param fileName String 类型
 */
public record BlueprintData(CompoundTag data, String fileName) {

    public static final Codec<BlueprintData> CODEC = CompoundTag.CODEC.xmap(
            tag -> new BlueprintData(tag, tag.getString("FileName")),
            bd -> {
                CompoundTag copy = bd.data.copy();
                copy.putString("FileName", bd.fileName);
                return copy;
            }
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintData> STREAM_CODEC =
            StreamCodec.of(
                    (buf, bd) -> {
                        ByteBufCodecs.COMPOUND_TAG.encode(buf, bd.data);
                        buf.writeUtf(bd.fileName);
                    },
                    buf -> {
                        CompoundTag tag = ByteBufCodecs.COMPOUND_TAG.decode(buf);
                        String name = buf.readUtf();
                        return BlueprintData.fromNbt(tag, name);
                    }
            );

    /** 从原始 NBT 和文件名构造，自动嵌入文件名到 NBT 中 */
    public BlueprintData(CompoundTag data, String fileName) {
        this.data = data.copy();
        this.fileName = fileName;
        this.data.putString("FileName", fileName);
    }

    /** 用于反序列化，从已有 NBT 中读取文件名 */
    public static BlueprintData fromNbt(CompoundTag tag, String fileName) {
        if (fileName.isEmpty()) fileName = "未命名蓝图";
        // 不再从 tag 中读取 FileName，避免覆盖
        return new BlueprintData(tag, fileName);
    }

    /** 解析蓝图数据 */
    public SchematicData toSchematicData() {
        return CreateSchematicReader.parseFromNBT(data);
    }
}