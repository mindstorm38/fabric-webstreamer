package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.display.render.DisplayLayer;
import fr.theorozier.webstreamer.display.render.DisplayLayerNode;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * A special, yet abstract, display layer node that contains multiple layers, mapped
 * to a specific key type. Abstract methods must be implemented by subclasses to
 * provide the key and to instantiate a new layer if the key is existing.
 *
 * @param <K> The key to map children nodes to.
 */
public abstract class DisplayLayerMap<K> implements DisplayLayerNode {

    private final HashMap<K, DisplayLayerNode> layers = new HashMap<>();

    @Override
    public void tick() {
        // Simply delegate.
        this.layers.values().forEach(DisplayLayerNode::tick);
    }

    @Override
    public boolean cleanup(long now) {
        this.layers.values().removeIf(layer -> layer.cleanup(now));
        return this.layers.isEmpty();
    }

    @Override
    public int cost() {
        return this.layers.values().stream().mapToInt(DisplayLayerNode::cost).sum();
    }

    @Override
    public DisplayLayer getLayer(Key key) throws OutOfLayerException, UnknownFormatException {
        K layerKey = this.getLayerKey(key);
        DisplayLayerNode layer = this.layers.get(layerKey);
        if (layer == null) {
            layer = this.getNewLayer(key);
            this.layers.put(layerKey, layer);
        }
        return layer.getLayer(key);
    }

    @NotNull
    protected abstract K getLayerKey(Key key);

    @NotNull
    protected abstract DisplayLayerNode getNewLayer(Key key) throws OutOfLayerException, UnknownFormatException;

}
