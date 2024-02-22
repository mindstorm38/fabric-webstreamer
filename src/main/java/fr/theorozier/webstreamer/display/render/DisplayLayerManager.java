package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;
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

    /** List of unique layers, ticked, checked and freed. */
    private final ArrayList<DisplayLayer> layers = new ArrayList<>();

    /**
     * A mapping from URI to various layer types (type is not fixed). This can be a simple layer object if it's
     * possible to immediately return it, but it may also be set to some sub-mapping in the future for things such
     * as SVG that requires different layers depending on the display's actual size.
     */
    private final HashMap<URI, LayerGroup<?>> groups = new HashMap<>();

    /** Common pools for shared and reusable heavy buffers. */
    private final DisplayLayerResources res = new DisplayLayerResources();

    /** Time in nanoseconds (monotonic) of the last cleanup for unused layers. */
    private long lastCleanup = 0;
    
    public DisplayLayerResources getResources() {
        return this.res;
    }
    
    @NotNull
    private LayerGroup<?> newGroup(URI uri) throws UnknownFormatException {
        String path = uri.getPath();
        if (path != null) {
            if (path.endsWith(".m3u8")) {
                return new Layer(new DisplayLayerHls(uri, this.res));
            } else if (path.endsWith(".jpeg") || path.endsWith(".jpg") || path.endsWith(".bmp") || path.endsWith(".png")) {
                return new Layer(new DisplayLayerImage(uri, this.res));
            }
        }
        throw new UnknownFormatException();
    }

    /**
     * Add a new managed layer.
     * @param layer The layer to add.
     */
    public void addLayer(DisplayLayer layer) {
        this.layers.add(layer);
    }

    /**
     * Remove a manager layer from its groups and free it.
     * @param layer
     */
    private void removeLayer(DisplayLayer layer) {
        layer.free();
        this.groups.get(layer.uri).removeLayer(layer); // TODO:
    }

    /**
     * Get a display layer from the given URI, the same URI returns the same layer.
     * @param uri The display URI.
     * @return The layer specific to the given URI.
     * @throws OutOfLayerException Maximum layers count has been reached.
     * @throws UnknownFormatException The URL format is not recognized.
     */
    @NotNull
    public DisplayLayer getLayer(URI uri, DisplayBlockEntity display) throws OutOfLayerException, UnknownFormatException {

        LayerGroup<?> group = this.groups.get(uri);
        if (group == null) {

            if (this.layers.size() >= MAX_LAYERS_COUNT) {
                // TODO: Rework, make it depends on the cost of layers (image lighter than HLS).
                throw new OutOfLayerException();
            }

            group = this.newGroup(uri);
            this.groups.put(uri, group);

        }

        return group.getLayer(this, display);
    }

    /**
     * Tick all active display layers. This also calls {@link #cleanup()}
     * at regular interval.
     */
    public void tick() {

        RenderSystem.assertOnRenderThread();
        this.layers.forEach(DisplayLayer::tick);

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
        this.layers.removeIf(layer -> {

            if (layer.isUnused(now)) {
                this.removeLayer(layer);
                return true;
            }

            return false;

        });
    }

    /**
     * Free and remove all layers.
     */
    public void clear() {
        this.layers.forEach(this::removeLayer);
        this.layers.clear();
    }
    
    public static class OutOfLayerException extends Exception {}
    public static class UnknownFormatException extends Exception {}

    private interface LayerGroup<T extends DisplayLayer> {
        T getLayer(DisplayLayerManager manager, DisplayBlockEntity display);
        boolean removeLayer(T layer);
    }

    private static class Layer implements LayerGroup<DisplayLayer> {

        private final DisplayLayer layer;
        private boolean added = false;

        private Layer(DisplayLayer layer) {
            this.layer = layer;
        }

        @Override
        public DisplayLayer getLayer(DisplayLayerManager manager, DisplayBlockEntity display) {
            if (!this.added) {
                manager.addLayer(this.layer);
                this.added = true;
            }
            return this.layer;
        }

        @Override
        public boolean removeLayer(DisplayLayer layer) {
            // We have only one layer, so we directly return true to delete this group.
            return true;
        }

    }

}
