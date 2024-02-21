package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.HashMap;

/**
 * This class is responsible for caching and keeping the number of layer to the minimum.
 */
@Environment(EnvType.CLIENT)
public class DisplayLayerManager {

    /** Max number of concurrent display layers. */
    private static final int MAX_LAYERS_COUNT = 20;
    /** Interval of cleanups for unused display layers. */
    private static final long CLEANUP_INTERVAL = 5L * 1000000000L;

    /** Cache of layers for each unique URI (and potentially other parameters). */
    private final HashMap<URI, DisplayLayer> layers = new HashMap<>();

    /** Common pools for shared and reusable heavy buffers. */
    private final DisplayLayerResources res = new DisplayLayerResources();

    /** Time in nanoseconds (monotonic) of the last cleanup for unused layers. */
    private long lastCleanup = 0;
    
    public DisplayLayerResources getResources() {
        return this.res;
    }
    
    @NotNull
    private DisplayLayer newLayerForUri(URI uri) throws UnknownFormatException {
        String path = uri.getPath();
        if (path != null) {
            if (path.endsWith(".m3u8")) {
                return new DisplayLayerHls(uri, this.res);
            } else if (path.endsWith(".jpeg") || path.endsWith(".jpg") || path.endsWith(".bmp") || path.endsWith(".png")) {
                return new DisplayLayerImage(uri, this.res);
            }
        }
        throw new UnknownFormatException();
    }
    
    /**
     * Get a display layer from the given URI, the same URI returns the same layer.
     * @param uri The display URI.
     * @return The layer specific to the given URI.
     * @throws OutOfLayerException Maximum layers count has been reached.
     * @throws UnknownFormatException The URL format is not recognized.
     */
    @NotNull
    public DisplayLayer getLayerForUri(URI uri) throws OutOfLayerException, UnknownFormatException {
        DisplayLayer layer = this.layers.get(uri);
        if (layer == null) {
            if (this.layers.size() >= MAX_LAYERS_COUNT) {
                throw new OutOfLayerException();
            }
            layer = this.newLayerForUri(uri);
            this.layers.put(uri, layer);
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
