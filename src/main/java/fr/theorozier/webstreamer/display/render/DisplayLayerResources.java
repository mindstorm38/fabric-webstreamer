package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.WebStreamerMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Different pool types given to {@link DisplayLayer} as a centralized way of getting
 * access to heavy heap buffers. This also provides a thread pool executor and an HTTP
 * client in order to reduce overhead when creating them.
 */
@Environment(EnvType.CLIENT)
public class DisplayLayerResources {
	
	private final ExecutorService executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
		private final AtomicInteger counter = new AtomicInteger();
		@Override
		public Thread newThread(@NotNull Runnable r) {
			return new Thread(r, "WebStreamer Display Queue (" + this.counter.getAndIncrement() + ")");
		}
	});
	
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final List<ByteBuffer> rawFileBuffers = new ArrayList<>();
	private final List<ShortBuffer> soundBuffers = new ArrayList<>();
	
	private int rawFileBuffersCount = 0;
	private int soundBuffersCount = 0;
	
	public ExecutorService getExecutor() {
		return this.executor;
	}
	
	public HttpClient getHttpClient() {
		return this.httpClient;
	}
	
	/**
	 * Allocate an image buffer. Such buffers are backed by arrays to allow to
	 * create a {@link java.io.ByteArrayInputStream} from it.
	 */
	public ByteBuffer allocRawFileBuffer() {
		synchronized (this.rawFileBuffers) {
			try {
				return this.rawFileBuffers.remove(this.rawFileBuffers.size() - 1);
			} catch (IndexOutOfBoundsException e) {
				this.rawFileBuffersCount++;
				WebStreamerMod.LOGGER.debug("Number of allocated raw file buffers: {}", this.rawFileBuffersCount);
				// 4 Mio buffer for pre-storing whole TransportStream file.
				return ByteBuffer.allocate(1 << 22);
			}
		}
	}
	
	public void freeRawFileBuffer(ByteBuffer buffer) {
		synchronized (this.rawFileBuffers) {
			this.rawFileBuffers.add(buffer);
		}
	}
	
	/**
	 * Allocate a sound buffer. Such buffers are backed by a native memory in
	 * order to be directly used as OpenAL buffer data.
	 */
	public ShortBuffer allocSoundBuffer() {
		synchronized (this.soundBuffers) {
			try {
				return this.soundBuffers.remove(this.soundBuffers.size() - 1);
			} catch (IndexOutOfBoundsException e) {
				this.soundBuffersCount++;
				WebStreamerMod.LOGGER.debug("Number of allocated sound buffers: {}", this.soundBuffersCount);
				// 8 Kio buffer for converting stereo to mono audio stream.
				return ByteBuffer.allocateDirect(8192).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
			}
		}
	}
	
	public void freeSoundBuffer(ShortBuffer buffer) {
		synchronized (this.soundBuffers) {
			this.soundBuffers.add(buffer);
		}
	}
	
}
