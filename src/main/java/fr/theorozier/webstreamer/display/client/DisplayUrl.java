package fr.theorozier.webstreamer.display.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.net.MalformedURLException;
import java.net.URL;

@Environment(EnvType.CLIENT)
public record DisplayUrl(URL url, int id) {

    public URL getContextUrl(String path) throws MalformedURLException {
        return new URL(this.url, path);
    }

}
