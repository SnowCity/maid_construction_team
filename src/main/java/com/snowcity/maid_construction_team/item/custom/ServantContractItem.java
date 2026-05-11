package com.snowcity.maid_construction_team.item.custom;

import com.snowcity.maid_construction_team.api.servant.IServantModelProvider;
import com.snowcity.maid_construction_team.api.servant.ServantModelRegistry;
import com.snowcity.maid_construction_team.api.servant.ServantNameProviderRegistry;
import com.snowcity.maid_construction_team.component.ServantContractData;
import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ServantContractItem extends Item {

    private static final Random RANDOM = new Random();

    public ServantContractItem(Properties properties) {
        super(properties.stacksTo(32)); // 空白契约可堆叠32个
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        // 已签订契约不可堆叠
        return stack.has(ModDataComponents.SERVANT_CONTRACT_DATA.get()) ? 1 : super.getMaxStackSize(stack);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, Player player,
                                                           @NotNull LivingEntity target, @NotNull InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        // 1. 只能对敌对生物使用
        if (!(target instanceof Enemy)) {
            player.sendSystemMessage(Component.literal("只能与敌对生物签订契约"));
            return InteractionResult.FAIL;
        }

        // 2. BOSS 检查
        EntityType<?> targetType = target.getType();
        if (!MaidConstructionTeamConfig.getInstance().isAllowBossContract() &&
                targetType.is(Tags.EntityTypes.BOSSES)) {
            player.sendSystemMessage(Component.literal("该生物太过强大，无法签订契约"));
            return InteractionResult.FAIL;
        }

        // 3. 生命值阈值检查
        float healthPercent = target.getHealth() / target.getMaxHealth();
        float threshold = (float) MaidConstructionTeamConfig.getInstance().getContractHealthThreshold();
        if (healthPercent > threshold) {
            player.sendSystemMessage(Component.literal("生物生命值过高，需先削弱至 " +
                    (int) (threshold * 100) + "% 以下"));
            return InteractionResult.FAIL;
        }

        // 4. 仅空白契约可签订
        if (stack.has(ModDataComponents.SERVANT_CONTRACT_DATA.get())) {
            player.sendSystemMessage(Component.literal("该契约已签订，不能重复签订"));
            return InteractionResult.FAIL;
        }

        // 5. 消耗一个空白契约，生成一个已签订契约
        ItemStack contractStack;
        if (stack.getCount() > 1) {
            contractStack = stack.split(1);      // 从堆中分离1个，原堆自动减少1
        } else {
            contractStack = stack;               // 手持只剩1个，直接使用（原引用变空）
        }
        // ★ 不再调用 stack.shrink(1)，避免双重消耗

        // 6. 签订数据
        // 6. 签订数据
        ResourceLocation typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(targetType);
        IServantModelProvider modelProvider = ServantModelRegistry.getProvider(targetType);
        ResourceLocation modelVariant = modelProvider.pickRandomVariant(targetType);
        String servantName = ServantNameProviderRegistry.getProvider().generateName(targetType);
        UUID contractId = UUID.randomUUID();

        ServantContractData data = new ServantContractData(contractId, typeKey, servantName, modelVariant, Optional.empty());
        contractStack.set(ModDataComponents.SERVANT_CONTRACT_DATA.get(), data);

        // 7. 将已签订契约放入玩家背包，若背包满则掉落地面
        if (!player.getInventory().add(contractStack)) {
            player.drop(contractStack, false);
        }

        // 8. 杀死生物，战利品由原版死亡事件处理
        target.kill();

        player.sendSystemMessage(Component.literal("已与 " + target.getName().getString() +
                " 签订契约，获得仆从 [" + servantName + "]"));

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        ServantContractData data = stack.get(ModDataComponents.SERVANT_CONTRACT_DATA.get());
        if (data != null) {
            tooltip.add(Component.literal("仆从: " + data.servantName()));
            tooltip.add(Component.literal("类型: " + data.entityType()));
            tooltip.add(Component.literal("状态: " +
                    (data.dispatchedSessionId().isPresent() ? "工作中" : "空闲")));
        } else {
            tooltip.add(Component.literal("空白契约 - 右键敌对生物签订"));
        }
    }
}