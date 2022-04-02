package fr.theorozier.webstreamer.display.source;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;

import java.net.MalformedURLException;
import java.net.URL;

public class RawDisplaySource implements DisplaySource {

    public static final String TYPE = "raw";
    
    private URL url;

    public RawDisplaySource(RawDisplaySource copy) {
        this.url = copy.url;
    }
    
    public RawDisplaySource() { }
    
    public void setUrl(URL url) {
        this.url = url;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public URL getUrl() {
        return this.url;
    }
    
    @Override
    public void writeNbt(NbtCompound nbt) {
        if (this.url != null) {
            nbt.putString("url", this.url.toString());
        }
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        if (nbt.get("url") instanceof NbtString nbtRaw) {
            try {
                this.url = new URL(nbtRaw.asString());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }
    
}
