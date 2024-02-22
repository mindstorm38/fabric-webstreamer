package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.HashMap;

public abstract class DisplayLayerMap<K> implements DisplayLayer {

    private final HashMap<K, DisplayLayer> layers = new HashMap<>();

    @Override
    public void tick() {
        // Simply delegate.
        this.layers.values().forEach(DisplayLayer::tick);
    }

    @Override
    public boolean cleanup(long now) {
        this.layers.values().removeIf(layer -> layer.cleanup(now));
        return this.layers.isEmpty();
    }

    @Override
    public int cost() {
        return this.layers.values().stream().mapToInt(DisplayLayer::cost).sum();
    }

    @Override
    public DisplayLayerRender getRender(URI uri, DisplayBlockEntity display) throws OutOfLayerException, UnknownFormatException {
        K key = this.getKey(uri, display);
        DisplayLayer layer = this.layers.get(key);
        if (layer == null) {
            layer = this.getNewLayer(uri, display);
            this.layers.put(key, layer);
        }
        return layer.getRender(uri, display);
    }

    @NotNull
    protected abstract K getKey(URI uri, DisplayBlockEntity display);

    @NotNull
    protected abstract DisplayLayer getNewLayer(URI uri, DisplayBlockEntity display) throws OutOfLayerException, UnknownFormatException;

}
