package com.snowcity.maid_construction_team.component;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 物资点登记表数据组件。
 * 内部包裹一个 CompoundTag，利用原版序列化机制进行持久化与网络同步。
 */
public record MaterialChecklistData(CompoundTag data) {

    /** 原版 CompoundTag 的 Codec 映射 */
    public static final Codec<MaterialChecklistData> CODEC =
            CompoundTag.CODEC.xmap(MaterialChecklistData::new, MaterialChecklistData::data);

    public static final String TAG_NAME = "name";

    /**
     * 利用 ByteBufCodecs.COMPOUND_TAG 适配的 StreamCodec。
     * 由于泛型差异，手动构建一个适配器，内部委托给原版编解码器。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialChecklistData> STREAM_CODEC =
            StreamCodec.of(
                    (buf, value) -> ByteBufCodecs.COMPOUND_TAG.encode(buf, value.data()),
                    buf -> new MaterialChecklistData(ByteBufCodecs.COMPOUND_TAG.decode(buf))
            );

    // NBT 内部键名常量
    public static final String TAG_MATERIAL_CONTAINERS = "MaterialContainers";
    public static final String TAG_DIMENSION = "dimension";
    public static final String TAG_X = "x";
    public static final String TAG_Y = "y";
    public static final String TAG_Z = "z";
    public static final String TAG_DATA_VERSION = "DataVersion";
    public static final int DATA_VERSION = 1;

    /** 确保数据中存在版本标记 */
    public CompoundTag ensureDataVersion() {
        if (!data.contains(TAG_DATA_VERSION, Tag.TAG_INT)) {
            data.putInt(TAG_DATA_VERSION, DATA_VERSION);
        }
        return data;
    }

    /** 获取存储的容器坐标列表 */
    public ListTag getContainerList() {
        if (data.contains(TAG_MATERIAL_CONTAINERS, Tag.TAG_LIST)) {
            return data.getList(TAG_MATERIAL_CONTAINERS, Tag.TAG_COMPOUND);
        }
        return new ListTag();
    }

    /** 返回一个新的实例，内部 NBT 包含新的容器列表 */
    public MaterialChecklistData withContainerList(ListTag newList) {
        CompoundTag copy = data.copy();
        copy.put(TAG_MATERIAL_CONTAINERS, newList);
        return new MaterialChecklistData(copy);
    }

    /** 从条目 CompoundTag 中获取自定义名称，若没有则返回 null */
    public static String getDisplayName(CompoundTag entry) {
        return entry.contains(TAG_NAME, Tag.TAG_STRING) ? entry.getString(TAG_NAME) : null;
    }

    /** 设置条目的自定义名称 */
    public static void setDisplayName(CompoundTag entry, String name) {
        entry.putString(TAG_NAME, name);
    }
}