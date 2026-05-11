package com.snowcity.maid_construction_team.core.schematic;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * 材料供应接口。
 * <p>
 * 为蓝图放置提供材料、工具和掉落回收支持。
 */
public interface IMaterialProvider {

    /**
     * 尝试消耗放置指定方块状态所需的材料。
     * 必须实现为原子操作：要么成功扣除并返回 true，要么材料不足返回 false 且不产生任何副作用。
     *
     * @param state 要放置的方块状态
     * @return true 表示材料充足且已消耗，false 表示材料不足
     */
    boolean tryConsume(BlockState state);

    /**
     * 将物品存入材料系统（例如标记的容器中）。
     * 用于回收破坏原有方块时产生的掉落物。
     *
     * @param stack 要存放的物品栈（不会被修改）
     * @return true 表示所有物品都已成功存入；false 表示部分或全部未能存入
     */
    boolean deposit(ItemStack stack);

    /**
     * 查找最适合破坏指定方块的有效工具。
     * 查找优先级：非玩家生物实体背包 → 方块容器 → 玩家背包（取决于配置）。
     * 在有效工具中选择耐久最低的。
     *
     * @param targetState 需要被破坏的方块状态
     * @return 找到的工具栈，若没有可用工具则返回 {@link ItemStack#EMPTY}
     */
    ItemStack findTool(BlockState targetState);

    /**
     * 将工具归还到原来的来源（因为破坏后工具可能还有耐久）。
     * 如果工具已损坏，应直接丢弃而不调用此方法。
     *
     * @param tool 要归还的工具栈
     */
    void returnTool(ItemStack tool);

    /**
     * 查询指定方块状态的当前库存数量。
     * 仅扫描，不做任何消耗。用于规划表这些只读场景。
     */
    int getStock(BlockState state);
}