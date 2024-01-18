package fr.theorozier.webstreamer.display.url;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.net.URI;

/**
 * A display URI maps an URI that was returned from a source to a unique ID.
 */
@Environment(EnvType.CLIENT)
public record DisplayUrl(URI uri, int id) {

    public URI getContextUri(String path) {
        return this.uri.resolve(path);
    }
    
    @Override
    public String toString() {
        return this.id + "#" + this.uri;
    }
    
}
