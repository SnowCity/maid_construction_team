package com.snowcity.maid_construction_team.api.servant;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 奴隶模型提供器接口。
 * <p>
 * 每个实现负责为指定类型的生物提供可用的模型变体列表、随机选择模型、
 * 以及对应的 GUI 小图标。默认实现使用原版生物模型。
 * <p>
 * 其他模组可通过实现此接口并注册到 {@link ServantModelRegistry} 来扩展奴隶外观。
 */
public interface IServantModelProvider {

    /**
     * 获取指定生物类型的所有可用模型变体标识符。
     * 每个变体对应一种视觉外观（例如僵尸有普通、尸壳、溺尸）。
     *
     * @param entityType 生物注册类型
     * @return 该类型可用的所有模型变体列表，不可为空
     */
    List<ResourceLocation> getModelVariants(EntityType<?> entityType);

    /**
     * 随机选择一个模型变体，用于契约签订时确定奴隶外观。
     *
     * @param entityType 生物注册类型
     * @return 选中的模型变体标识符，若该类型只有一个变体则返回它
     */
    ResourceLocation pickRandomVariant(EntityType<?> entityType);

    /**
     * 获取指定模型变体的 GUI 小图标（通常为 16x16 的纹理）。
     * 用于在花名册和契约之书列表中显示。
     *
     * @param entityType   生物注册类型
     * @param modelVariant 模型变体标识符
     * @param stack        契约物品栈（可用于获取某些上下文信息，通常为 null）
     * @return 图标纹理的 ResourceLocation
     */
    ResourceLocation getIcon(EntityType<?> entityType, ResourceLocation modelVariant, ItemStack stack);

    /**
     * 获取该生物类型对应仆从的实体纹理。
     * @param entityType 生物注册类型
     * @param modelVariant 模型变体标识符
     * @return 主纹理的 ResourceLocation
     */
    ResourceLocation getTexture(EntityType<?> entityType, ResourceLocation modelVariant);
}