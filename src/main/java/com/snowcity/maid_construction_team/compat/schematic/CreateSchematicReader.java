package com.snowcity.maid_construction_team.compat.schematic;

import com.snowcity.maid_construction_team.core.schematic.*;
import com.snowcity.maid_construction_team.core.schematic.persistence.SimpleBlueprintPersistence;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CreateSchematicReader implements ISchematicReader {

    // 日志记录器，方便调试
    private static final Logger LOGGER = LogManager.getLogger();
    // 代表“无方块实体”的常量，提高代码可读性
    private static final CompoundTag NO_BLOCK_ENTITY = null;
    // 默认的数据版本号，当原文件没有提供时使用
    private static final int DEFAULT_DATA_VERSION = -1;

    private final SimpleBlueprintPersistence persistence = new SimpleBlueprintPersistence();

    @Override
    public String getFormatName() {
        return "Create Schematic .nbt";
    }

    @Override
    public SchematicData read(Path filePath) throws IOException {
        // 1. 读取压缩的NBT文件
        CompoundTag root = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());

        // 2. 提取基础信息
        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        if (sizeTag.size() < 3) {
            throw new IOException("Invalid schematic: missing or incomplete 'size' tag");
        }
        int sizeX = sizeTag.getInt(0);
        int sizeY = sizeTag.getInt(1);
        int sizeZ = sizeTag.getInt(2);

        // 3. 初始化构建器
        SchematicData.Builder builder = new SchematicData.Builder()
                .setSize(sizeX, sizeY, sizeZ).originalNbt(root);

        // 4. 解析调色板
        List<BlockState> palette = parsePalette(root.getList("palette", Tag.TAG_COMPOUND));

        // 5. 解析并添加方块
        parseAndAddBlocks(root.getList("blocks", Tag.TAG_COMPOUND), palette, builder);

        // 6. 解析并添加实体
        parseAndAddEntities(root.getList("entities", Tag.TAG_COMPOUND), builder);

        // 7. 构建最终对象
        return builder.build();
    }

    @Override
    public IBlueprintPersistence getPersistence() {
        return persistence;
    }

    /**
     * 将调色板列表转换为 BlockState 列表。
     * @return BlockState对象集合
     */
    public static List<BlockState> parsePalette(ListTag paletteTag) {
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (Tag tag : paletteTag) {
            if (tag instanceof CompoundTag compound) {
                BlockState.CODEC.parse(NbtOps.INSTANCE, compound)
                        .resultOrPartial(error -> LOGGER.error("Failed to parse BlockState in palette: {}", error))
                        .ifPresent(palette::add);
            }
        }
        return palette;
    }
    // 遍历 blocks 列表，提取每个方块的信息并添加到 Builder。
    public static void parseAndAddBlocks(ListTag blocksTag, List<BlockState> palette, SchematicData.Builder builder) {
        for (Tag tag : blocksTag) {
            if (!(tag instanceof CompoundTag blockTag)) {
                continue;
            }

            // 获取位置
            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() < 3) {
                LOGGER.warn("Block entry missing 'pos' tag, skipping");
                continue;
            }
            BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));

            // 获取调色板索引
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                LOGGER.warn("Invalid palette index {} at position {}, skipping", stateIndex, pos);
                continue;
            }
            BlockState state = palette.get(stateIndex);

            // 获取可选的方块实体数据
            CompoundTag blockEntityData = blockTag.contains("nbt", Tag.TAG_COMPOUND)
                    ? blockTag.getCompound("nbt")
                    : NO_BLOCK_ENTITY;

            // 添加到 Builder
            BlockInfo blockInfo = new BlockInfo(pos, state, blockEntityData);
            builder.addBlock(blockInfo);
        }
    }

    /**
     * 遍历实体列表，提取位置和完整 NBT，并添加到 Builder。
     */
    public static void parseAndAddEntities(ListTag entitiesTag, SchematicData.Builder builder) {
        for (Tag tag : entitiesTag) {
            if (!(tag instanceof CompoundTag entityData)) {
                LOGGER.warn("Non-compound tag in entities list, skipping.");
                continue;
            }

            Vec3 pos = extractEntityPos(entityData);
            if (pos == null) {
                LOGGER.warn("Entity missing position data, skipping: {}", entityData);
                continue;
            }

            EntityInfo entityInfo = new EntityInfo(pos, entityData);
            builder.addEntity(entityInfo);
        }
    }

    /**
     * 从实体 NBT 中提取位置向量。
     * 优先使用原版 "Pos" 列表，若不存在则尝试独立的 x/y/z 标签。
     */
    private static Vec3 extractEntityPos(CompoundTag entityData) {
        // 标准格式：Pos = [dX, dY, dZ]
        if (entityData.contains("Pos", Tag.TAG_LIST)) {
            ListTag posTag = entityData.getList("Pos", Tag.TAG_DOUBLE);
            if (posTag.size() >= 3) {
                return new Vec3(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));
            }
        }
        // 备用格式：独立的 x, y, z 双精度标签
        if (entityData.contains("x") && entityData.contains("y") && entityData.contains("z")) {
            return new Vec3(entityData.getDouble("x"), entityData.getDouble("y"), entityData.getDouble("z"));
        }
        return null;
    }

    /**
     * 从原始 NBT 直接解析 SchematicData，无需文件路径。
     * 适用于从蓝图纸物品中读取 NBT 后重建。
     */
    public static SchematicData parseFromNBT(CompoundTag root) {
        // 校验必要条件
        // 分别检查每个必要字段，提供具体的错误描述
        if (!root.contains("size", 9)) {
            throw new IllegalArgumentException("Invalid blueprint NBT: missing field 'size'");
        }
        if (!root.contains("palette", 9)) {
            throw new IllegalArgumentException("Invalid blueprint NBT: missing field 'palette'");
        }
        if (!root.contains("blocks", 9)) {
            throw new IllegalArgumentException("Invalid blueprint NBT: missing field 'blocks'");
        }

        // 提取尺寸
        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        int sizeX = sizeTag.getInt(0);
        int sizeY = sizeTag.getInt(1);
        int sizeZ = sizeTag.getInt(2);

        SchematicData.Builder builder = new SchematicData.Builder().setSize(sizeX, sizeY, sizeZ)
                .originalNbt(root);  // 保存原始 NBT;

        // 解析调色板
        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = parsePalette(paletteTag); // 复用已有的私有方法

        // 解析方块
        ListTag blocksTag = root.getList("blocks", Tag.TAG_COMPOUND);
        parseAndAddBlocks(blocksTag, palette, builder); // 复用

        // 解析实体
        ListTag entitiesTag = root.getList("entities", Tag.TAG_COMPOUND);
        parseAndAddEntities(entitiesTag, builder); // 复用

        return builder.build();
    }
}
