package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.source.DisplaySource;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * <p>A display render data is an extension added to the {@link DisplayBlockEntity} class
 * only for client side, it's used to asynchronously request the URI from
 * {@link DisplaySource#getUri()} of the block entity (because that method is blocking).
 * This allows non-blocking request of the URL from the display renderer.
 * </p>
 */
@Environment(EnvType.CLIENT)
public class DisplayRenderData {

	private final DisplayBlockEntity display;
	
	private boolean sourceDirty;
	private Future<URI> futureUri;
	private URI uri;
	
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
	 * Get the URL of a specific display. This method must be called from 
	 * {@link DisplayBlockEntityRenderer} only in the render thread.
	 * 
	 * @param executor The executor to execute async code on.
	 * @return Return a non-null URL when loaded.
	 */
	public URI getUri(ExecutorService executor) {
		
		if (this.sourceDirty) {
			this.uri = null;
			this.futureUri = executor.submit(() -> this.display.getSource().getUri());
			this.sourceDirty = false;
		}
		
		if (this.futureUri != null && this.futureUri.isDone()) {
			try {
				this.uri = this.futureUri.get();
				if (this.uri == null) {
					WebStreamerMod.LOGGER.info(this.display.makeLog("Caching no display URI."));
				} else {
					WebStreamerMod.LOGGER.info(this.display.makeLog("Caching display URI: {}"), this.uri);
				}
			} catch (InterruptedException | CancellationException e) {
				// Cancel should not happen.
			} catch (ExecutionException e) {
				WebStreamerMod.LOGGER.warn(this.display.makeLog("Error caching display URI."), e);
			} finally {
				this.futureUri = null;
			}
		}
		
		return this.uri;
		
	}

}
