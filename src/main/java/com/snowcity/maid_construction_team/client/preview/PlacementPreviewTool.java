package com.snowcity.maid_construction_team.client.preview;

/**
 * 蓝图预览微调工具类型。
 */
public enum PlacementPreviewTool {
    /** 水平移动：沿玩家视线方向平移蓝图 */
    MOVE_XZ("⇔ 水平移动"),
    /** 垂直移动：上下移动蓝图 */
    MOVE_Y("↕ 垂直移动"),
    /** 旋转：旋转蓝图 */
    ROTATE("↻ 旋转");

    private final String displayName;

    PlacementPreviewTool(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 切换到下一个工具 */
    public PlacementPreviewTool next() {
        PlacementPreviewTool[] tools = values();
        return tools[(ordinal() + 1) % tools.length];
    }

    /** 切换到上一个工具 */
    public PlacementPreviewTool previous() {
        PlacementPreviewTool[] tools = values();
        return tools[(ordinal() - 1 + tools.length) % tools.length];
    }
}