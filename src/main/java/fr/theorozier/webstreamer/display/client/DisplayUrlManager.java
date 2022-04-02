package fr.theorozier.webstreamer.display.client;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.net.URL;

@Environment(EnvType.CLIENT)
public class DisplayUrlManager {

    private final Object2IntArrayMap<URL> urlCache = new Object2IntArrayMap<>();
    private int counter = 0;

    public int allocUrl(URL url) {
        return this.urlCache.computeIfAbsent(url, key -> ++counter);
    }

}
