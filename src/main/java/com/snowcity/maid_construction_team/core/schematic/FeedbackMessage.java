package com.snowcity.maid_construction_team.core.schematic;

import java.util.HashMap;
import java.util.Map;

/**
 * 会话状态变更时产生的反馈消息。
 * <p>
 * 存储在 {@link PlacementSession} 内的消息列表中，供未来 UI 查询。
 */
public class FeedbackMessage {

    /** 消息类型枚举 */
    public enum Type {
        /** 任务已启动 */
        TASK_STARTED,
        /** 材料不足，等待补充 */
        MATERIAL_LACK,
        /** 材料已补足，任务恢复 */
        MATERIAL_RESTORED,
        /** 任务成功完成 */
        TASK_COMPLETED,
        /** 任务被取消 */
        TASK_CANCELLED,
        /** 因玩家离线而暂停 */
        TASK_PAUSED_OFFLINE,
        /** 玩家手动暂停 */
        TASK_PAUSED_MANUALLY,
        /** 任务从暂停中恢复（包括手动恢复、上线恢复） */
        TASK_RESUMED,
        /** 登记表遗失，无法获取材料来源 */
        CHECKLIST_LOST
    }

    private final long timestamp;
    private final Type type;
    private final String content;
    private final Map<String, Object> extraData;

    public FeedbackMessage(Type type, String content) {
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.content = content;
        this.extraData = new HashMap<>();
    }

    public FeedbackMessage(Type type, String content, Map<String, Object> extraData) {
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.content = content;
        this.extraData = extraData != null ? new HashMap<>(extraData) : new HashMap<>();
    }

    public long getTimestamp() { return timestamp; }
    public Type getType() { return type; }
    public String getContent() { return content; }
    public Map<String, Object> getExtraData() { return extraData; }
}