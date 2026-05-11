package com.snowcity.maid_construction_team.core.schematic.persistence;

import com.snowcity.maid_construction_team.core.schematic.IBlueprintPersistence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 原版压缩 NBT 格式的蓝图持久化实现。
 * <p>
 * 使用 {@link NbtIo#readCompressed} 和 {@link NbtIo#writeCompressed} 进行文件读写，
 * 文件扩展名为 {@code .nbt}。
 */
public class SimpleBlueprintPersistence implements IBlueprintPersistence {

    /** 该格式使用的文件扩展名（包含点号） */
    private static final String EXTENSION = ".nbt";

    /**
     * 复制源文件到目标目录，并重命名为指定的蓝图名称。
     *
     * @param sourcePath    源蓝图文件路径
     * @param targetDir     目标目录
     * @param blueprintName 蓝图名称（不含扩展名）
     * @return 新文件的完整路径
     * @throws IOException 如果文件操作失败
     */
    @Override
    public Path saveFromFile(Path sourcePath, Path targetDir, String blueprintName) throws IOException {
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(blueprintName + EXTENSION);
        Files.copy(sourcePath, targetFile, StandardCopyOption.REPLACE_EXISTING);
        return targetFile;
    }

    /**
     * 将蓝图 NBT 压缩写入目标目录，文件名为 {@code blueprintName.nbt}。
     *
     * @param blueprintNbt  蓝图原始 NBT
     * @param targetDir     目标目录
     * @param blueprintName 蓝图名称（不含扩展名）
     * @return 新文件的完整路径
     * @throws IOException 如果写入失败
     */
    @Override
    public Path saveFromNbt(CompoundTag blueprintNbt, Path targetDir, String blueprintName) throws IOException {
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(blueprintName + EXTENSION);
        NbtIo.writeCompressed(blueprintNbt, targetFile);
        return targetFile;
    }

    @Override
    public CompoundTag load(Path filePath) throws IOException {
        return NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
    }

    /**
     * 删除指定路径的文件（如果存在）。
     *
     * @param filePath 文件的完整路径
     * @throws IOException 如果删除操作失败
     */
    @Override
    public void delete(Path filePath) throws IOException {
        Files.deleteIfExists(filePath);
    }
}