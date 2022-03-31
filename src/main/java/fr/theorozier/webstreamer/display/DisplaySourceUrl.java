package fr.theorozier.webstreamer.display;

import java.net.MalformedURLException;
import java.net.URL;

public record DisplaySourceUrl(URL url, int id) {

    public URL getContextUrl(String path) throws MalformedURLException {
        return new URL(this.url, path);
    }

}
