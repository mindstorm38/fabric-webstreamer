package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.platform.TextureUtil;
import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.display.url.DisplayUrl;
import fr.theorozier.webstreamer.util.AsyncProcessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
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

@Environment(EnvType.CLIENT)
public class DisplayLayerImage extends DisplayLayer {
	
	private static final long FAILING_IMAGE_REQUEST_INTERVAL = 30L * 1000000000L;
	
	private final AsyncProcessor<URI, STBLoadedImage, IOException> asyncImage;
	private long imageNextRequestTimestamp = 0;
	private boolean imageUploaded = false;
	
	public DisplayLayerImage(DisplayUrl url, DisplayLayerResources res) {
		super(url, res);
		this.asyncImage = new AsyncProcessor<>(this::requestImageBlocking, true);
	}
	
	@Override
	protected void tick() {
		
		long now = System.nanoTime();
		
		if (!this.imageUploaded && !this.asyncImage.requested()) {
			if (now >= this.imageNextRequestTimestamp) {
				this.asyncImage.push(this.url.uri());
			}
		}
		
		this.asyncImage.fetch(this.res.getExecutor(), imgBuf -> {
			int format = switch (imgBuf.channels) {
				case 1 -> GL11.GL_RED;
				case 2 -> GL30.GL_RG;
				case 3 -> GL11.GL_RGB;
				case 4 -> GL11.GL_RGBA;
				default -> throw new IllegalStateException();
			};
			this.tex.uploadRaw(imgBuf.buffer, GL11.GL_RGBA8, imgBuf.width, imgBuf.height, imgBuf.width, format);
			imgBuf.free();
			this.imageUploaded = true;
			WebStreamerMod.LOGGER.info(makeLog("Uploaded image..."));
		}, err -> {
			WebStreamerMod.LOGGER.error(makeLog("Failed to request image, retrying in {} seconds."), FAILING_IMAGE_REQUEST_INTERVAL / 1000000000, err);
			this.imageNextRequestTimestamp = now + FAILING_IMAGE_REQUEST_INTERVAL;
		});
		
	}
	
	private STBLoadedImage requestImageBlocking(URI uri) throws IOException {
		try {
			HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(5)).build();
			HttpResponse<InputStream> res = this.res.getHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (res.statusCode() == 200) {
				InputStream stream = res.body();
				ByteBuffer buf = null;
				try {
					buf = TextureUtil.readResource(stream);
					buf.rewind();
					try (MemoryStack memoryStack = MemoryStack.stackPush()){
						IntBuffer width = memoryStack.mallocInt(1);
						IntBuffer height = memoryStack.mallocInt(1);
						IntBuffer channels = memoryStack.mallocInt(1);
						ByteBuffer stbBuf = STBImage.stbi_load_from_memory(buf, width, height, channels, 0);
						if (stbBuf == null) {
							throw new IOException("Could not load image: " + STBImage.stbi_failure_reason());
						}
						return new STBLoadedImage(stbBuf, width.get(0), height.get(0), channels.get(0));
					}
				} finally {
					MemoryUtil.memFree(buf);
					IOUtils.closeQuietly(stream);
				}
			} else {
				throw new IOException("HTTP request failed, status code: " + res.statusCode());
			}
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}
	
	private record STBLoadedImage(ByteBuffer buffer, int width, int height, int channels) {
		
		private void free() {
			STBImage.stbi_image_free(this.buffer);
		}
		
	}
	
}
