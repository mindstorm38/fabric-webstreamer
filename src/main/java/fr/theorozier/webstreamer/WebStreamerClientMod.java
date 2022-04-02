package fr.theorozier.webstreamer;

import fr.theorozier.webstreamer.display.client.DisplayUrlManager;
import fr.theorozier.webstreamer.twitch.TwitchClient;
import fr.theorozier.webstreamer.display.render.DisplayBlockEntityRenderer;
import fr.theorozier.webstreamer.display.render.DisplayLayerManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegLogCallback;

@Environment(EnvType.CLIENT)
public class WebStreamerClientMod implements ClientModInitializer {

    public static DisplayUrlManager DISPLAY_URLS;
    public static DisplayLayerManager DISPLAY_LAYERS;
    public static TwitchClient TWITCH_CLIENT;

    @Override
    public void onInitializeClient() {

        BlockEntityRendererRegistry.register(WebStreamerMod.DISPLAY_BLOCK_ENTITY, DisplayBlockEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(WebStreamerMod.DISPLAY_BLOCK, RenderLayer.getCutout());

        DISPLAY_URLS = new DisplayUrlManager();
        DISPLAY_LAYERS = new DisplayLayerManager();
        TWITCH_CLIENT = new TwitchClient();
        
        FFmpegLogCallback.setLevel(avutil.AV_LOG_QUIET);
    
    }

}
