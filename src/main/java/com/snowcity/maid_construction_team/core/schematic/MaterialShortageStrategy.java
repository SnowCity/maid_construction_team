package com.snowcity.maid_construction_team.core.schematic;

/**
 * 材料不足时的应对策略。
 */
public enum MaterialShortageStrategy {
    /** 暂停整个会话，等待材料补足 */
    PAUSE,
    /** 跳过当前方块，继续处理下一个 */
    SKIP
}