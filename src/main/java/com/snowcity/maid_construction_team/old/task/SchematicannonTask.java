package com.snowcity.maid_construction_team.old.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * SchematicannonTask 类实现了 IMaidTask 接口，用于新建一个自定义女仆的工作任务。
 * <p>
 * The SchematicannonTask class implements the IMaidTask interface to define a new custom maid work task.
 */
public class SchematicannonTask implements IMaidTask {

    /**
     * 任务的唯一标识符
     * 格式是"模组ID:任务名称"，这里注册为"schematicannon_task"
     * 蓝图加农炮任务
     */
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "schematicannon_task");

    /**
     * 任务的图标
     * 这里使用机械动力的蓝图大炮(schematicannon)作为图标
     * 这样玩家一眼就能看出来
     */
    private static final ItemStack ICON =
            BuiltInRegistries.ITEM
                    .get(ResourceLocation.fromNamespaceAndPath("create", "schematicannon"))
                    .getDefaultInstance();

    /**
     * 获取任务的 ID
     * <p>
     * Get the unique identifier of the task
     */
    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /**
     * 获取任务的图标
     * <p>
     * Get the icon of the task
     */
    @Override
    public ItemStack getIcon() {
        return ICON;
    }

    /**
     * 获取女仆在该任务时的音效，可以为 null
     * <p>
     * Get the sound when the maid in this task, can be null
     */
    @Override
    public SoundEvent getAmbientSound(EntityMaid entityMaid) {
        return null;
    }

    /**
     * 创建女仆 AI，这一块通过 Minecraft 原版的 BehaviorControl 来实现，可以参考 com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle
     * <p>
     * Create maid AI, this part is implemented through Minecraft's BehaviorControl, you can refer to com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle
     */
    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid entityMaid) {
        return List.of();
    }
}