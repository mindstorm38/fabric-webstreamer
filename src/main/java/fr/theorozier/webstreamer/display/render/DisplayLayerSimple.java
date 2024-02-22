package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.theorozier.webstreamer.WebStreamerMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3i;
import org.lwjgl.opengl.GL11;

import java.net.URI;

/**
 * Concrete implementation of a display layer that actually provides a render layer
 * and method to push audio sources.
 */
@Environment(EnvType.CLIENT)
public abstract class DisplayLayerSimple implements DisplayLayerNode, DisplayLayer {
	
	/** The timeout for a layer to be considered unused */
	protected static final long LAYER_UNUSED_TIMEOUT = 15L * 1000000000L;
	
	// Common //
	protected final URI uri;
	protected final DisplayLayerResources res;
	protected final DisplayTexture tex;
	private final DisplayRenderLayer renderLayer;
	
	// Timing //
	/** Time in nanoseconds (monotonic) of the last use. */
	protected long lastUse = 0;
	
	public DisplayLayerSimple(URI uri, DisplayLayerResources res) {
		this.uri = uri;
		this.res = res;
		this.tex = new DisplayTexture();
		this.renderLayer = new DisplayRenderLayer(this);
		WebStreamerMod.LOGGER.info(makeLog("Allocate display layer for {}"), this.uri);
	}

	@Override
	public abstract void tick();

	@Override
	public boolean cleanup(long now) {
		if (now == 0 || now - this.lastUse >= LAYER_UNUSED_TIMEOUT) {
			WebStreamerMod.LOGGER.info(makeLog("Free display layer for {}"), this.uri);
			this.tex.clearGlId();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public abstract int cost();

	@Override
	public DisplayLayer getLayer(Key key) {
		return this;
	}

	@Override
	public void pushAudioSource(Vec3i pos, float dist, float audioDistance, float audioVolume) { }

	@Override
	public boolean isLost() {
		return false;
	}

	@Override
	public RenderLayer getRenderLayer() {
		return this.renderLayer;
	}

	/**
	 * Internal function to prepare a log message for this specific layer.
	 * @param message The message format to append to the header.
	 * @return The log message ready to format.
	 */
	protected String makeLog(String message) {
		return String.format("[%s:%08X] ", this.getClass().getSimpleName(), this.uri.hashCode()) + message;
	}

	private static class DisplayRenderLayer extends RenderLayer {
		private DisplayRenderLayer(DisplayLayerSimple layer) {
			super("display", VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS,
					256, false, true,
					() -> {
						layer.lastUse = System.nanoTime();
						RenderPhase.POSITION_TEXTURE_PROGRAM.startDrawing();
						RenderSystem.enableDepthTest();
						RenderSystem.depthFunc(GL11.GL_LEQUAL);
						RenderSystem.setShaderTexture(0, layer.tex.getGlId());
					},
					RenderSystem::disableDepthTest);
		}
	}
	
}
