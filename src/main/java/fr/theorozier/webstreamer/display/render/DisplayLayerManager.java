package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.theorozier.webstreamer.display.url.DisplayUrl;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

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

    public DisplayLayer forSource(DisplayUrl url) {
        DisplayLayer layer = this.layers.get(url.id());
        if (layer == null) {
            if (this.layers.size() >= MAX_LAYERS_COUNT) {
                return null;
            }
            layer = new DisplayLayer(this.res, url);
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
        this.layers.values().forEach(layer -> {
            layer.displayTick();
            layer.resetAudioSource();
        });

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
            if (displayLayer.displayIsUnused(now)) {
                displayLayer.displayFree();
                return true;
            }
            return false;
        });
    }

    /**
     * Free and remove all layers.
     */
    public void clear() {
        this.layers.values().forEach(DisplayLayer::displayFree);
        this.layers.clear();
    }
    
}
