package fr.theorozier.webstreamer.display.source;

import java.net.URL;

public class RawDisplaySource implements DisplaySource {

    private URL url;

    public void setUrl(URL url) {
        this.url = url;
    }

    @Override
    public URL getUrl() {
        return this.url;
    }

}
