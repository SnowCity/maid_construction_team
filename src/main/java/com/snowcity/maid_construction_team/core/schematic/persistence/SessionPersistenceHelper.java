package com.snowcity.maid_construction_team.core.schematic.persistence;

import com.snowcity.maid_construction_team.compat.schematic.CreateSchematicReader;
import com.snowcity.maid_construction_team.core.manager.SessionStateMachine;
import com.snowcity.maid_construction_team.core.schematic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 会话持久化辅助工具。
 * <p>
 * 提供静态方法，用于将蓝图放置会话的蓝图数据和进度数据保存到磁盘，
 * 以及从磁盘加载并重建会话。使用 {@link IBlueprintPersistence} 处理蓝图文件，
 * 进度数据则保存为独立的 {@code session_data.nbt}。
 */
public class SessionPersistenceHelper {

    /** 进度数据文件名 */
    public static final String SESSION_DATA_FILE = "session_data.nbt";
    /** 蓝图文件名前缀（后缀由持久化实现决定） */
    public static final String BLUEPRINT_FILE_NAME = "blueprint";

    /**
     * 将会话数据持久化到玩家数据目录下的对应文件夹。
     *
     * @param session        要保存的会话
     * @param persistence    蓝图持久化实现（用于保存蓝图文件）
     * @param basePlayerDir  玩家数据根目录，例如 {@code playerdata/<UUID>/}
     * @throws IOException 如果文件操作失败
     */
    public static void save(PlacementSession session, IBlueprintPersistence persistence, Path basePlayerDir) throws IOException {
        UUID sessionId = session.getSessionId();
        Path sessionDir = basePlayerDir.resolve("maid_building_blueprint").resolve(sessionId.toString());
        Files.createDirectories(sessionDir);

        // 1. 保存蓝图原始 NBT（如果存在）
        ProgressivePlacer placer = session.getPlacer();
        if (placer != null) {
            SchematicData schematic = placer.getSchematicData();
            if (schematic != null && schematic.getOriginalNbt() != null) {
                persistence.saveFromNbt(schematic.getOriginalNbt(), sessionDir, BLUEPRINT_FILE_NAME);
            }
        }

        // 2. 保存进度数据
        PlacementProgress progress = session.getProgress();
        CompoundTag dataTag = new CompoundTag();

        // 会话标识
        dataTag.putUUID("sessionId", sessionId);
        // 锚点坐标
        BlockPos anchor = placer != null ? placer.getAnchor() : BlockPos.ZERO;
        dataTag.put("anchor", blockPosToNbt(anchor));
        // 旋转方向
        Rotation rotation = placer != null ? placer.getRotation() : Rotation.NONE;
        dataTag.putString("rotation", rotation.name());
        // 参与者列表
        dataTag.put("participants", uuidListToNbt(new ArrayList<>(session.getParticipantUuids())));
        // 当前方块索引
        dataTag.putInt("currentBlockIndex", progress.getCurrentBlockIndex());
        // 已消耗材料映射
        dataTag.put("materialConsumed", materialConsumedToNbt(progress.getMaterialConsumed()));
        // 缺失方块列表
        dataTag.put("missedBlocks", blockPosListToNbt(progress.getMissedBlocks()));
        // 材料不足策略
        dataTag.putString("shortageStrategy", session.getShortageStrategy().name());
        // 已放置实体计数
        dataTag.putInt("placedEntityCount", progress.getPlacedEntityCount());
        // 会话状态（保存时通常是 PAUSED_OFFLINE）
        dataTag.putString("state", session.getState().name());
        // 蓝图文件名（用于恢复显示）
        dataTag.putString("blueprintName", session.getBlueprintName());

        Path dataFile = sessionDir.resolve(SESSION_DATA_FILE);
        NbtIo.writeCompressed(dataTag, dataFile);
    }

