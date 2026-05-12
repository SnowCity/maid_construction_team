package com.snowcity.maid_construction_team;

import com.snowcity.maid_construction_team.api.contract.ContractScanner;
import com.snowcity.maid_construction_team.api.labor.LaborProviderRegistry;
import com.snowcity.maid_construction_team.api.labor.provider.MaidLaborProvider;
import com.snowcity.maid_construction_team.api.labor.provider.PetLaborProvider;
import com.snowcity.maid_construction_team.command.ConstructionStatusCommand;
import com.snowcity.maid_construction_team.core.init.ModAttachments;
import com.snowcity.maid_construction_team.network.handler.MaterialListClientHandler;
import com.snowcity.maid_construction_team.network.handler.PrintMaterialServerHandler;
import com.snowcity.maid_construction_team.network.handler.RequestMaterialListServerHandler;
import com.snowcity.maid_construction_team.network.handler.labor.DispatchLaborServerHandler;
import com.snowcity.maid_construction_team.network.handler.labor.LaborListClientHandler;
import com.snowcity.maid_construction_team.network.handler.labor.RecallLaborServerHandler;
import com.snowcity.maid_construction_team.network.handler.labor.RequestLaborListServerHandler;
import com.snowcity.maid_construction_team.network.handler.session.SessionStateChangedClientHandler;
import com.snowcity.maid_construction_team.network.ModifyChecklistPayload;
import com.snowcity.maid_construction_team.network.payload.*;
import com.snowcity.maid_construction_team.network.payload.blueprint.ImportBlueprintPayload;
import com.snowcity.maid_construction_team.network.handler.blueprint.ImportBlueprintServerHandler;
import com.snowcity.maid_construction_team.network.payload.blueprint.StartPlacementPayload;
import com.snowcity.maid_construction_team.network.handler.blueprint.StartPlacementServerHandler;
import com.snowcity.maid_construction_team.network.handler.session.ControlSessionServerHandler;
import com.snowcity.maid_construction_team.network.handler.session.RequestSessionsServerHandler;
import com.snowcity.maid_construction_team.network.handler.session.SessionsResponseClientHandler;
import com.snowcity.maid_construction_team.network.payload.labor.DispatchLaborPayload;
import com.snowcity.maid_construction_team.network.payload.labor.LaborListPayload;
import com.snowcity.maid_construction_team.network.payload.labor.RecallLaborPayload;
import com.snowcity.maid_construction_team.network.payload.labor.RequestLaborListPayload;
import com.snowcity.maid_construction_team.network.payload.session.ControlSessionPayload;
import com.snowcity.maid_construction_team.network.payload.session.RequestSessionsPayload;
import com.snowcity.maid_construction_team.network.payload.session.SessionStateChangedPayload;
import com.snowcity.maid_construction_team.network.payload.session.SessionsResponsePayload;
import com.snowcity.maid_construction_team.config.MaidConstructionTeamConfig;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.item.MaidConstructionTeamCreativeModeTabs;
import com.snowcity.maid_construction_team.item.MaidConstructionTeamItems;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;


// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(MaidConstructionTeam.MOD_ID)
public class MaidConstructionTeam {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "maid_construction_team";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public MaidConstructionTeam(IEventBus modEventBus, ModContainer modContainer) {
        // 自动注册模组主类中的方法
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerNetworking);

        // 监听命令注册事件
        NeoForge.EVENT_BUS.addListener(this::registerCommands);

        // 注册核心内容
        MaidConstructionTeamItems.register(modEventBus);
        MaidConstructionTeamCreativeModeTabs.register(modEventBus);

        // 数据组件
        ModDataComponents.REGISTER.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        LaborProviderRegistry.register(new MaidLaborProvider());

        modEventBus.addListener(this::addCreative);

        // 配置文件路径
//        modContainer.registerConfig(
//                ModConfig.Type.COMMON, MaidBuildConfig.SPEC, "maid_construction_team/old_maid_construction_team.toml"
//        );

        modContainer.registerConfig(
                ModConfig.Type.COMMON, MaidConstructionTeamConfig.SPEC, "maid_construction_team/maid_construction_team.toml"
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LaborProviderRegistry.register(new PetLaborProvider());
        ContractScanner.scanAndRegister();
    }

    // 加入到创造物品栏中
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
    }


    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // 网络数据包
    private void registerNetworking(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID);

        registrar.playToServer(RequestSessionsPayload.TYPE, RequestSessionsPayload.STREAM_CODEC, RequestSessionsServerHandler::handle);
        registrar.playToClient(SessionsResponsePayload.TYPE, SessionsResponsePayload.STREAM_CODEC, SessionsResponseClientHandler::handle);
        registrar.playToServer(ControlSessionPayload.TYPE, ControlSessionPayload.STREAM_CODEC, ControlSessionServerHandler::handle);
        registrar.playToServer(StartPlacementPayload.TYPE, StartPlacementPayload.STREAM_CODEC, StartPlacementServerHandler::handle);
        registrar.playToServer(ImportBlueprintPayload.TYPE, ImportBlueprintPayload.STREAM_CODEC, ImportBlueprintServerHandler::handle);
        registrar.playToClient(SessionStateChangedPayload.TYPE, SessionStateChangedPayload.STREAM_CODEC, SessionStateChangedClientHandler::handle);
        registrar.playToServer(DispatchLaborPayload.TYPE, DispatchLaborPayload.STREAM_CODEC, DispatchLaborServerHandler::handle);
        registrar.playToServer(RecallLaborPayload.TYPE, RecallLaborPayload.STREAM_CODEC, RecallLaborServerHandler::handle);
        registrar.playToServer(RequestLaborListPayload.TYPE, RequestLaborListPayload.STREAM_CODEC, RequestLaborListServerHandler::handle);
        registrar.playToClient(LaborListPayload.TYPE, LaborListPayload.STREAM_CODEC, LaborListClientHandler::handle);


        registrar.playToServer(ModifyChecklistPayload.TYPE, ModifyChecklistPayload.STREAM_CODEC, ModifyChecklistPayload::handle);
        registrar.playToClient(SyncHandItemPayload.TYPE, SyncHandItemPayload.STREAM_CODEC, SyncHandItemPayload::handleClient);
        registrar.playToServer(ModifyContractBookPayload.TYPE, ModifyContractBookPayload.STREAM_CODEC, ModifyContractBookPayload::handle);

        registrar.playToServer(RequestMaterialListPayload.TYPE, RequestMaterialListPayload.STREAM_CODEC, RequestMaterialListServerHandler::handle);
        registrar.playToClient(MaterialListPayload.TYPE, MaterialListPayload.STREAM_CODEC, MaterialListClientHandler::handle);

        registrar.playToServer(PrintMaterialPayload.TYPE, PrintMaterialPayload.STREAM_CODEC, PrintMaterialServerHandler::handle);

    }

    /**
     * 注册命令
     */
    private void registerCommands(RegisterCommandsEvent event) {

        ConstructionStatusCommand.register(event.getDispatcher());
    }
}
