package com.snowcity.maid_construction_team.api.servant;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * 字母数字命名器的默认实现。
 * <p>
 * 命名格式：生物类型简写-序号，例如 Zom-001、Ske-002。
 * 如果玩家重命名过，则优先使用重命名后的名称。
 */
public class AlphanumericServantNameProvider implements IServantNameProvider {

    private final Map<String, Integer> counters = new HashMap<>();

    @Override
    public String generateName(EntityType<?> entityType) {
        String prefix = getTypePrefix(entityType);
        int next = counters.getOrDefault(prefix, 0) + 1;
        counters.put(prefix, next);
        return prefix + "-" + String.format("%03d", next);
    }

    @Override
    public String getProviderName() {
        return "Alphanumeric";
    }

    /**
     * 根据生物注册名生成类型简写（取 `path` 前3个大写字母）。
     */
    private String getTypePrefix(EntityType<?> entityType) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).getPath();
        return path.substring(0, Math.min(3, path.length())).toUpperCase();
    }
}