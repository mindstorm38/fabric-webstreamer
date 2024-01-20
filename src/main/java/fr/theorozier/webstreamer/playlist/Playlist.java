package fr.theorozier.webstreamer.playlist;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic playlist data structure that defines different URIs for each quality 
 * available on a channel.
 */
public class Playlist {
		
	private final String channel;
	private final ArrayList<PlaylistQuality> qualities = new ArrayList<>();
	
	public Playlist(@NotNull String channel) {
		this.channel = channel;
	}

	@NotNull
	public String getChannel() {
		return channel;
	}
	
	public void addQuality(PlaylistQuality quality) {
		this.qualities.add(quality);
	}

	@NotNull
	public List<PlaylistQuality> getQualities() {
		return qualities;
	}
	
	public PlaylistQuality getQuality(String quality) {
		return this.qualities.stream().filter(pl -> pl.name().equals(quality)).findFirst().orElse(null);
	}
	
}
