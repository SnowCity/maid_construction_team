package com.snowcity.maid_construction_team.api.servant;

import net.minecraft.world.entity.EntityType;

/**
 * 仆从命名提供器接口。
 * <p>
 * 负责在契约签订时为仆从生成默认名称。
 * 其他模组可实现此接口来定制命名规则。
 */
public interface IServantNameProvider {

    /**
     * 为指定生物类型生成一个唯一的仆从名称。
     *
     * @param entityType 生物注册类型
     * @return 生成的仆从名称（应全局唯一）
     */
    String generateName(EntityType<?> entityType);

    /**
     * 获取该命名器的显示名称，用于配置文件或 UI。
     */
    String getProviderName();
}