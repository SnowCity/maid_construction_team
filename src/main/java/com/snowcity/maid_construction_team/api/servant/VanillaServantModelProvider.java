package com.snowcity.maid_construction_team.api.servant;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 默认的奴隶模型提供器。
 * <p>
 * 直接使用原版生物自身的注册名作为唯一的模型变体，
 * 随机选择只是返回该固定值，图标纹理也基于原版生物纹理。
 */
public class VanillaServantModelProvider implements IServantModelProvider {

    @Override
    public List<ResourceLocation> getModelVariants(EntityType<?> entityType) {
        // 默认只有原版模型一个变体
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return List.of(key);
    }

    @Override
    public ResourceLocation pickRandomVariant(EntityType<?> entityType) {
        // 只有一个变体，直接返回
        return BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
    }

    @Override
    public ResourceLocation getIcon(EntityType<?> entityType, ResourceLocation modelVariant, ItemStack stack) {
        // 使用原版生物纹理路径作为图标（这里简化，实际应返回具体的纹理位置）
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/entity/" + id.getPath() + ".png");
    }

    @Override
    public ResourceLocation getTexture(EntityType<?> entityType, ResourceLocation modelVariant) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        String path = key.getPath();
        // 大部分原版实体纹理在 textures/entity/<path>/<path>.png
        // 少数特殊实体（如末影龙）在别处，暂时只处理常见人形和动物
        return ResourceLocation.fromNamespaceAndPath(key.getNamespace(), "textures/entity/" + path + "/" + path + ".png");
    }
}