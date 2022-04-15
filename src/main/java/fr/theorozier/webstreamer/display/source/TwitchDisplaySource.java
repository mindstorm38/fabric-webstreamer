package fr.theorozier.webstreamer.display.source;

import fr.theorozier.webstreamer.WebStreamerClientMod;
import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.playlist.Playlist;
import fr.theorozier.webstreamer.playlist.PlaylistQuality;
import fr.theorozier.webstreamer.twitch.TwitchClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;

import java.net.*;

public class TwitchDisplaySource implements DisplaySource {
    
    public static final String TYPE = "twitch";

    private String channel;
    private String quality;
    // private PlaylistQuality quality;

    public TwitchDisplaySource(TwitchDisplaySource copy) {
        this.channel = copy.channel;
        this.quality = copy.quality;
        // this.quality = copy.quality;
    }
    
    public TwitchDisplaySource() { }
    
    public void setChannelQuality(String channel, String quality) {
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
    
    public String getQuality() {
        return quality;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public URI getUri() {
        if (this.channel != null && this.quality != null) {
            try {
                Playlist playlist = WebStreamerClientMod.TWITCH_CLIENT.requestPlaylist(this.channel);
                PlaylistQuality quality = playlist.getQuality(this.quality);
                return quality.uri();
            } catch (TwitchClient.PlaylistException e) {
                WebStreamerMod.LOGGER.error("Failed to request twitch channel", e);
            }
        }
        return null;
    }
    
    @Override
    public void writeNbt(NbtCompound nbt) {
        if (this.channel != null && this.quality != null) {
            nbt.putString("channel", this.channel);
            nbt.putString("quality", this.quality);
            //nbt.putString("url", this.quality.uri().toString());
        }
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        if (
            nbt.get("channel") instanceof NbtString channel &&
            nbt.get("quality") instanceof NbtString quality /*&&
            nbt.get("url") instanceof NbtString urlRaw*/
        ) {
            try {
                // URI uri = URI.create(urlRaw.asString());
                this.channel = channel.asString();
                this.quality = quality.asString();
                // his.quality = new PlaylistQuality(quality.asString(), uri);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

}
