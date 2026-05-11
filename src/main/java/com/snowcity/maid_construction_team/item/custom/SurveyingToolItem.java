package com.snowcity.maid_construction_team.item.custom;

import com.snowcity.maid_construction_team.old.screen.SurveyingToolScreen;
import com.snowcity.maid_construction_team.old.util.AreaSchematicCapturer;
import com.snowcity.maid_construction_team.old.util.SchematicLoader;
import com.snowcity.maid_construction_team.old.util.SchematicPersistence;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 测绘仪物品
 * 功能类似 Create 模组的蓝图与笔：右键选点，蹲下右键清空，右键空气保存
 */
public class SurveyingToolItem extends Item {

    private static final String KEY_FIRST_POS = "FirstPos";
    private static final String KEY_SECOND_POS = "SecondPos";

    public SurveyingToolItem(Properties properties) {
        super(properties);
    }

    /**
     * 右键点击方块：选择坐标点或清空
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();

        // 蹲下右键：清空所有记录
        if (player.isShiftKeyDown()) {
            clearAllPositions(stack);
            AreaSchematicCapturer.clearCache(); // 确保清空
            sendStatus(player, "§e已清空所有记录");
            return InteractionResult.CONSUME;
        }

        // 普通右键：选择坐标点
        BlockPos firstPos = getStoredPos(stack, KEY_FIRST_POS);

        if (firstPos == null) {
            // 设置第一个点
            setStoredPos(stack, KEY_FIRST_POS, clickedPos);
            clearStoredPos(stack, KEY_SECOND_POS);
            sendStatus(player, "§a已设置起点: " + formatPos(clickedPos));
        } else {
            // 设置第二个点，并预扫描区域（关键修复！）
            setStoredPos(stack, KEY_SECOND_POS, clickedPos);
            sendStatus(player, "§b已设置终点: " + formatPos(clickedPos));

            // 立即扫描区域，缓存材料统计
            SchematicLoader.SchematicData data = AreaSchematicCapturer.capture(level, firstPos, clickedPos);
            if (data != null && !data.blocks.isEmpty()) {
                sendStatus(player, "§a扫描完成，共 " + data.blocks.size() + " 个方块 (右键空气保存)");
            } else {
                sendStatus(player, "§c区域为空，请重新选择");
            }
        }

        return InteractionResult.CONSUME;
    }

    /**
     * 右键点击空气：打开保存菜单
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            BlockPos pos1 = getStoredPos(stack, KEY_FIRST_POS);
            BlockPos pos2 = getStoredPos(stack, KEY_SECOND_POS);

            if (pos1 != null && pos2 != null) {
                Minecraft.getInstance().setScreen(new SurveyingToolScreen(stack, hand));
            } else if (pos1 != null) {
                sendStatus(player, "§e请选择第二个点");
            } else {
                sendStatus(player, "§c请先选择起点");
            }
        }
        return InteractionResultHolder.success(stack);
    }

    /**
     * 执行蓝图保存（服务端直接调用）
     */
    // 在 saveSchematic 方法中
    // 在 saveSchematic 方法中，只修改保存调用这一行
    // 只修改saveSchematic方法，其他保持不变
    public static void saveSchematic(Player player, ItemStack stack, String fileName) {
        Level level = player.level();
        if (level.isClientSide) return;

        BlockPos pos1 = getStoredPos(stack, KEY_FIRST_POS);
        BlockPos pos2 = getStoredPos(stack, KEY_SECOND_POS);

        if (pos1 == null || pos2 == null) {
            sendStatus(player, "§c坐标数据丢失，请重新选择");
            return;
        }

        sendStatus(player, "§e正在保存蓝图...");

        // 捕获方块数据
        SchematicLoader.SchematicData data = AreaSchematicCapturer.capture(level, pos1, pos2);

        if (data == null || data.isEmpty()) {
            sendStatus(player, "§c所选区域为空");
            return;
        }

        // 处理文件名
        String safeName = (fileName == null || fileName.isBlank()) ? "blueprint_" + System.currentTimeMillis() : fileName;

        // 调用Create兼容的保存方法
        boolean success = SchematicPersistence.saveCreateCompatibleSchematic(safeName, data, level);

        if (success) {
            sendStatus(player, "§a✅ 蓝图保存成功！可在Create蓝图桌中加载");
            clearAllPositions(stack);
            AreaSchematicCapturer.clearCache();
        } else {
            sendStatus(player, "§c❌ 蓝图保存失败，请查看控制台");
        }
    }

    // =========================================================================
    // NBT 存储工具
    // =========================================================================

    private static CompoundTag getCustomData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    private static void setCustomData(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void setStoredPos(ItemStack stack, String key, BlockPos pos) {
        CompoundTag tag = getCustomData(stack);
        tag.putIntArray(key, new int[]{pos.getX(), pos.getY(), pos.getZ()});
        setCustomData(stack, tag);
    }

    public static BlockPos getStoredPos(ItemStack stack, String key) {
        CompoundTag tag = getCustomData(stack);
        if (tag.contains(key, net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            int[] arr = tag.getIntArray(key);
            return new BlockPos(arr[0], arr[1], arr[2]);
        }
        return null;
    }

    private static void clearStoredPos(ItemStack stack, String key) {
        CompoundTag tag = getCustomData(stack);
        tag.remove(key);
        setCustomData(stack, tag);
    }

    private static void clearAllPositions(ItemStack stack) {
        CompoundTag tag = getCustomData(stack);
        tag.remove(KEY_FIRST_POS);
        tag.remove(KEY_SECOND_POS);
        setCustomData(stack, tag);
    }

    // =========================================================================
    // 辅助工具
    // =========================================================================

    private static void sendStatus(Player player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private static String formatPos(BlockPos pos) {
        return String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }
}