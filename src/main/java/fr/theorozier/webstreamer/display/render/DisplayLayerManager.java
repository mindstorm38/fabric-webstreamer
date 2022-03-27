package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.source.Source;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class is responsible for caching and keeping the number of layer to the minimum.
 */
public class DisplayLayerManager {

    private final Int2ObjectArrayMap<DisplayLayer> layerCache = new Int2ObjectArrayMap<>();
    
    private final ExecutorService executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
        
        private final AtomicInteger counter = new AtomicInteger();
        
        @Override
        public Thread newThread(@NotNull Runnable r) {
            return new Thread(r, "WebStream Display IO (" + this.counter.getAndIncrement() + ")");
        }
        
    });
    
    public DisplayLayer forSource(Source source) {
        return layerCache.computeIfAbsent(source.getId(), key -> new DisplayLayer(this, source));
    }
    
    /**
     * Tick all active display layers.
     */
    public void tickDisplays() {
        this.layerCache.values().forEach(DisplayLayer::tickDisplay);
    }
    
    public ExecutorService getExecutor() {
        return executor;
    }
    
}
