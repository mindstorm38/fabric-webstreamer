package fr.theorozier.webstreamer;

import fr.theorozier.webstreamer.display.DisplayBlock;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebStreamerMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("webstreamer");

    public static final Block DISPLAY_BLOCK = new DisplayBlock();
    public static BlockEntityType<DisplayBlockEntity> DISPLAY_BLOCK_ENTITY;

    @Override
    public void onInitialize() {

        Registry.register(Registry.BLOCK, "webstreamer:display", DISPLAY_BLOCK);
        Registry.register(Registry.ITEM, "webstreamer:display", new BlockItem(DISPLAY_BLOCK, new FabricItemSettings().group(ItemGroup.REDSTONE)));
        DISPLAY_BLOCK_ENTITY = Registry.register(Registry.BLOCK_ENTITY_TYPE, "webstreamer:display", FabricBlockEntityTypeBuilder.create(DisplayBlockEntity::new, DISPLAY_BLOCK).build());

        LOGGER.info("WebStreamer started.");

    }

}
