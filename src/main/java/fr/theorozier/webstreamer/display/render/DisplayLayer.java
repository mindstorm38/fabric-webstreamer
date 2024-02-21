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

@Environment(EnvType.CLIENT)
public abstract class DisplayLayer {
	
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
	
	public DisplayLayer(URI uri, DisplayLayerResources res) {
		this.uri = uri;
		this.res = res;
		this.tex = new DisplayTexture();
		this.renderLayer = new DisplayRenderLayer(this);
		WebStreamerMod.LOGGER.info(makeLog("Allocate display layer for {}"), this.uri);
	}
	
	/** Called when the display layer is being freed, before garbage collection. */
	protected void free() {
		WebStreamerMod.LOGGER.info(makeLog("Free display layer for {}"), this.uri);
		this.tex.clearGlId();
	}
	
	/** Called on each reader tick. */
	protected abstract void tick();
	
	/** Called for each display position and configuration. */
	@SuppressWarnings("unused")
	public void pushAudioSource(Vec3i pos, float dist, float audioDistance, float audioVolume) { }
	
	/**
	 * Check if this layer is unused for too long, in such case
	 * @param now The reference timestamp (monotonic nanoseconds from {@link System#nanoTime()}).
	 * @return True if the layer is unused for too long.
	 */
	public boolean isUnused(long now) {
		return now - this.lastUse >= LAYER_UNUSED_TIMEOUT;
	}
	
	/**
	 * @return True if this layer should be lost while currently used.
	 */
	public boolean isLost() {
		return false;
	}
	
	protected String makeLog(String message) {
		return String.format("[%s:%08X] ", this.getClass().getSimpleName(), this.uri.hashCode()) + message;
	}
	
	public RenderLayer getRenderLayer() {
		return this.renderLayer;
	}
	
	private static class DisplayRenderLayer extends RenderLayer {
		private DisplayRenderLayer(DisplayLayer layer) {
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
