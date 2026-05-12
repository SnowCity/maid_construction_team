package com.snowcity.maid_construction_team.api.compat.create;

import net.neoforged.fml.ModList;

/**
 * Create + 女仆联动入口类
 * 【作用】检测 Create 模组是否存在，封装所有联动注册逻辑
 */
public class CreateMaidCompat {
    private static final String MOD_ID = "create";

    public static boolean init(){
        return ModList.get().isLoaded(MOD_ID);
    }
}
