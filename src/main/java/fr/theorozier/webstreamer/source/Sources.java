package fr.theorozier.webstreamer.source;

import java.util.HashMap;

public class Sources {

    private final HashMap<String, Source> sources = new HashMap<>();
    private int idCounter = 0;

    public Source getSource(String url) {
        return this.sources.computeIfAbsent(url, key -> new Source(this.idCounter++, key));
    }

}
