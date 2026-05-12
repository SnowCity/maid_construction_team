package com.snowcity.maid_construction_team.item;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import com.snowcity.maid_construction_team.item.custom.*;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MaidConstructionTeamItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MaidConstructionTeam.MOD_ID);

    // 规划表 Schedule
    public static final DeferredItem<Item> SCHEDULE =
            ITEMS.register("schedule", () -> new ScheduleItem(new Item.Properties()));
    // 洩矢铁轮 Moriya Iron Ring
    public static final DeferredItem<Item> MORIYA_IRON_RING =
            ITEMS.register("moriya_iron_ring", () -> new MoriyaIronRingItem(new Item.Properties()));
    // 物资登记表 material_checklist
    public static final DeferredItem<Item> MATERIAL_CHECKLIST =
            ITEMS.register("material_checklist", () -> new MaterialChecklistItem(new Item.Properties()));
    // 蓝图纸 blueprint_paper
    public static final DeferredItem<Item> BLUEPRINT_PAPER =
            ITEMS.register("blueprint_paper", () -> new BlueprintPaperItem(new Item.Properties()));
    // 仆从契约 servant_contract
    public static final DeferredItem<Item> SERVANT_CONTRACT =
            ITEMS.register("servant_contract", () -> new ServantContractItem(new Item.Properties()));
    // 契约之书 contract_book
    public static final DeferredItem<Item> CONTRACT_BOOK =
            ITEMS.register("contract_book", () -> new ContractBookItem(new Item.Properties()));
    // 花名册 roster
    public static final DeferredItem<Item> ROSTER =
            ITEMS.register("roster", () -> new RosterItem(new Item.Properties().stacksTo(1)));


    public static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}
