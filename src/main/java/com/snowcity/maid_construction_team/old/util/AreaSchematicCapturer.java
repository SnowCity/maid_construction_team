package com.snowcity.maid_construction_team.old.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 完全对齐Create模组的方块捕获器
 */
public class AreaSchematicCapturer {

    private static Map<String, Integer> rawMaterialCache = new HashMap<>();

    public static SchematicLoader.SchematicData capture(Level level, BlockPos posA, BlockPos posB) {
        int minX = Math.min(posA.getX(), posB.getX());
        int minY = Math.min(posA.getY(), posB.getY());
        int minZ = Math.min(posA.getZ(), posB.getZ());
        int maxX = Math.max(posA.getX(), posB.getX());
        int maxY = Math.max(posA.getY(), posB.getY());
        int maxZ = Math.max(posA.getZ(), posB.getZ());

        BlockPos minPos = new BlockPos(minX, minY, minZ);
        BlockPos maxPos = new BlockPos(maxX, maxY, maxZ);

        System.out.println("[捕获器] 边界: " + minPos + " -> " + maxPos);
        System.out.println("[捕获器] 尺寸: " + (maxX-minX+1) + "x" + (maxY-minY+1) + "x" + (maxZ-minZ+1));

        SchematicLoader.SchematicData data = new SchematicLoader.SchematicData();
        data.width = maxX - minX + 1;
        data.height = maxY - minY + 1;
        data.length = maxZ - minZ + 1;

        List<CompoundTag> paletteList = new ArrayList<>();
        Map<BlockState, Integer> stateToIndex = new HashMap<>();
        ListTag blocksList = new ListTag();
        Map<String, Integer> materialStats = new HashMap<>();

        int totalBlocks = 0;
        int validBlocks = 0;

        // 和Create完全一致的遍历顺序：Z → X → Y
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos worldPos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(worldPos);
                    totalBlocks++;

                    if (state.isAir()) continue;

                    validBlocks++;

                    int relX = x - minX;
                    int relY = y - minY;
                    int relZ = z - minZ;

                    int stateId;
                    if (stateToIndex.containsKey(state)) {
                        stateId = stateToIndex.get(state);
                    } else {
                        stateId = paletteList.size();
                        stateToIndex.put(state, stateId);
                        paletteList.add(serializeBlockState(state));
                    }

                    String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    materialStats.put(blockId, materialStats.getOrDefault(blockId, 0) + 1);

                    // 【关键修改】和Create完全一致的 pos 格式：ListTag<IntTag>
                    CompoundTag blockEntry = new CompoundTag();

                    // 不再是 putIntArray，而是创建一个 ListTag 包含 3 个 IntTag
                    ListTag posList = new ListTag();
                    posList.add(IntTag.valueOf(relX));
                    posList.add(IntTag.valueOf(relY));
                    posList.add(IntTag.valueOf(relZ));
                    blockEntry.put("pos", posList);

                    blockEntry.putInt("state", stateId);

                    BlockEntity be = level.getBlockEntity(worldPos);
                    if (be != null) {
                        CompoundTag beTag = be.saveWithFullMetadata(level.registryAccess());
                        beTag.remove("x");
                        beTag.remove("y");
                        beTag.remove("z");
                        blockEntry.put("nbt", beTag);
                    }

                    blocksList.add(blockEntry);
                }
            }
        }

        System.out.println("[捕获器] 总扫描方块: " + totalBlocks);
        System.out.println("[捕获器] 有效非空气方块: " + validBlocks);
        System.out.println("[捕获器] 调色板大小: " + paletteList.size());

        ListTag finalPalette = new ListTag();
        finalPalette.addAll(paletteList);

        data.palette = finalPalette;
        data.blocks = blocksList;
        data.entities = new ListTag();
        data.dataVersion = 3955;

        rawMaterialCache = new HashMap<>(materialStats);

        return data;
    }

    public static Map<String, Integer> getRawMaterialCache() {
        return rawMaterialCache;
    }

    public static void setRawMaterialCache(Map<String, Integer> cache) {
        rawMaterialCache = new HashMap<>(cache);
    }

    public static void clearCache() {
        rawMaterialCache = new HashMap<>();
    }

    private static CompoundTag serializeBlockState(BlockState state) {
        CompoundTag stateTag = new CompoundTag();
        stateTag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());

        if (!state.getValues().isEmpty()) {
            CompoundTag propertiesTag = new CompoundTag();
            state.getValues().forEach((property, value) -> {
                propertiesTag.putString(property.getName(), value.toString());
            });
            stateTag.put("Properties", propertiesTag);
        }

        return stateTag;
    }
}