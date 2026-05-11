package com.snowcity.maid_construction_team.core.schematic;

import com.snowcity.maid_construction_team.core.manager.SessionStateMachine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class PlacementSession {

    private static final Logger LOGGER = LogManager.getLogger();

    private UUID sessionId;
    private final UUID creatorUuid;
    /** 劳动力参与者集合（仅宠物和自定义劳动力） */
    private final Set<UUID> participantUuids;
    /** 当前激活的契约加成列表 */
    private final Set<UUID> activeContractIds = new HashSet<>();
    private final PlacementProgress progress;
    private final List<FeedbackMessage> feedbackMessages;
    private SessionStateMachine.State state;
    private ProgressivePlacer placer;
    private MaterialShortageStrategy shortageStrategy;
    private final String blueprintName;

    public PlacementSession(UUID creatorUuid, Set<UUID> participantUuids, PlacementProgress progress,
                            String blueprintName, MaterialShortageStrategy strategy) {
        Set<UUID> temp = new HashSet<>(participantUuids != null ? participantUuids : Collections.emptySet());
//        temp.add(creatorUuid);
        this.creatorUuid = creatorUuid;
        this.participantUuids = temp;
        this.sessionId = UUID.randomUUID();
        this.progress = progress;
        this.feedbackMessages = new ArrayList<>();
        this.state = SessionStateMachine.State.RUNNING;
        this.placer = null;
        this.blueprintName = blueprintName;
        this.shortageStrategy = strategy;
    }

    // ===== 基本属性和状态管理 =====
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public UUID getCreatorUuid() { return creatorUuid; }
    public String getBlueprintName() { return blueprintName; }
    public PlacementProgress getProgress() { return progress; }
    public List<FeedbackMessage> getFeedbackMessages() { return feedbackMessages; }
    public SessionStateMachine.State getState() { return state; }
    public void setState(SessionStateMachine.State newState) {
        this.state = SessionStateMachine.transition(this.state, newState);
    }
    public ProgressivePlacer getPlacer() { return placer; }
    public void setPlacer(ProgressivePlacer placer) { this.placer = placer; }
    public MaterialShortageStrategy getShortageStrategy() { return shortageStrategy; }
    public void setShortageStrategy(MaterialShortageStrategy strategy) { this.shortageStrategy = strategy; }
    public void addFeedback(FeedbackMessage msg) { feedbackMessages.add(msg); }

    // ===== 劳动力参与者管理 =====
    /** 只读视图，仅包含宠物和自定义劳动力 */
    public Set<UUID> getParticipantUuids() {
        return Collections.unmodifiableSet(participantUuids);
    }
    public boolean addParticipant(UUID entityUuid) {
        boolean added = participantUuids.add(entityUuid);
        if (added) LOGGER.info("[Session] Added participant UUID: {}", entityUuid);
        return added;
    }
    public boolean removeParticipant(UUID entityUuid) {
        if (entityUuid.equals(creatorUuid)) return false;
        boolean removed = participantUuids.remove(entityUuid);
        if (removed) LOGGER.info("[Session] Removed participant UUID: {}", entityUuid);
        return removed;
    }

    // ===== 契约加成管理 =====
    /** 添加一个激活的契约加成 */
    public boolean addContractBonus(UUID contractId) {
        boolean added = activeContractIds.add(contractId);
        if (added) LOGGER.info("[Session] Activated contract bonus: {}", contractId);
        return added;
    }
    /** 移除一个契约加成 */
    public boolean removeContractBonus(UUID contractId) {
        boolean removed = activeContractIds.remove(contractId);
        if (removed) LOGGER.info("[Session] Deactivated contract bonus: {}", contractId);
        return removed;
    }
    /** 获取当前所有激活的契约加成列表（只读） */
    public Set<UUID> getActiveContractIds() {
        return Collections.unmodifiableSet(activeContractIds);
    }
    /** 清空所有激活的契约加成 */
    public void clearContractBonuses() {
        activeContractIds.clear();
    }
}