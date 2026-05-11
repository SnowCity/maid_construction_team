package com.snowcity.maid_construction_team.api.labor;

import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

/**
 * 劳动力提供者接口。
 * <p>
 * 每种劳动力来源（如仆从契约、驯服宠物、未来可能的雇佣村民等）
 * 都通过实现此接口来接入花名册系统。
 * <p>
 * 实现类需要通过 {@link LaborProviderRegistry#register(ILaborProvider)} 注册，
 * 之后花名册会自动扫描并展示该来源的劳动力。
 * <p>
 * 每个提供者负责：
 * <ul>
 *   <li>扫描劳动力 — {@link #scanLabor(Player)}</li>
 *   <li>执行派遣 — {@link #dispatch(UUID, PlacementSession, Player)}</li>
 *   <li>执行召回 — {@link #recall(UUID, Player)}</li>
 * </ul>
 */
public interface ILaborProvider {

    /**
     * @return 提供者唯一标识，如 {@code "servant_contract"}、{@code "pet"}。
     *         该标识用于网络通信和注册表查找。
     */
    String getProviderId();

    /**
     * @return 提供者显示名称，如 {@code "仆从契约"}、{@code "驯服宠物"}。
     *         用于花名册 GUI 中的来源标签和过滤按钮。
     */
    String getDisplayName();

    /**
     * 扫描指定玩家当前所有属于此来源的劳动力。
     * <p>
     * 此方法在花名册 GUI 打开时或手动刷新时被调用。
     * 返回的 {@link LaborInfo} 列表中应包含每个劳动力的展示名称、生物类型、
     * 来源标识、当前状态（空闲/工作中/离线）以及关联的会话信息。
     *
     * @param player 目标玩家
     * @return 该来源下的所有劳动力列表，可能为空但不可为 {@code null}
     */
    List<LaborInfo> scanLabor(Player player);

    /**
     * 执行派遣操作。
     * <p>
     * 将指定劳动力派遣到给定的蓝图放置会话中。
     * 实现应至少完成以下操作：
     * <ol>
     *   <li>将劳动力 UUID 添加到会话的参与者列表中。</li>
     *   <li>执行来源特定的派遣逻辑（如仆从契约需在锚点附近生成仆从实体）。</li>
     *   <li>更新劳动力数据中的派遣状态（如契约的 {@code dispatchedSessionId}）。</li>
     * </ol>
     *
     * @param laborId 劳动力的唯一标识（仆从的 contractId 或宠物的 UUID）
     * @param session 目标蓝图放置会话
     * @param player  执行派遣的玩家
     * @return {@code true} 表示派遣成功，{@code false} 表示失败（如劳动力不存在或已派遣）
     */
    boolean dispatch(UUID laborId, PlacementSession session, Player player);

    /**
     * 执行召回操作。
     * <p>
     * 将指定劳动力从当前派遣的所有会话中撤回。
     * 实现应至少完成以下操作：
     * <ol>
     *   <li>从所有活跃会话的参与者列表中移除该劳动力 UUID。</li>
     *   <li>执行来源特定的召回逻辑（如仆从契约需删除世界中对应的仆从实体）。</li>
     *   <li>更新劳动力数据中的派遣状态（恢复为空闲）。</li>
     * </ol>
     *
     * @param laborId 劳动力的唯一标识
     * @param player  执行召回的玩家
     * @return {@code true} 表示召回成功，{@code false} 表示失败
     */
    boolean recall(UUID laborId, Player player);

    /**
     * @return 该提供者是否启用。
     *         花名册只会扫描已启用的提供者。
     *         可通过配置文件控制此值，允许玩家关闭特定劳动力来源。
     */
    boolean isEnabled();
}