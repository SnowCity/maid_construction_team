package com.snowcity.maid_construction_team.old.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Create兼容的蓝图保存器
 */
public class SchematicPersistence {

    /**
     * 保存Create兼容的蓝图到世界存档的schematics文件夹
     */
    public static boolean saveCreateCompatibleSchematic(String fileName, SchematicLoader.SchematicData data, Level level) {
        if (data == null || data.isEmpty()) {
            System.err.println("[保存器] 错误：蓝图数据为空");
            return false;
        }

        // 1. 处理文件名
        String safeFileName = fileName.endsWith(".nbt") ? fileName : fileName + ".nbt";

        // 2. 【关键】获取Create默认读取的路径：当前世界存档的schematics文件夹
        Path saveDir = FMLPaths.getOrCreateGameRelativePath(FMLPaths.GAMEDIR.get().resolve("schematics"));

        Path filePath = saveDir.resolve(safeFileName);

        try {
            // 3. 创建目录
            Files.createDirectories(saveDir);

            // 4. 获取严格对齐Create的NBT
            CompoundTag nbtData = data.writeToNbt();

            // 5. GZIP压缩写入（和Create完全一致）
            NbtIo.writeCompressed(nbtData, filePath);

            System.out.println("[保存器] 成功保存到: " + filePath.toAbsolutePath());
            System.out.println("[保存器] 方块数: " + data.getBlockCount());

            return true;

        } catch (IOException e) {
            System.err.println("[保存器] 保存失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 读取蓝图
     */
    public static SchematicLoader.SchematicData loadSchematic(String fileName) {
        String safeFileName = fileName.endsWith(".nbt") ? fileName : fileName + ".nbt";
        Path filePath = FMLPaths.getOrCreateGameRelativePath(
                FMLPaths.GAMEDIR.get().resolve("schematics")
        ).resolve(safeFileName);

        if (!Files.exists(filePath)) {
            System.err.println("[保存器] 文件不存在: " + filePath);
            return null;
        }

        try {
            CompoundTag nbtData = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
            SchematicLoader.SchematicData data = new SchematicLoader.SchematicData();
            data.readBasicInfo(nbtData);
            return data;
        } catch (IOException e) {
            System.err.println("[保存器] 读取失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}