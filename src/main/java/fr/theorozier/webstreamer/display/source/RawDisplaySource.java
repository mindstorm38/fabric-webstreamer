package fr.theorozier.webstreamer.display.source;

import net.minecraft.nbt.NbtCompound;

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
    
    @Override
    public void writeNbt(NbtCompound nbt) {
    
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
    
    }
    
}
