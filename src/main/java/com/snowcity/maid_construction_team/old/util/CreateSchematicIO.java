package com.snowcity.maid_construction_team.old.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Create模组兼容的蓝图读写器
 * 完全匹配Create蓝图桌的读取规范，路径、格式100%对齐
 */
public class CreateSchematicIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateSchematicIO.class);
    private static final String SCHEMATICS_FOLDER = "schematics";
    private static final int CREATE_SCHEMATIC_VERSION = 2;
    private static final int MINECRAFT_DATA_VERSION = 3955; // 1.21.1固定值

    // =========================================================================
    // 核心：保存Create兼容的蓝图（保存到世界存档内，和Create行为完全一致）
    // =========================================================================
    public static boolean saveCreateCompatibleSchematic(String schematicName, Level level, BlockPos posA, BlockPos posB, Player author) {
        LOGGER.info("========== 开始保存Create兼容蓝图 ==========");

        // 1. 计算正确的边界（最小/最大坐标）
        BlockPos minPos = new BlockPos(
                Math.min(posA.getX(), posB.getX()),
                Math.min(posA.getY(), posB.getY()),
                Math.min(posA.getZ(), posB.getZ())
        );
        BlockPos maxPos = new BlockPos(
                Math.max(posA.getX(), posB.getX()),
                Math.max(posA.getY(), posB.getY()),
                Math.max(posA.getZ(), posB.getZ())
        );

        // 尺寸：x宽 / y高 / z长，和Create完全对齐
        int width = maxPos.getX() - minPos.getX() + 1;
        int height = maxPos.getY() - minPos.getY() + 1;
        int length = maxPos.getZ() - minPos.getZ() + 1;

        LOGGER.info("蓝图边界: {} -> {}", minPos, maxPos);
        LOGGER.info("蓝图尺寸: {}x{}x{}", width, height, length);

        // 2. 初始化数据结构
        ListTag paletteList = new ListTag();
        Map<BlockState, Integer> stateToIndexMap = new HashMap<>();
        ListTag blocksList = new ListTag();
        Map<String, Integer> materialStats = new HashMap<>();

        int totalScannedBlocks = 0;
        int validBlocksSaved = 0;

        // 3. 遍历区域，收集方块数据（三重循环确保顺序正确）
        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    BlockPos worldPos = new BlockPos(x, y, z);
                    BlockState blockState = level.getBlockState(worldPos);
                    totalScannedBlocks++;

                    // 跳过空气方块
                    if (blockState.isAir()) continue;

                    // 计算相对坐标（Create要求从0开始的相对坐标）
                    int relX = x - minPos.getX();
                    int relY = y - minPos.getY();
                    int relZ = z - minPos.getZ();

                    // 处理方块调色板
                    int stateIndex;
                    if (stateToIndexMap.containsKey(blockState)) {
                        stateIndex = stateToIndexMap.get(blockState);
                    } else {
                        stateIndex = paletteList.size();
                        stateToIndexMap.put(blockState, stateIndex);
                        paletteList.add(serializeBlockState(blockState));
                    }

                    // 统计材料
                    String blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
                    materialStats.put(blockId, materialStats.getOrDefault(blockId, 0) + 1);

                    // 构建方块条目（完全匹配Create的格式）
                    CompoundTag blockEntry = new CompoundTag();
                    blockEntry.putIntArray("pos", new int[]{relX, relY, relZ});
                    blockEntry.putInt("state", stateIndex);

                    // 保存方块实体数据（箱子、Create仓库等）
                    BlockEntity blockEntity = level.getBlockEntity(worldPos);
                    if (blockEntity != null) {
                        CompoundTag beTag = blockEntity.saveWithFullMetadata(level.registryAccess());
                        // 移除世界坐标，Create只需要相对坐标
                        beTag.remove("x");
                        beTag.remove("y");
                        beTag.remove("z");
                        blockEntry.put("nbt", beTag);
                    }

                    blocksList.add(blockEntry);
                    validBlocksSaved++;
                }
            }
        }

        // 4. 校验数据
        if (validBlocksSaved == 0) {
            LOGGER.error("保存失败：蓝图区域内没有有效方块");
            return false;
        }

        LOGGER.info("扫描完成：总方块数={}, 有效方块数={}, 材料种类={}", totalScannedBlocks, validBlocksSaved, paletteList.size());
        LOGGER.info("材料统计: {}", materialStats);

        // 5. 构建Create标准NBT结构
        CompoundTag rootTag = new CompoundTag();

        // 核心必填标签（Create强制要求）
        ListTag sizeTag = new ListTag();
        sizeTag.add(IntTag.valueOf(width));
        sizeTag.add(IntTag.valueOf(height));
        sizeTag.add(IntTag.valueOf(length));
        rootTag.put("size", sizeTag);

        rootTag.put("entities", new ListTag()); // 空实体列表，必须存在
        rootTag.put("blocks", blocksList);
        rootTag.put("palette", paletteList);
        rootTag.putInt("DataVersion", MINECRAFT_DATA_VERSION);
        rootTag.putInt("Version", CREATE_SCHEMATIC_VERSION); // Create蓝图版本号

        // 可选元数据（Create蓝图桌会显示）
        rootTag.putString("Name", schematicName);
        if (author != null) {
            rootTag.putString("Author", author.getName().getString());
        }

        // 6. 获取保存路径（世界存档内的schematics文件夹，Create默认读取位置）
        Path saveDir = getWorldSchematicsPath(level);
        if (saveDir == null) {
            LOGGER.error("保存失败：无法获取世界存档路径");
            return false;
        }

        // 7. 写入文件
        String safeFileName = schematicName.endsWith(".nbt") ? schematicName : schematicName + ".nbt";
        Path schematicFile = saveDir.resolve(safeFileName);

        try {
            Files.createDirectories(saveDir);
            NbtIo.writeCompressed(rootTag, schematicFile);
            LOGGER.info("蓝图保存成功！路径: {}", schematicFile.toAbsolutePath());
            LOGGER.info("========== 保存完成 ==========");

            // 同步更新材料缓存给GUI用
            AreaSchematicCapturer.setRawMaterialCache(materialStats);
            return true;
        } catch (IOException e) {
            LOGGER.error("蓝图保存失败：IO异常", e);
            return false;
        }
    }

    // =========================================================================
    // 辅助工具
    // =========================================================================

    /**
     * 获取当前世界存档的schematics文件夹路径（和Create完全一致）
     */
    private static Path getWorldSchematicsPath(Level level) {
        if (level.getServer() == null) return null;
        // 世界存档根路径
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve(SCHEMATICS_FOLDER);
    }

    /**
     * 序列化方块状态（完全匹配Minecraft/Create的格式）
     */
    private static CompoundTag serializeBlockState(BlockState state) {
        CompoundTag stateTag = new CompoundTag();
        stateTag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());

        // 写入方块属性（如朝向、开关状态、Create的large/axis等）
        if (!state.getValues().isEmpty()) {
            CompoundTag propertiesTag = new CompoundTag();
            state.getValues().forEach((property, value) -> {
                propertiesTag.putString(property.getName(), value.toString());
            });
            stateTag.put("Properties", propertiesTag);
        }

        return stateTag;
    }

    /**
     * 调试用：读取蓝图并打印信息
     */
    public static void debugLoadSchematic(String schematicName, Level level) {
        LOGGER.info("========== 开始调试读取蓝图 ==========");
        Path saveDir = getWorldSchematicsPath(level);
        if (saveDir == null) {
            LOGGER.error("读取失败：无法获取世界存档路径");
            return;
        }

        String safeFileName = schematicName.endsWith(".nbt") ? schematicName : schematicName + ".nbt";
        Path schematicFile = saveDir.resolve(safeFileName);

        if (!Files.exists(schematicFile)) {
            LOGGER.error("读取失败：文件不存在 {}", schematicFile);
            return;
        }

        try {
            CompoundTag rootTag = NbtIo.readCompressed(schematicFile, NbtAccounter.unlimitedHeap());

            ListTag sizeTag = rootTag.getList("size", net.minecraft.nbt.Tag.TAG_INT);
            int width = sizeTag.getInt(0);
            int height = sizeTag.getInt(1);
            int length = sizeTag.getInt(2);

            ListTag blocksTag = rootTag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
            ListTag paletteTag = rootTag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);

            LOGGER.info("读取成功！尺寸: {}x{}x{}", width, height, length);
            LOGGER.info("方块数: {}, 调色板大小: {}", blocksTag.size(), paletteTag.size());
            LOGGER.info("========== 读取完成 ==========");

        } catch (IOException e) {
            LOGGER.error("读取失败：IO异常", e);
        }
    }
}