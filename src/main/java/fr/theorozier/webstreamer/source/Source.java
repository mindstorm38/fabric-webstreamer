package fr.theorozier.webstreamer.source;

import java.net.MalformedURLException;
import java.net.URL;

public class Source {

    private final int id;
    private final URL url;

    public Source(int id, URL url) {

        this.id = id;
        this.url = url;

    }

    public int getId() {
        return id;
    }
    
    public URL getUrl() {
        return url;
    }
    
    public URL getContextUrl(String path) throws MalformedURLException {
        return new URL(this.url, path);
    }

}
