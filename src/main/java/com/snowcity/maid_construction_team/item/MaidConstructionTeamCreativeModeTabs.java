package com.snowcity.maid_construction_team.item;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class MaidConstructionTeamCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MaidConstructionTeam.MOD_ID);

    public static final Supplier<CreativeModeTab> MAID_CONSTRUCTION_TEAM_TAB =
            CREATIVE_MODE_TABS.register("maid_construction_team_tab", () -> CreativeModeTab.builder()
                    // 分类栏的图标
                    .icon( () -> new ItemStack(MaidConstructionTeamItems.BLUEPRINT_PAPER.get()))
                    .title(Component.translatable("item_group.maid_construction_team_tab"))
                    .displayItems((itemDisplayParameters, output) -> {
                        // 需要在这个单开的创造物品栏里显示的物品
                        output.accept(MaidConstructionTeamItems.SCHEDULE);
                        output.accept(MaidConstructionTeamItems.MORIYA_IRON_RING);
                        output.accept(MaidConstructionTeamItems.MATERIAL_CHECKLIST);
                        output.accept(MaidConstructionTeamItems.BLUEPRINT_PAPER);
                        output.accept(MaidConstructionTeamItems.SERVANT_CONTRACT);
                        output.accept(MaidConstructionTeamItems.CONTRACT_BOOK);
                        output.accept(MaidConstructionTeamItems.ROSTER);
                    }).build());

    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
