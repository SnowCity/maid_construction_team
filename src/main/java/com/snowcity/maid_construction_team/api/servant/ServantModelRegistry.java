package com.snowcity.maid_construction_team.api.servant;

import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * 奴隶模型提供器的注册表。
 * <p>
 * 默认注册一个 {@link VanillaServantModelProvider} 处理所有生物类型。
 * 其他模组可通过 {@link #register(EntityType, IServantModelProvider)} 覆盖特定类型的实现。
 */
public class ServantModelRegistry {

    private static final IServantModelProvider DEFAULT_PROVIDER = new VanillaServantModelProvider();
    private static final Map<EntityType<?>, IServantModelProvider> PROVIDERS = new HashMap<>();

    /**
     * 为指定生物类型注册自定义模型提供器。
     */
    public static void register(EntityType<?> entityType, IServantModelProvider provider) {
        PROVIDERS.put(entityType, provider);
    }

    /**
     * 获取指定生物类型的模型提供器，若未注册则返回默认实现。
     */
    public static IServantModelProvider getProvider(EntityType<?> entityType) {
        return PROVIDERS.getOrDefault(entityType, DEFAULT_PROVIDER);
    }
}