package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * This class is responsible for caching and keeping the number of layer to the minimum.
 */
@Environment(EnvType.CLIENT)
public class DisplayLayerManager extends DisplayLayerMap<Object> {

    /** Max cost for concurrent layers. */
    private static final int MAX_LAYERS_COST = 20 * 30;  // Approx 20 HLS layers.

    /** Interval of cleanups for unused display layers. */
    private static final long CLEANUP_INTERVAL = 5L * 1000000000L;

    /** Common pools for shared and reusable heavy buffers. */
    private final DisplayLayerResources res = new DisplayLayerResources();

    /** Time in nanoseconds (monotonic) of the last cleanup for unused layers. */
    private long lastCleanup = 0;

    /** Used to handle SVG images. **/
    private final DisplayLayerSVGMap svgMap = new DisplayLayerSVGMap(this.res); //TODO: probably change where this happens.
    
    public DisplayLayerResources getResources() {
        return this.res;
    }

    @Override
    public void tick() {

        RenderSystem.assertOnRenderThread();
        super.tick();

        long now = System.nanoTime();
        if (now - this.lastCleanup >= CLEANUP_INTERVAL) {
            super.cleanup(now); // Super to avoid redundant render thread check.
            this.lastCleanup = now;
        }

    }

    @Override
    public boolean cleanup(long now) {
        RenderSystem.assertOnRenderThread();
        return super.cleanup(now);
    }

    @Override
    @NotNull
    protected Object getLayerKey(Key key) {
        String path = key.uri().getPath();
        if (path.endsWith(".svg")) {
            return svgMap.getLayerKey(key); // we need to call this somewhere.
        }
        return key.uri();
    }

    @Override
    @NotNull
    protected DisplayLayerNode getNewLayer(Key key) throws OutOfLayerException, UnknownFormatException {

        if (this.cost() >= MAX_LAYERS_COST) {
            throw new OutOfLayerException();
        }

        String path = key.uri().getPath();
        if (path != null) {
            if (path.endsWith(".m3u8")) {
                return new DisplayLayerHls(key.uri(), this.res);
            } else if (path.endsWith(".jpeg") || path.endsWith(".jpg") || path.endsWith(".bmp") || path.endsWith(".png")) {
                return new DisplayLayerImage(key.uri(), this.res);
            } else if (path.endsWith(".svg")) {
                return svgMap.getNewLayer(key);
            }
        }

        throw new UnknownFormatException();

    }

}
