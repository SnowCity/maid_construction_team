package com.snowcity.maid_construction_team.config;

import com.snowcity.maid_construction_team.core.schematic.MaterialShortageStrategy;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组全局配置中心。
 * 所有放置相关的开关和参数均在此定义，支持在游戏运行时动态修改并立即生效，
 * 并通过 NeoForge 的配置系统自动保存到磁盘。
 */
public class MaidConstructionTeamConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ======================= 配置项定义 =======================

    /** 放置方块时是否引发方块更新（如红石信号、水流扩散等） */
    private static final ModConfigSpec.BooleanValue UPDATE_BLOCKS =
            BUILDER.comment("放置方块时是否引发方块更新（如红石信号、水流扩散等）。")
                    .define("updateBlocks", false);

    /** 是否放置蓝图中的空气方块。启用时会清空蓝图范围内的原有方块 */
    private static final ModConfigSpec.BooleanValue PLACE_AIR =
            BUILDER.comment("是否放置蓝图中的空气方块。启用时会清空蓝图范围内的原有方块。")
                    .define("placeAir", true);

    /** 是否启用材料消耗机制。关闭后放置方块将不消耗任何材料 */
    private static final ModConfigSpec.BooleanValue CONSUME_MATERIALS =
            BUILDER.comment("是否启用材料消耗机制。关闭后放置方块将不消耗任何材料。")
                    .define("consumeMaterials", true);

    /** 渐进式放置时，每个游戏刻最多放置的方块数量 */
    private static final ModConfigSpec.IntValue BLOCKS_PER_TICK =
            BUILDER.comment("渐进式放置时，每个游戏刻最多放置的方块数量。")
                    .defineInRange("blocksPerTick", 20, 1, 100);

    /** 放置操作的间隔游戏刻数。例如设为 1 表示每刻都放置，设为 5 表示每 5 刻放置一次 */
    private static final ModConfigSpec.IntValue PLACEMENT_INTERVAL =
            BUILDER.comment("放置操作的间隔游戏刻数。例如设为 1 表示每刻都放置，设为 5 表示每 5 刻放置一次。")
                    .defineInRange("placementInterval", 1, 1, 100);

    /** 是否强制清除蓝图范围内的原有方块。即使蓝图在该位置是空气，也会移除原有方块 */
    private static final ModConfigSpec.BooleanValue CLEAR_ORIGINAL_BLOCKS =
            BUILDER.comment("是否强制清除蓝图范围内的原有方块。即使蓝图在该位置是空气，也会移除原有方块。")
                    .define("clearOriginalBlocks", true);

    /** 清除原有方块时，是否模拟玩家破坏并产生掉落物，尝试回收至材料容器 */
    private static final ModConfigSpec.BooleanValue OBTAIN_DROPS =
            BUILDER.comment("清除原有方块时，是否模拟玩家破坏并产生掉落物，尝试回收至材料容器。")
                    .define("obtainDrops", true);

    /** 每个参与建造的非玩家生物实体（如女仆、佣兵）提供的额外每Tick放置方块数 */
    private static final ModConfigSpec.IntValue LABORER_BONUS =
            BUILDER.comment("每个参与建造的非玩家生物实体（如女仆、佣兵）提供的额外每Tick放置方块数。")
                    .defineInRange("laborerBonus", 1, 0, 100);

    /** 是否在查找工具和材料时将玩家背包纳入搜索范围。默认关闭，以保护玩家物品。 */
    private static final ModConfigSpec.BooleanValue INCLUDE_PLAYER_INVENTORY =
            BUILDER.comment("是否在查找工具和材料时将玩家背包纳入搜索范围。默认关闭。")
                    .define("includePlayerInventory", false);

    private static final ModConfigSpec.BooleanValue ENABLE_TOOL_SIMULATION =
            BUILDER.comment("是否开启工具模拟。开启后清除原有方块会寻找工具并消耗耐久，掉落物回收。")
                    .define("enableToolSimulation", false);

    private static final ModConfigSpec.EnumValue<MaterialShortageStrategy> MATERIAL_SHORTAGE_STRATEGY =
            BUILDER.comment("材料不足时的默认策略：PAUSE（暂停），SKIP（跳过）")
                    .defineEnum("materialShortageStrategy", MaterialShortageStrategy.PAUSE);

    private static final ModConfigSpec.BooleanValue ALLOW_BOSS_CONTRACT =
            BUILDER.comment("是否允许与BOSS生物签订奴隶契约")
                    .define("allowBossContract", false);

    private static final ModConfigSpec.DoubleValue CONTRACT_HEALTH_THRESHOLD =
            BUILDER.comment("签订契约时生物生命值必须低于此比例 (0.0 ~ 1.0)")
                    .defineInRange("contractHealthThreshold", 0.5, 0.0, 1.0);

    // --- 预览相关 ---
    public static final ModConfigSpec.IntValue PREVIEW_FRAME_RED = BUILDER
            .comment("预览边框红色分量 (0-255)")
            .defineInRange("previewFrameRed", 255, 0, 255);
    public static final ModConfigSpec.IntValue PREVIEW_FRAME_GREEN = BUILDER
            .comment("预览边框绿色分量 (0-255)")
            .defineInRange("previewFrameGreen", 105, 0, 255);
    public static final ModConfigSpec.IntValue PREVIEW_FRAME_BLUE = BUILDER
            .comment("预览边框蓝色分量 (0-255)")
            .defineInRange("previewFrameBlue", 180, 0, 255);
    public static final ModConfigSpec.BooleanValue PREVIEW_DEPTH_TEST_ENABLED = BUILDER
            .comment("预览时是否默认开启深度测试")
            .define("previewDepthTestEnabled", false);
    public static final ModConfigSpec.IntValue PREVIEW_STEP_LARGE = BUILDER
            .comment("预览快速移动时的步长（格）")
            .defineInRange("previewStepLarge", 10, 1, 100);



    // 构建最终的配置规格，供注册使用
    public static final ModConfigSpec SPEC = BUILDER.build();

    // 单例实例
    private static MaidConstructionTeamConfig instance;

    /**
     * 获取配置的单例实例。
     */
    public static MaidConstructionTeamConfig getInstance() {
        if (instance == null) {
            instance = new MaidConstructionTeamConfig();
        }
        return instance;
    }

    // 私有构造函数，防止外部实例化
    private MaidConstructionTeamConfig() {
    }

    /**
     * 将配置注册到指定的模组容器中。
     * 应在模组主类的构造函数里调用。
     *
     * @param modContainer 模组容器实例
     */
    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(Type.COMMON, SPEC);
    }

    // ======================= 公开的 Getter 方法 =======================

    public boolean isUpdateBlocks() {
        return UPDATE_BLOCKS.get();
    }

    public boolean isPlaceAir() {
        return PLACE_AIR.get();
    }

    public boolean isConsumeMaterials() {
        return CONSUME_MATERIALS.get();
    }

    public int getBlocksPerTick() {
        return BLOCKS_PER_TICK.get();
    }

    public static int getPlacementInterval() {
        return PLACEMENT_INTERVAL.get();
    }

    public boolean isClearOriginalBlocks() {
        return CLEAR_ORIGINAL_BLOCKS.get();
    }

    public boolean isObtainDrops() {
        return OBTAIN_DROPS.get();
    }

    public int getLaborerBonus() {
        return LABORER_BONUS.get();
    }

    public boolean isIncludePlayerInventory() {
        return INCLUDE_PLAYER_INVENTORY.get();
    }

    public boolean isEnableToolSimulation() { return ENABLE_TOOL_SIMULATION.get(); }

    public MaterialShortageStrategy getMaterialShortageStrategy() {
        return MATERIAL_SHORTAGE_STRATEGY.get();
    }

    public boolean isAllowBossContract() { return ALLOW_BOSS_CONTRACT.get(); }

    public double getContractHealthThreshold() { return CONTRACT_HEALTH_THRESHOLD.get(); }


    // getter 方法
    public int getPreviewFrameRed() { return PREVIEW_FRAME_RED.get(); }
    public int getPreviewFrameGreen() { return PREVIEW_FRAME_GREEN.get(); }
    public int getPreviewFrameBlue() { return PREVIEW_FRAME_BLUE.get(); }
    public boolean isPreviewDepthTestEnabled() { return PREVIEW_DEPTH_TEST_ENABLED.get(); }
    public int getPreviewStepLarge() { return PREVIEW_STEP_LARGE.get(); }

    // ======================= 公开的 Setter 方法 =======================

    public void setUpdateBlocks(boolean value) {
        UPDATE_BLOCKS.set(value);
    }

    public void setPlaceAir(boolean value) {
        PLACE_AIR.set(value);
    }

    public void setConsumeMaterials(boolean value) {
        CONSUME_MATERIALS.set(value);
    }

    public void setBlocksPerTick(int value) {
        BLOCKS_PER_TICK.set(value);
    }

    public void setPlacementInterval(int value) {
        PLACEMENT_INTERVAL.set(value);
    }

    public void setClearOriginalBlocks(boolean value) {
        CLEAR_ORIGINAL_BLOCKS.set(value);
    }

    public void setObtainDrops(boolean value) {
        OBTAIN_DROPS.set(value);
    }

    public void setLaborerBonus(int value) {
        LABORER_BONUS.set(value);
    }

    public void setIncludePlayerInventory(boolean value) {
        INCLUDE_PLAYER_INVENTORY.set(value);
    }

    public void setEnableToolSimulation(boolean v) { ENABLE_TOOL_SIMULATION.set(v); }

    public void setMaterialShortageStrategy(MaterialShortageStrategy value) {
        MATERIAL_SHORTAGE_STRATEGY.set(value);
    }
}