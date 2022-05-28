package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.WebStreamerClientMod;
import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.url.DisplayUrl;
import fr.theorozier.webstreamer.display.source.DisplaySource;
import fr.theorozier.webstreamer.util.AsyncProcessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.net.URI;
import java.util.concurrent.ExecutorService;

@Environment(EnvType.CLIENT)
public class DisplayRenderData {

	private final DisplayBlockEntity display;
	
	private DisplayUrl url;
	
	private final AsyncProcessor<DisplaySource, URI, Exception> asyncUrl = new AsyncProcessor<>(DisplaySource::getUri);
	
	private float lastWidth = 0f;
	private float lastHeight = 0f;
	
	private float widthOffset = 0f;
	private float heightOffset = 0f;
	
	public DisplayRenderData(DisplayBlockEntity display) {
		this.display = display;
	}
	
	/**
	 * Get the URL of a specific display.
	 * @param executor The executor to execute async code on.
	 * @return Return a non-null URL when loaded.
	 */
	public DisplayUrl getUrl(ExecutorService executor) {
		
		// This call will only actually push if the source differs from the last one pushed.
		this.asyncUrl.push(this.display.getSource());
		
		this.asyncUrl.fetch(executor, uri -> {
			if (uri != null) {
				this.url = WebStreamerClientMod.DISPLAY_URLS.allocUri(uri);
				WebStreamerMod.LOGGER.info(this.display.makeLog("Allocated a new display url {}."), this.url);
			} else {
				this.url = null;
				WebStreamerMod.LOGGER.info(this.display.makeLog("No URI found for the display."));
			}
		}, e -> {
			this.url = null;
			WebStreamerMod.LOGGER.warn(this.display.makeLog("Unhandled error while getting source uri."), e);
		});
		
		return this.url;
		
	}
	
	/**
	 * Force reset the internal URL and try to re-fetch it.
	 * Especially important to Twitch sources to allow re-fetching old playlist URLs.
	 */
	public void resetUrl() {
		this.display.getSource().resetUri();
		this.asyncUrl.reset();
		this.url = null;
	}
	
	public float getWidthOffset() {
		float width = this.display.getWidth();
		if (width != this.lastWidth) {
			this.lastWidth = width;
			this.widthOffset = this.display.calcWidthOffset();
		}
		return widthOffset;
	}
	
	public float getHeightOffset() {
		float height = this.display.getHeight();
		if (height != this.lastHeight) {
			this.lastHeight = height;
			this.heightOffset = this.display.calcHeightOffset();
		}
		return heightOffset;
	}

}
