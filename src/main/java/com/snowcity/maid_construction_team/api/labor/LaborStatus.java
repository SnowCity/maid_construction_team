package com.snowcity.maid_construction_team.api.labor;

/**
 * 劳动力状态枚举
 */
public enum LaborStatus {
    /** 空闲，可派遣 */
    IDLE,
    /** 工作中，已派遣到某会话 */
    WORKING,
    /** 离线，实体所在区块未加载或已死亡 */
    OFFLINE
}