    /**
     * 从玩家数据目录加载所有未完成的持久化会话。
     * 加载成功后会自动删除源文件夹，防止重复加载。
     *
     * @param basePlayerDir 玩家数据根目录
     * @param player        对应的玩家实例（用于某些上下文）
     * @return 包含重建所需信息的会话数据列表
     * @throws IOException 如果文件读取失败
     */
    public static List<LoadedSessionData> load(Path basePlayerDir, ServerPlayer player) throws IOException {
        Path blueprintDir = basePlayerDir.resolve("maid_building_blueprint");
        if (Files.notExists(blueprintDir)) return List.of();

        List<LoadedSessionData> sessions = new ArrayList<>();
        Files.list(blueprintDir).forEach(sessionDir -> {
            if (!Files.isDirectory(sessionDir)) return;
            try {
                // 寻找蓝图文件（由于可能存在不同扩展名，简单遍历目录下文件名为 "blueprint.*" 的第一个文件）
                Path blueprintFile = findBlueprintFile(sessionDir);
                Path dataFile = sessionDir.resolve(SESSION_DATA_FILE);
                if (blueprintFile == null || Files.notExists(dataFile)) return;

                // 加载蓝图 NBT（使用默认持久化实现，因为目前只有一种格式）
                IBlueprintPersistence persistence = new SimpleBlueprintPersistence();
                CompoundTag blueprintNbt = persistence.load(blueprintFile);
                // 加载进度数据
                CompoundTag dataTag = NbtIo.readCompressed(dataFile, NbtAccounter.unlimitedHeap());

                // 重建 SchematicData
                SchematicData schematic = CreateSchematicReader.parseFromNBT(blueprintNbt);
                // 提取所有字段
                UUID sessionId = dataTag.getUUID("sessionId");
                BlockPos anchor = blockPosFromNbt(dataTag.getCompound("anchor"));
                Rotation rotation = Rotation.valueOf(dataTag.getString("rotation"));
                Set<UUID> participants = uuidSetFromNbt(dataTag.getList("participants", Tag.TAG_COMPOUND));
                int currentBlockIndex = dataTag.getInt("currentBlockIndex");
                Map<Block, Integer> materialConsumed = materialConsumedFromNbt(dataTag.getCompound("materialConsumed"));
                List<BlockPos> missedBlocks = blockPosListFromNbt(dataTag.getList("missedBlocks", Tag.TAG_COMPOUND));
                MaterialShortageStrategy strategy = MaterialShortageStrategy.valueOf(dataTag.getString("shortageStrategy"));
                int placedEntityCount = dataTag.getInt("placedEntityCount");
                SessionStateMachine.State state = SessionStateMachine.State.valueOf(dataTag.getString("state"));
                String blueprintName = dataTag.getString("blueprintName");

                LoadedSessionData loaded = new LoadedSessionData(
                        sessionId, schematic, blueprintNbt, anchor, rotation,
                        participants, currentBlockIndex,
                        materialConsumed, missedBlocks, strategy, placedEntityCount, state,
                        blueprintName
                );
                sessions.add(loaded);
                // 加载后删除目录，避免重复加载
                deleteDirectory(sessionDir);
            } catch (Exception e) {
                // 记录日志，避免单个文件损坏影响其他会话
                e.printStackTrace();
            }
        });
        return sessions;
    }

    /**
     * 删除指定会话的持久化文件夹。
     *
     * @param sessionId     会话ID
     * @param basePlayerDir 玩家数据根目录
     * @throws IOException 如果删除失败
     */
    public static void delete(UUID sessionId, Path basePlayerDir) throws IOException {
        Path sessionDir = basePlayerDir.resolve("maid_building_blueprint").resolve(sessionId.toString());
        if (Files.exists(sessionDir)) {
            deleteDirectory(sessionDir);
        }
    }

    // ==================== NBT 序列化辅助方法 ====================

    private static CompoundTag blockPosToNbt(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    private static BlockPos blockPosFromNbt(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    private static ListTag uuidListToNbt(List<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", uuid);
            list.add(tag);
        }
        return list;
    }

    private static Set<UUID> uuidSetFromNbt(ListTag list) {
        Set<UUID> uuids = new HashSet<>();
        for (Tag tag : list) {
            if (tag instanceof CompoundTag c) {
                uuids.add(c.getUUID("uuid"));
            }
        }
        return uuids;
    }

    private static ListTag blockPosListToNbt(List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            list.add(blockPosToNbt(pos));
        }
        return list;
    }

    private static List<BlockPos> blockPosListFromNbt(ListTag list) {
        List<BlockPos> positions = new ArrayList<>();
        for (Tag tag : list) {
            if (tag instanceof CompoundTag c) {
                positions.add(blockPosFromNbt(c));
            }
        }
        return positions;
    }

    private static CompoundTag materialConsumedToNbt(Map<Block, Integer> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<Block, Integer> entry : map.entrySet()) {
            // 使用方块注册名作为键
            String key = BuiltInRegistries.BLOCK.getKey(entry.getKey()).toString();
            tag.putInt(key, entry.getValue());
        }
        return tag;
    }

    private static Map<Block, Integer> materialConsumedFromNbt(CompoundTag tag) {
        Map<Block, Integer> map = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(key));
            if (block != null) {
                map.put(block, tag.getInt(key));
            }
        }
        return map;
    }

    // ==================== 文件操作辅助 ====================

    private static Path findBlueprintFile(Path sessionDir) throws IOException {
        // 寻找第一个名为 "blueprint.*" 的文件
        return Files.list(sessionDir)
                .filter(path -> path.getFileName().toString().startsWith(BLUEPRINT_FILE_NAME + "."))
                .findFirst()
                .orElse(null);
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException ignored) {}
                    });
        }
    }

    // ==================== 内部数据结构 ====================

    /**
     * 从持久化文件加载后重建的会话数据，包含恢复所需的所有信息。
     */
    public record LoadedSessionData(
            UUID sessionId,
            SchematicData schematic,
            CompoundTag blueprintNbt,
            BlockPos anchor,
            Rotation rotation,
            Set<UUID> participants,
            int currentBlockIndex,
            Map<Block, Integer> materialConsumed,
            List<BlockPos> missedBlocks,
            MaterialShortageStrategy strategy,
            int placedEntityCount,
            SessionStateMachine.State state,
            String blueprintName
    ) {}
}