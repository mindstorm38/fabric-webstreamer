package fr.theorozier.webstreamer.source;

import java.net.URL;
import java.util.HashMap;

public class Sources {

    private final HashMap<URL, Source> sources = new HashMap<>();
    private int idCounter = 0;

    public Source getSource(URL url) {
        return this.sources.computeIfAbsent(url, key -> new Source(this.idCounter++, key));
    }

}
