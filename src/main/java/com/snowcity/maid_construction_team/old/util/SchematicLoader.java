package com.snowcity.maid_construction_team.old.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一蓝图加载器
 * 完全兼容 Create 模组蓝图格式
 */
public class SchematicLoader {

    private static final Path DEFAULT_DIR = FMLPaths.getOrCreateGameRelativePath(
            FMLPaths.GAMEDIR.get().resolve("schematics")
    );

    // 读取方法保持不变...
    private static CompoundTag readRawNbt(Path filePath) {
        if (!Files.exists(filePath)) return null;
        try {
            return NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static SchematicData readVanillaOrCreate(String fileName) {
        return readVanillaOrCreate(DEFAULT_DIR.resolve(fileName));
    }

    public static SchematicData readVanillaOrCreate(Path filePath) {
        CompoundTag root = readRawNbt(filePath);
        if (root == null || !isValidVanillaOrCreate(root)) return null;
        SchematicData data = new SchematicData();
        data.readBasicInfo(root);
        return data;
    }

    private static boolean isValidVanillaOrCreate(CompoundTag root) {
        return root.contains("size", Tag.TAG_LIST)
                && root.contains("palette", Tag.TAG_LIST)
                && root.contains("blocks", Tag.TAG_LIST);
    }

    // 写入方法
    public static boolean write(String fileName, CompoundTag data) {
        return write(DEFAULT_DIR.resolve(fileName), data);
    }

    public static boolean write(Path filePath, CompoundTag data) {
        if (data == null) return false;
        try {
            Files.createDirectories(filePath.getParent());
            NbtIo.writeCompressed(data, filePath);
            System.out.println("[Loader] 成功写入: " + filePath);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 数据容器
    // 只修改SchematicData内部类的writeToNbt方法，其他保持不变
    public static class SchematicData {
        public int width, height, length;
        public ListTag palette;
        public ListTag blocks;
        public ListTag entities = new ListTag();
        public int dataVersion = 3955;

        // 在 SchematicData 类的 readBasicInfo 方法中
        public void readBasicInfo(CompoundTag root) {
            ListTag sizeList = root.getList("size", net.minecraft.nbt.Tag.TAG_INT);
            this.width = sizeList.getInt(0);
            this.height = sizeList.getInt(1);
            this.length = sizeList.getInt(2);
            this.palette = root.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);

            // 【修改】兼容两种 pos 格式
            ListTag rawBlocksList = root.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
            this.blocks = new ListTag();

            for (int i = 0; i < rawBlocksList.size(); i++) {
                CompoundTag blockEntry = rawBlocksList.getCompound(i).copy();

                // 检查 pos 类型
                if (blockEntry.contains("pos", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                    // 旧格式：IntArrayTag，转换为 ListTag
                    int[] posArray = blockEntry.getIntArray("pos");
                    ListTag posList = new ListTag();
                    posList.add(IntTag.valueOf(posArray[0]));
                    posList.add(IntTag.valueOf(posArray[1]));
                    posList.add(IntTag.valueOf(posArray[2]));
                    blockEntry.put("pos", posList);
                }

                this.blocks.add(blockEntry);
            }

            if (root.contains("entities", net.minecraft.nbt.Tag.TAG_LIST)) {
                this.entities = root.getList("entities", net.minecraft.nbt.Tag.TAG_COMPOUND);
            }
        }

        /**
         * 【关键】100%对齐Create的NBT结构，无多余标签
         */
        public CompoundTag writeToNbt() {
            CompoundTag rootTag = new CompoundTag();

            // 严格按照Create的标签顺序写入
            // 1. size
            ListTag sizeList = new ListTag();
            sizeList.add(IntTag.valueOf(this.width));
            sizeList.add(IntTag.valueOf(this.height));
            sizeList.add(IntTag.valueOf(this.length));
            rootTag.put("size", sizeList);

            // 2. entities
            rootTag.put("entities", this.entities);

            // 3. blocks
            rootTag.put("blocks", this.blocks);

            // 4. palette
            rootTag.put("palette", this.palette);

            // 5. DataVersion
            rootTag.putInt("DataVersion", this.dataVersion);

            // 【重要】移除了多余的Version标签，Create的蓝图里没有这个字段
            return rootTag;
        }

        public boolean isEmpty() {
            return blocks == null || blocks.isEmpty();
        }

        public int getBlockCount() {
            return blocks == null ? 0 : blocks.size();
        }
    }

    public static class LitematicData {
        public CompoundTag metadata;
        public CompoundTag regions;
        public List<String> getRegionNames() { return new ArrayList<>(regions.getAllKeys()); }
    }

    public static class MineColoniesData {
        public int width, height, length;
        public CompoundTag rawBlueprintTag;
        public boolean hasCustomData;
    }
}