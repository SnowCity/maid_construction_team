package com.snowcity.maid_construction_team.client;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import com.snowcity.maid_construction_team.compat.schematic.CreateSchematicReader;
import com.snowcity.maid_construction_team.core.schematic.SchematicData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

@EventBusSubscriber(modid = MaidConstructionTeam.MOD_ID, value = Dist.CLIENT)
public class TestCreateSchematicReader {

    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
//        event.enqueueWork(TestCreateSchematicReader::runTest);
    }

    private static void runTest() {
        Path schematicPath = Path.of("schematics", "test.nbt");
        CreateSchematicReader reader = new CreateSchematicReader();

        try {
            SchematicData data = reader.read(schematicPath);
            LOGGER.info("=== Create Schematic Test ===");
            LOGGER.info("Format: {}", reader.getFormatName());
            LOGGER.info("Size: {}x{}x{}", data.getSize().getX(), data.getSize().getY(), data.getSize().getZ());
            LOGGER.info("Block count: {}", data.getBlockCount());
            LOGGER.info("Entity count: {}", data.getEntityCount());
            if (data.getBlockCount() > 0) {
                var firstBlock = data.getBlocks().get(0);
                LOGGER.info("First block: pos={}, state={}, hasBlockEntity={}",
                        firstBlock.getPos(), firstBlock.getState(), firstBlock.hasBlockEntity());
            }
            LOGGER.info("Test passed successfully!");
        } catch (IOException e) {
            LOGGER.error("Test failed: {}", e.getMessage(), e);
        }
    }
}
