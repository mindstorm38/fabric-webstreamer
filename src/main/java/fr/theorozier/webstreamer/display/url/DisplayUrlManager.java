package fr.theorozier.webstreamer.display.url;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.net.URI;

@Environment(EnvType.CLIENT)
public class DisplayUrlManager {

    private final Object2IntArrayMap<URI> urlCache = new Object2IntArrayMap<>();
    private int counter = 0;

    public DisplayUrl allocUri(URI uri) {
        return new DisplayUrl(uri, this.urlCache.computeIfAbsent(uri, key -> ++counter));
    }

}
