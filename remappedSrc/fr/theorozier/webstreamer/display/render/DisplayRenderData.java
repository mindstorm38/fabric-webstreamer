package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.WebStreamerClientMod;
import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.url.DisplayUrl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Environment(EnvType.CLIENT)
public class DisplayRenderData {

	private final DisplayBlockEntity display;
	
	private boolean sourceDirty;
	private Future<URI> futureUrl;
	private DisplayUrl url;
	
	private float lastWidth = 0f;
	private float lastHeight = 0f;
	
	private float widthOffset = 0f;
	private float heightOffset = 0f;
	
	public DisplayRenderData(DisplayBlockEntity display) {
		this.display = display;
		this.sourceDirty = true;
	}
	
	/**
	 * Mark the render data dirty. This will force the internal URL to be updated.
	 * This is called only from {@link DisplayBlockEntity}.
	 */
	public void markSourceDirty() {
		this.sourceDirty = true;
	}
	
	/**
	 * Get the URL of a specific display. This method must be called from {@link DisplayBlockEntityRenderer} only
	 * in the render thread.
	 * @param executor The executor to execute async code on.
	 * @return Return a non-null URL when loaded.
	 */
	public DisplayUrl getUrl(ExecutorService executor) {
		
		if (this.sourceDirty) {
			this.url = null;
			this.futureUrl = executor.submit(() -> this.display.getSource().getUri());
			this.sourceDirty = false;
		}
		
		if (this.futureUrl != null && this.futureUrl.isDone()) {
			try {
				URI uri = this.futureUrl.get();
				if (uri == null) {
					WebStreamerMod.LOGGER.info(this.display.makeLog("No URI found for the display."));
				} else {
					this.url = WebStreamerClientMod.DISPLAY_URLS.allocUri(uri);
					WebStreamerMod.LOGGER.info(this.display.makeLog("Allocated a new display url {}."), this.url);
				}
			} catch (InterruptedException | CancellationException e) {
				// Cancel should not happen.
			} catch (ExecutionException e) {
				WebStreamerMod.LOGGER.warn(this.display.makeLog("Unhandled error while getting source uri."), e);
			} finally {
				this.futureUrl = null;
			}
		}
		
		return this.url;
		
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
