package com.snowcity.maid_construction_team.client.event;

import com.mojang.blaze3d.vertex.*;
import com.snowcity.maid_construction_team.component.MaterialChecklistData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.item.custom.MaterialChecklistItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = "maid_construction_team", value = Dist.CLIENT)
public class ClientRenderEvents {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof MaterialChecklistItem)) return;

        // 从数据组件读取标记列表
        MaterialChecklistData data = stack.getOrDefault(
                ModDataComponents.MATERIAL_CHECKLIST.get(),
                new MaterialChecklistData(new CompoundTag())
        );
        ListTag containers = data.getContainerList();
        if (containers.isEmpty()) return;

        String currentDim = player.level().dimension().location().toString();

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.LINES);

        for (Tag t : containers) {
            CompoundTag entry = (CompoundTag) t;
            if (!entry.getString(MaterialChecklistData.TAG_DIMENSION).equals(currentDim)) continue;

            BlockPos pos = new BlockPos(
                    entry.getInt(MaterialChecklistData.TAG_X),
                    entry.getInt(MaterialChecklistData.TAG_Y),
                    entry.getInt(MaterialChecklistData.TAG_Z)
            );
            if (!pos.closerThan(player.blockPosition(), 64.0)) continue;

            AABB box = new AABB(pos);
            LevelRenderer.renderLineBox(poseStack, vertexConsumer, box, 1.0f, 0.84f, 0.0f, 1.0f);
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }
}