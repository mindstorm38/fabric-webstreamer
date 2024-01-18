package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.theorozier.webstreamer.display.url.DisplayUrl;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.NotNull;

/**
 * This class is responsible for caching and keeping the number of layer to the minimum.
 */
@Environment(EnvType.CLIENT)
public class DisplayLayerManager {

    /** Max number of concurrent display layers. */
    private static final int MAX_LAYERS_COUNT = 20;
    /** Interval of cleanups for unused display layers. */
    private static final long CLEANUP_INTERVAL = 5L * 1000000000L;
 
    private final Int2ObjectOpenHashMap<DisplayLayer> layers = new Int2ObjectOpenHashMap<>();
    
    /** Common pools for shared and reusable heavy buffers. */
    private final DisplayLayerResources res = new DisplayLayerResources();

    /** Time in nanoseconds (monotonic) of the last cleanup for unused layers. */
    private long lastCleanup = 0;
    
    public DisplayLayerResources getResources() {
        return this.res;
    }
    
    @NotNull
    private DisplayLayer newLayerForUrl(DisplayUrl url) throws UnknownFormatException {
        String path = url.uri().getPath();
        if (path != null) {
            if (path.endsWith(".m3u8")) {
                return new DisplayLayerHls(url, this.res);
            } else if (path.endsWith(".jpeg") || path.endsWith(".jpg") || path.endsWith(".bmp") || path.endsWith(".png")) {
                return new DisplayLayerImage(url, this.res);
            }
        }
        throw new UnknownFormatException();
    }
    
    /**
     * Get a display layer from the given URL, the same URL returns the same layer.
     * @param url The display URL.
     * @return The layer specific to the given URL.
     * @throws OutOfLayerException Maximum layers count has been reached.
     * @throws UnknownFormatException The URL format is not recognized.
     */
    @NotNull
    public DisplayLayer getLayerForUrl(DisplayUrl url) throws OutOfLayerException, UnknownFormatException {
        DisplayLayer layer = this.layers.get(url.id());
        if (layer == null) {
            if (this.layers.size() >= MAX_LAYERS_COUNT) {
                throw new OutOfLayerException();
            }
            layer = this.newLayerForUrl(url);
            this.layers.put(url.id(), layer);
        }
        return layer;
    }

    /**
     * Tick all active display layers. This also calls {@link #cleanup()}
     * at regular interval.
     */
    public void tick() {

        RenderSystem.assertOnRenderThread();
        this.layers.values().forEach(DisplayLayer::tick);

        long now = System.nanoTime();
        if (now - this.lastCleanup >= CLEANUP_INTERVAL) {
            this.cleanup();
            this.lastCleanup = now;
        }

    }

    /**
     * Cleanup unused display layers.
     */
    public void cleanup() {
        RenderSystem.assertOnRenderThread();
        long now = System.nanoTime();
        this.layers.values().removeIf(displayLayer -> {
            if (displayLayer.isUnused(now)) {
                displayLayer.free();
                return true;
            }
            return false;
        });
    }

    /**
     * Free and remove all layers.
     */
    public void clear() {
        this.layers.values().forEach(DisplayLayer::free);
        this.layers.clear();
    }
    
    public static class OutOfLayerException extends Exception {}
    public static class UnknownFormatException extends Exception {}
    
}
