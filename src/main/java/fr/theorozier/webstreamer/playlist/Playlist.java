package fr.theorozier.webstreamer.playlist;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic playlist data structure that defines different URIs for each quality 
 * available on a channel.
 */
public class Playlist {
		
	private final String channel;
	private final ArrayList<PlaylistQuality> qualities = new ArrayList<>();
	
	public Playlist(String channel) {
		this.channel = channel;
	}
	
	public String getChannel() {
		return channel;
	}
	
	public void addQuality(PlaylistQuality quality) {
		this.qualities.add(quality);
	}
	
	public List<PlaylistQuality> getQualities() {
		return qualities;
	}
	
	public PlaylistQuality getQuality(String quality) {
		return this.qualities.stream().filter(pl -> pl.name().equals(quality)).findFirst().orElse(null);
	}
	
}
