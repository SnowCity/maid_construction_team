package com.snowcity.maid_construction_team.core.schematic;

import net.minecraft.nbt.CompoundTag;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 蓝图数据持久化接口。
 * <p>
 * 负责蓝图原始 NBT 数据的底层文件读写。
 * 每个实现对应一种蓝图文件格式（如机械动力 .nbt），
 * 通常与 {@link ISchematicReader} 配套。
 */
public interface IBlueprintPersistence {

    /**
     * 将现有的蓝图文件复制到目标目录，并重命名。
     *
     * @param sourcePath    源蓝图文件路径
     * @param targetDir     目标目录
     * @param blueprintName 蓝图的显示名称（不含扩展名）
     * @return 新文件的完整路径
     * @throws IOException 如果复制失败
     */
    Path saveFromFile(Path sourcePath, Path targetDir, String blueprintName) throws IOException;

    /**
     * 将内存中的蓝图 NBT 数据以本格式写入目标目录的文件。
     *
     * @param blueprintNbt  蓝图原始 NBT
     * @param targetDir     目标目录
     * @param blueprintName 蓝图的显示名称（不含扩展名）
     * @return 新文件的完整路径
     * @throws IOException 如果写入失败
     */
    Path saveFromNbt(CompoundTag blueprintNbt, Path targetDir, String blueprintName) throws IOException;

    /**
     * 从指定文件路径加载蓝图原始 NBT。
     *
     * @param filePath 文件路径
     * @return 蓝图的原始 NBT 数据
     * @throws IOException 如果读取或解压失败
     */
    CompoundTag load(Path filePath) throws IOException;

    /**
     * 删除指定的蓝图文件。
     *
     * @param filePath 文件的完整路径
     * @throws IOException 如果删除失败
     */
    void delete(Path filePath) throws IOException;
}