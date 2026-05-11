package com.snowcity.maid_construction_team.old.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MaidBuildConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // 会话超时时间
    public static final ModConfigSpec.IntValue SESSION_TIMEOUT_TICKS;
    // 是否启用超时
    public static final ModConfigSpec.BooleanValue ENABLE_SESSION_TIMEOUT;

    // 每次 tick 尝试放置的方块数（建造速度）
    public static ModConfigSpec.IntValue blocksPerTick;
    // 建造 tick 间隔（例如 1 = 每 tick 都尝试，20 = 每秒尝试一次）
    public static ModConfigSpec.IntValue buildInterval;

    static {
        // 分组: 建造速度
        BUILDER.push("build_speed");

        blocksPerTick = BUILDER
                .comment("女仆每次尝试建造时放置的方块数量")
                .defineInRange("blocksPerTick", 1, 1, 1024);

        buildInterval = BUILDER
                .comment("女仆建造的间隔（单位：tick，20 = 1秒）")
                .defineInRange("buildInterval", 10, 1, 200);

        BUILDER.pop();

        // 分组: 会话
        BUILDER.push("session");
        ENABLE_SESSION_TIMEOUT = BUILDER
                .comment("是否启用建筑任务超时自动注销建造任务(默认开启)")
                .define("enable_session_timeout", true);

        SESSION_TIMEOUT_TICKS = BUILDER
                .comment("建筑任务超时时间 (单位: 分钟, 默认5分钟, 仅在启用超时后生效), 最大超时时间为60分钟")
                .defineInRange("session_timeout_minutes", 5, 1,60);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static int getSessionTimeoutTick(){
        if (!ENABLE_SESSION_TIMEOUT.get()) return Integer.MAX_VALUE;
        return SESSION_TIMEOUT_TICKS.get() * 60 * 20;
    }
}