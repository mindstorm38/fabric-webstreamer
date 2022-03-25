package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.source.Source;
import io.netty.util.collection.IntObjectHashMap;

public class DisplayLayerManager {

    private final IntObjectHashMap<DisplayLayer> layerCache = new IntObjectHashMap<>();

    public DisplayLayer forSource(Source source) {
        return layerCache.computeIfAbsent(source.getId(), key -> new DisplayLayer(source));
    }

}
