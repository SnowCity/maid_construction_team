package com.snowcity.maid_construction_team.api.contract;

import java.lang.annotation.*;

/**
 * 加在 IContractEffect 实现类上，自动注册分工效果与生物映射。
 * 无需手动调用任何注册方法
 */
@Retention(RetentionPolicy.RUNTIME) // 生命周期
@Target(ElementType.TYPE)
public @interface AutoContractEffect {
    /** 分配该分工的生物类型（如 "minecraft:witch"） */
    String[] assignTo();
    /** 共存上限，默认 3 */
    int maxActive() default 3;
}