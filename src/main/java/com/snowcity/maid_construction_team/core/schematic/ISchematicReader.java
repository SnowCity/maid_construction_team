package com.snowcity.maid_construction_team.core.schematic;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 蓝图解析器的统一接口。
 * 所有针对特定模组或格式的解析器都必须实现此接口。
 */
public interface ISchematicReader {
    /**
     * 返回此解析器所支持的蓝图格式名称。
     * 可用于日志记录或为用户提供信息。
     *
     * @return 格式名称，例如 "MineColonies Blueprint"
     */
    String getFormatName();

    /**
     * 从给定的文件路径读取并解析蓝图。
     *
     * @param filePath 蓝图文件的路径
     * @return 解析后得到的标准化 SchematicData 对象
     * @throws IOException 如果文件读取或解析过程中发生错误
     */
    SchematicData read(Path filePath) throws IOException;

    /**
     * 获取与当前解析器配套的蓝图持久化实现。
     * 该持久化器能够以相同的文件格式读写蓝图原始 NBT。
     *
     * @return 非空的持久化器实例
     */
    IBlueprintPersistence getPersistence();
}