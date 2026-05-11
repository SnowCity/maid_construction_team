package com.snowcity.maid_construction_team.api.contract;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import java.util.*;

/**
 * 契约分工注册表。
 * 管理生物类型→分工标签→效果对象及默认数值的映射，以及共存上限。
 */
public class ContractRoleRegistry {
    private static final Map<String, IContractEffect> ROLE_EFFECTS = new HashMap<>();
    private static final Map<ResourceLocation, String> ENTITY_ROLES = new HashMap<>();
    private static final Map<ResourceLocation, Integer> MAX_ACTIVE = new HashMap<>();
    private static final Map<ResourceLocation, Map<String, Float>> CUSTOM_PARAMS = new HashMap<>();

    /** 注册分工效果 */
    public static void registerEffect(IContractEffect effect) {
        ROLE_EFFECTS.put(effect.getRoleId(), effect);
    }

    /** 指定生物的主分工 */
    public static void assignRole(EntityType<?> entityType, String roleId) {
        ResourceLocation key = EntityType.getKey(entityType);
        ENTITY_ROLES.put(key, roleId);
    }

    /** 设置生物共存上限（默认3） */
    public static void setMaxActive(EntityType<?> entityType, int max) {
        ResourceLocation key = EntityType.getKey(entityType);
        MAX_ACTIVE.put(key, max);
    }

    /** 获取生物共存上限 */
    public static int getMaxActive(EntityType<?> entityType) {
        return MAX_ACTIVE.getOrDefault(EntityType.getKey(entityType), 3);
    }

    /** 获取生物的分工效果对象 */
    public static IContractEffect getEffect(EntityType<?> entityType) {
        ResourceLocation key = EntityType.getKey(entityType);
        String roleId = ENTITY_ROLES.get(key);
        if (roleId == null) return null;
        return ROLE_EFFECTS.get(roleId);
    }

    // 省略参数个性化设置的代码（可后续补充）



    static {
        loadFromSPI(); // 类加载时自动执行
    }

    /**
     * 通过 ServiceLoader 加载所有 IContractEffect 实现，
     * 并使用 @AutoContractEffect 注解自动注册效果和生物映射。
     */
    private static void loadFromSPI() {
        ServiceLoader<IContractEffect> loader = ServiceLoader.load(IContractEffect.class);
        for (IContractEffect effect : loader) {
            registerEffect(effect);

            AutoContractEffect def = effect.getClass().getAnnotation(AutoContractEffect.class);
            if (def != null) {
                for (String rawType : def.assignTo()) {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(rawType));
                    if (type != null) {
                        assignRole(type, effect.getRoleId());
                        setMaxActive(type, def.maxActive());
                    }
                }
            }
        }
    }

    /**
     * 获取所有已注册的分工效果对象（用于展示指南）
     */
    public static Collection<IContractEffect> getAllEffects() {
        return Collections.unmodifiableCollection(ROLE_EFFECTS.values());
    }

    /**
     * 获取分配了某个分工的所有生物类型（返回 ResourceLocation 列表，便于显示）
     */
    public static List<ResourceLocation> getEntitiesForRole(String roleId) {
        List<ResourceLocation> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, String> entry : ENTITY_ROLES.entrySet()) {
            if (entry.getValue().equals(roleId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
}