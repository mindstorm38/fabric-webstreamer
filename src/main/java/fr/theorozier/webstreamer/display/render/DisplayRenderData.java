package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.WebStreamerClientMod;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.client.DisplayUrl;
import fr.theorozier.webstreamer.display.source.DisplaySource;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.net.URL;

@Environment(EnvType.CLIENT)
public class DisplayRenderData {

	private final DisplayBlockEntity display;
	
	private DisplaySource lastSource;
	private DisplayUrl url;
	
	private float lastWidth = 0f;
	private float lastHeight = 0f;
	
	private float widthOffset = 0f;
	private float heightOffset = 0f;
	
	public DisplayRenderData(DisplayBlockEntity display) {
		this.display = display;
	}
	
	public DisplayUrl getUrl() {
		DisplaySource source = this.display.getSource();
		if (source != this.lastSource) {
			this.lastSource = source;
			this.url = null;
			URL rawUrl = source.getUrl();
			if (rawUrl != null) {
				this.url = new DisplayUrl(rawUrl, WebStreamerClientMod.DISPLAY_URLS.allocUrl(rawUrl));
			}
		}
		return this.url;
	}
	
	public float getWidthOffset() {
		float width = this.display.getWidth();
		if (width != this.lastWidth) {
			this.lastWidth = width;
			this.widthOffset = (this.display.getWidth() - 1) / -2f;
		}
		return widthOffset;
	}
	
	public float getHeightOffset() {
		float height = this.display.getHeight();
		if (height != this.lastHeight) {
			this.lastHeight = height;
			this.heightOffset = (this.display.getHeight() - 1) / -2f;
		}
		return heightOffset;
	}

}
