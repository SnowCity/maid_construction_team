package com.snowcity.maid_construction_team.core.manager;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * 蓝图放置会话的状态机。
 * <p>
 * 集中定义所有合法状态及状态转换规则。
 * 所有状态变更都必须通过 {@link #transition(State, State)} 进行，
 * 若尝试非法转换将抛出异常，从而杜绝状态不一致。
 */
public class SessionStateMachine {

    /**
     * 会话可能处于的状态。
     */
    public enum State implements StringRepresentable {
        /** 任务正在 Tick 中逐步放置方块 */
        RUNNING,
        /** 材料不足，等待玩家补充后自动恢复 */
        WAITING_MATERIALS,
        /** 玩家主动暂停（手动） */
        PAUSED,
        /** 玩家离线，任务暂停，持久化到磁盘 */
        PAUSED_OFFLINE,
        /** 所有方块和实体已放置完成 */
        COMPLETED,
        /** 任务被手动取消 */
        CANCELLED;

        /** 用于网络传输和持久化的 Codec */
        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public @NotNull String getSerializedName() {
            return this.name().toLowerCase();
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC =
                StreamCodec.of(
                        (buf, state) -> buf.writeEnum(state),
                        buf -> buf.readEnum(State.class)
                );
    }

    // 从每个状态出发的允许目标状态集合
    private static final Set<State> FROM_RUNNING = EnumSet.of(
            State.WAITING_MATERIALS, State.PAUSED, State.PAUSED_OFFLINE, State.COMPLETED, State.CANCELLED
    );
    private static final Set<State> FROM_WAITING_MATERIALS = EnumSet.of(
            State.RUNNING, State.PAUSED, State.PAUSED_OFFLINE, State.CANCELLED
    );
    private static final Set<State> FROM_PAUSED = EnumSet.of(
            State.RUNNING, State.CANCELLED
    );
    private static final Set<State> FROM_PAUSED_OFFLINE = EnumSet.of(
            State.RUNNING, State.WAITING_MATERIALS, State.CANCELLED
    );

    /**
     * 执行一次状态转换，验证合法性后返回目标状态。
     *
     * @param current 当前状态（不能为终态 COMPLETED 或 CANCELLED）
     * @param target  期望的目标状态
     * @return 目标状态（合法转换时）
     * @throws IllegalStateException 如果转换不被允许
     */
    public static State transition(State current, State target) {
        // 终态不允许再转换
        if (current == State.COMPLETED || current == State.CANCELLED) {
            throw new IllegalStateException("Cannot transition from terminal state " + current);
        }
        Set<State> allowed = allowedTargets(current);
        if (!allowed.contains(target)) {
            throw new IllegalStateException("Illegal state transition: " + current + " -> " + target);
        }
        return target;
    }

    /**
     * 返回从指定状态可以合法转换到的目标状态集合。
     */
    public static Set<State> allowedTargets(State current) {
        return switch (current) {
            case RUNNING -> FROM_RUNNING;
            case WAITING_MATERIALS -> FROM_WAITING_MATERIALS;
            case PAUSED -> FROM_PAUSED;
            case PAUSED_OFFLINE -> FROM_PAUSED_OFFLINE;
            case COMPLETED, CANCELLED -> EnumSet.noneOf(State.class);
        };
    }
}