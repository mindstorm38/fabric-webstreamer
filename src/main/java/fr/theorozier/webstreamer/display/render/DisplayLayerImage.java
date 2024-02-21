package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.platform.TextureUtil;
import fr.theorozier.webstreamer.WebStreamerMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Environment(EnvType.CLIENT)
public class DisplayLayerImage extends DisplayLayer {
	
	private static final long FAILING_IMAGE_REQUEST_INTERVAL = 30L * 1000000000L;
	
	private long imageNextRequestTimestamp = 0;
	private boolean imageUploaded = false;
	private Future<STBLoadedImage> futureImage;
	
	public DisplayLayerImage(URI uri, DisplayLayerResources res) {
		super(uri, res);
	}
	
	@Override
	protected void tick() {
		
		long now = System.nanoTime();
		
		if (this.futureImage == null) {
			if (!this.imageUploaded && now >= this.imageNextRequestTimestamp) {
				this.futureImage = this.res.getExecutor().submit(this::requestImageBlocking);
			}
		} else if (this.futureImage.isDone()) {
			
			STBLoadedImage img = null;
			
			try {
				
				img = this.futureImage.get();
				
				WebStreamerMod.LOGGER.info(makeLog("Uploading image... (channels: {}, pixel size: {})"), img.channels, img.buffer.remaining() / (img.width * img.height));
				
				this.tex.uploadRaw(img.buffer, GL11.GL_RGBA, img.width, img.height, img.width, GL11.GL_RGBA, 4);
				this.imageUploaded = true;
				
			} catch (InterruptedException | CancellationException e) {
				// Should not happen
			} catch (ExecutionException e) {
				WebStreamerMod.LOGGER.error(makeLog("Failed to request image, retrying in {} seconds."), FAILING_IMAGE_REQUEST_INTERVAL / 1000000000, e.getCause());
				this.imageNextRequestTimestamp = now + FAILING_IMAGE_REQUEST_INTERVAL;
			} finally {
				this.futureImage = null;
				if (img != null) {
					img.free();
				}
			}
			
		}
		
	}

	protected STBLoadedImage requestImageBlocking() throws IOException {
		try {
			HttpRequest request = HttpRequest.newBuilder(this.uri).GET().timeout(Duration.ofSeconds(5)).build();
			HttpResponse<InputStream> res = this.res.getHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (res.statusCode() == 200) {
				return this.readImageBlocking(res.body());
			} else {
				throw new IOException("HTTP request failed, status code: " + res.statusCode());
			}
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}

	protected STBLoadedImage readImageBlocking(InputStream stream) throws IOException {
		ByteBuffer buf = null;
		try {
			buf = TextureUtil.readResource(stream);
			buf.rewind();
			try (MemoryStack memoryStack = MemoryStack.stackPush()){
				IntBuffer width = memoryStack.mallocInt(1);
				IntBuffer height = memoryStack.mallocInt(1);
				IntBuffer channels = memoryStack.mallocInt(1);
				ByteBuffer stbBuf = STBImage.stbi_load_from_memory(buf, width, height, channels, STBImage.STBI_rgb_alpha);
				if (stbBuf == null) {
					throw new IOException("Could not load image: " + STBImage.stbi_failure_reason());
				}
				return new STBLoadedImage(stbBuf, width.get(0), height.get(0), channels.get(0));
			}
		} finally {
			MemoryUtil.memFree(buf);
			IOUtils.closeQuietly(stream);
		}
	}

	protected record STBLoadedImage(ByteBuffer buffer, int width, int height, int channels) {

		private void free() {
			STBImage.stbi_image_free(this.buffer);
		}
		
	}
	
}
