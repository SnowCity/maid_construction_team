package com.snowcity.maid_construction_team.api.contract;

import com.snowcity.maid_construction_team.component.ContractBookData;
import com.snowcity.maid_construction_team.component.ServantContractData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.core.manager.PlacementSessionManager;
import com.snowcity.maid_construction_team.core.manager.PlayerSessionManager;
import com.snowcity.maid_construction_team.core.schematic.PlacementSession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 仆从契约加成管理器。
 * <p>
 * 负责将已签订的仆从契约激活为当前会话的建造加成，或取消激活。
 * 加成效果由 {@link com.snowcity.maid_construction_team.api.contract.ContractRoleRegistry} 中的分工定义决定。
 */
public class ContractBonusManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContractBonusManager.class);

    /**
     * 激活契约加成：将契约 ID 加入会话的加成列表，并标记契约状态。
     *
     * @param contractId 契约的 contractId
     * @param session    目标会话
     * @param player     执行操作的玩家
     * @return 激活成功返回 true
     */
    public static boolean activate(UUID contractId, PlacementSession session, Player player) {
        ServantContractData contractData = findContractData(player, contractId);
        if (contractData == null) {
            return false;
        }

        // 加入会话的加成列表
        boolean added = session.addContractBonus(contractId);
        if (!added) {
            return false;
        }

        // 更新契约的派遣状态（复用 dispatchedSessionId 标识激活的会话）
        updateContractDispatched(player, contractId, session.getSessionId());

        return true;
    }

    /**
     * 取消契约加成（玩家主动操作）：从所有活跃会话中移除该契约的加成，并清除派遣状态。
     * <p>
     * 注意：若建筑自然完成，会话已不再是活跃状态，此时应使用 {@link #clearContractState} 强制清除派遣标记。
     *
     * @param contractId 契约的 contractId
     * @param player     执行操作的玩家
     * @return 取消成功返回 true
     */
    public static boolean deactivate(UUID contractId, Player player) {
        PlacementSessionManager mgr = PlayerSessionManager.of(player);
        boolean removed = false;
        for (PlacementSession session : mgr.getActiveSessions()) {
            if (session.getActiveContractIds().contains(contractId)) {
                session.removeContractBonus(contractId);
                removed = true;
            }
        }

        if (removed) {
            // 清除契约的派遣标记
            updateContractDispatched(player, contractId, null);
        } else {
        }
        return removed;
    }

    /**
     * 强制清除契约的派遣状态，不依赖于活跃会话（用于建筑自然完成等场景）。
     * <p>
     * 调用后，契约在契约之书中会显示为“未激活”，且派遣标记被移除。
     * 建议在会话完成时遍历所有激活契约调用此方法，随后从会话中移除加成 ID。
     *
     * @param contractId 契约的 contractId
     * @param player     拥有该契约的玩家
     */
    public static void clearContractState(UUID contractId, Player player) {
        updateContractDispatched(player, contractId, null);
        LOGGER.info("[Clear] Contract {} state reset (dispatched ID removed)", contractId);
    }

    /**
     * 在玩家背包中查找指定 contractId 的契约数据。
     * 优先查找散装契约物品，再查找所有契约之书内部条目。
     */
    public static ServantContractData findContractData(Player player, UUID contractId) {
        // 1. 查找散装契约
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            ServantContractData data = stack.get(ModDataComponents.SERVANT_CONTRACT_DATA.get());
            if (data != null && data.contractId().equals(contractId)) {
                return data;
            }
        }

        // 2. 查找所有契约之书
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            ContractBookData bookData = stack.get(ModDataComponents.CONTRACT_BOOK_DATA.get());
            if (bookData != null) {
                for (ContractBookData.ContractEntry entry : bookData.entries()) {
                    if (entry.contractId().equals(contractId)) {
                        return new ServantContractData(
                                entry.contractId(),
                                entry.entityType(),
                                entry.servantName(),
                                entry.modelVariant(),
                                entry.dispatchedSessionId()
                        );
                    }
                }
            }
        }
        return null;
    }

    /**
     * 更新契约的派遣状态（标记或清除 dispatchedSessionId）。
     * 若契约条目在契约之书中，修改其内部 NBT；若为散装物品，修改物品的数据组件。
     * <p>
     * 此方法已改为 public，以便外部在清理状态时直接调用。
     */
    public static void updateContractDispatched(Player player, UUID contractId, UUID sessionId) {
        Optional<UUID> optionalId = Optional.ofNullable(sessionId);

        // 1. 尝试在契约之书中更新
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            ContractBookData bookData = stack.get(ModDataComponents.CONTRACT_BOOK_DATA.get());
            if (bookData != null) {
                List<ContractBookData.ContractEntry> newEntries = new ArrayList<>(bookData.entries());
                boolean found = false;
                for (int i = 0; i < newEntries.size(); i++) {
                    ContractBookData.ContractEntry entry = newEntries.get(i);
                    if (entry.contractId().equals(contractId)) {
                        newEntries.set(i, new ContractBookData.ContractEntry(
                                entry.contractId(), entry.entityType(), entry.servantName(),
                                entry.modelVariant(), optionalId
                        ));
                        found = true;
                        break;
                    }
                }
                if (found) {
                    stack.set(ModDataComponents.CONTRACT_BOOK_DATA.get(), new ContractBookData(newEntries));
                    return;
                }
            }
        }

        // 2. 尝试在散装物品中更新
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            ServantContractData data = stack.get(ModDataComponents.SERVANT_CONTRACT_DATA.get());
            if (data != null && data.contractId().equals(contractId)) {
                ServantContractData updated = new ServantContractData(
                        data.contractId(), data.entityType(), data.servantName(),
                        data.modelVariant(), optionalId
                );
                stack.set(ModDataComponents.SERVANT_CONTRACT_DATA.get(), updated);
                return;
            }
        }
    }
}