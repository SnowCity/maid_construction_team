package com.snowcity.maid_construction_team.client.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.snowcity.maid_construction_team.api.contract.ContractRoleRegistry;
import com.snowcity.maid_construction_team.api.contract.IContractEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.*;

@OnlyIn(Dist.CLIENT)
public class ContractGuideScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int TOP_OFFSET = 30;
    private static final int VISIBLE_ROWS = 10;
    private static final int ENTITY_ICON_SIZE = 18;

    private final Screen parent;

    private final List<GuideRow> rows = new ArrayList<>();
    private int scrollOffset = 0;

    public ContractGuideScreen(Screen parent) {
        super(Component.translatable("mct.screen.contract_guide.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(
                Button.builder(Component.translatable("mct.screen.back"), btn -> {
                            if (parent != null) {
                                Minecraft.getInstance().setScreen(parent);
                            } else {
                                onClose();
                            }
                        })
                        .pos(width / 2 - 40, height - 30)
                        .size(80, 20)
                        .build()
        );
        buildRows();
    }

    private void buildRows() {
        rows.clear();
        for (IContractEffect effect : ContractRoleRegistry.getAllEffects()) {
            String roleId = effect.getRoleId();
            List<ResourceLocation> entityRLs = ContractRoleRegistry.getEntitiesForRole(roleId);

            // 分工抬头
            rows.add(new GuideRow(Component.literal("§6◆ " + roleId + " §r"), true, null));

            // 效果描述
            for (Component desc : effect.getEffectDescriptions()) {
                rows.add(new GuideRow(desc, false, null));
            }

            // 生物图标行（过滤 null 后直接添加）
            for (ResourceLocation rl : entityRLs) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
                if (type != null) {
                    rows.add(new GuideRow(Component.translatable(type.getDescriptionId()), false, type));
                }
            }

            // 分隔行
            rows.add(new GuideRow(Component.empty(), false, null));
        }
        scrollOffset = 0;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "契约分工指南", width / 2, 10, 0xFFFFFF);

        int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        int startRow = scrollOffset;
        int endRow = Math.min(rows.size(), startRow + VISIBLE_ROWS);

        int y = TOP_OFFSET;
        for (int i = startRow; i < endRow; i++) {
            GuideRow row = rows.get(i);
            int color = row.isHeader ? 0xFFAA00 : 0xCCCCCC;

            // 固定图标绘制在 x=20 的位置，直接写死即可
            if (row.entityType != null) {
                renderEntityIcon(graphics, y, row.entityType);
                graphics.drawString(font, row.text, 20 + ENTITY_ICON_SIZE + 4, y + (ROW_HEIGHT - font.lineHeight) / 2, color);
            } else if (!row.isHeader) {
                graphics.drawString(font, row.text, 20, y + (ROW_HEIGHT - font.lineHeight) / 2, color);
            } else {
                graphics.drawString(font, row.text, 15, y + (ROW_HEIGHT - font.lineHeight) / 2, color);
            }
            y += ROW_HEIGHT;
        }

        if (rows.size() > VISIBLE_ROWS) {
            graphics.drawString(font, "滚动查看更多", width - 80, height - 15, 0x888888);
        }
    }

    /**
     * 渲染实体图标，y 坐标由调用者传入
     */
    private void renderEntityIcon(GuiGraphics graphics, int y, EntityType<?> type) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Entity entity = type.create(minecraft.level);
        if (!(entity instanceof LivingEntity living)) return;

        living.setPos(0, 0, 0);
        living.yBodyRot = 20.0f;
        living.yHeadRot = 20.0f;

        graphics.pose().pushPose();
        // 图标中心坐标
        float centerX = 20 + ENTITY_ICON_SIZE / 2.0f;
        float centerY = y + ENTITY_ICON_SIZE / 2.0f;
        graphics.pose().translate(centerX, centerY, 50.0F);
        // Y 轴负缩放使实体正立
        float scale = 20.0f;
        graphics.pose().scale(scale, -scale, scale);
        // 旋转 20°
        graphics.pose().mulPose(new Quaternionf().rotateY((float) Math.toRadians(20)));

        // 设置 GUI 实体光照
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        dispatcher.render(living, 0.0, 0.0, 0.0, 0.0F, 1.0F, graphics.pose(), graphics.bufferSource(), 15728880);
        graphics.flush();
        dispatcher.setRenderShadow(true);
        graphics.pose().popPose();
        Lighting.setupFor3DItems();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int) scrollY;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record GuideRow(Component text, boolean isHeader, @Nullable EntityType<?> entityType) {}
}