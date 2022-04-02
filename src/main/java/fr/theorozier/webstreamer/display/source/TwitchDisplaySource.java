package fr.theorozier.webstreamer.display.source;

import fr.theorozier.webstreamer.playlist.PlaylistQuality;
import net.minecraft.nbt.NbtCompound;

import java.net.*;

public class TwitchDisplaySource implements DisplaySource {

    private String channel;
    private PlaylistQuality quality;

    public void setChannelQuality(String channel, PlaylistQuality quality) {
        this.channel = channel;
        this.quality = quality;
    }

    public void clearChannelQuality() {
        this.channel = null;
        this.quality = null;
    }

    public String getChannel() {
        return channel;
    }

    @Override
    public URL getUrl() {
        return this.quality.url();
    }
    
    @Override
    public void writeNbt(NbtCompound nbt) {
    
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
    
    }

}
