package fr.theorozier.webstreamer.display.source;

import fr.theorozier.webstreamer.playlist.PlaylistQuality;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;

import java.net.*;

public class TwitchDisplaySource implements DisplaySource {
    
    public static final String TYPE = "twitch";

    private String channel;
    private PlaylistQuality quality;

    public TwitchDisplaySource(TwitchDisplaySource copy) {
        this.channel = copy.channel;
        this.quality = copy.quality;
    }
    
    public TwitchDisplaySource() { }
    
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
    
    public PlaylistQuality getQuality() {
        return quality;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public URL getUrl() {
        return this.quality == null ? null : this.quality.url();
    }
    
    @Override
    public void writeNbt(NbtCompound nbt) {
        if (this.channel != null && this.quality != null) {
            nbt.putString("channel", this.channel);
            nbt.putString("quality", this.quality.name());
            nbt.putString("url", this.quality.url().toString());
        }
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        if (
            nbt.get("channel") instanceof NbtString channel &&
            nbt.get("quality") instanceof NbtString quality &&
            nbt.get("url") instanceof NbtString urlRaw
        ) {
            try {
                URL url = new URL(urlRaw.asString());
                this.channel = channel.asString();
                this.quality = new PlaylistQuality(quality.asString(), url);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

}
