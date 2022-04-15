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
	
	/** 8 Mio buffer for pre-storing whole TransportStream file. */
	private static final int RAW_FILE_BUFFER_SIZE = 1 << 23;
	/** Limit to 256 Mio of raw file buffers. */
	private static final int RAW_FILE_BUFFER_LIMIT = 16;
	/** 8 Kio buffer for converting (16 or 8 bits) stereo to mono 16 bits audio stream. */
	private static final int AUDIO_BUFFER_SIZE = 8192;
	/** Limit to 512 Kio of audio buffers. */
	private static final int AUDIO_BUFFER_LIMIT = 32;
	
	private final ExecutorService executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
		private final AtomicInteger counter = new AtomicInteger();
		@Override
		public Thread newThread(@NotNull Runnable r) {
			return new Thread(r, "WebStreamer Display Queue (" + this.counter.getAndIncrement() + ")");
		}
	});
	
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final List<ByteBuffer> rawFileBuffers = new ArrayList<>();
	private final List<ShortBuffer> audioBuffers = new ArrayList<>();
	
	private int rawFileBuffersCount = 0;
	private int audioBuffersCount = 0;
	
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
				if (this.rawFileBuffersCount >= RAW_FILE_BUFFER_LIMIT) {
					throw new IllegalStateException("reached maximum number of allocated raw file buffers: " + RAW_FILE_BUFFER_LIMIT);
				}
				this.rawFileBuffersCount++;
				WebStreamerMod.LOGGER.debug("Number of allocated raw file buffers: {}", this.rawFileBuffersCount);
				return ByteBuffer.allocate(RAW_FILE_BUFFER_SIZE);
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
	public ShortBuffer allocAudioBuffer() {
		synchronized (this.audioBuffers) {
			try {
				return this.audioBuffers.remove(this.audioBuffers.size() - 1);
			} catch (IndexOutOfBoundsException e) {
				if (this.audioBuffersCount >= AUDIO_BUFFER_LIMIT) {
					throw new IllegalStateException("reached maximum number of allocated audio buffers: " + AUDIO_BUFFER_LIMIT);
				}
				this.audioBuffersCount++;
				WebStreamerMod.LOGGER.debug("Number of allocated sound buffers: {}", this.audioBuffersCount);
				return ByteBuffer.allocateDirect(AUDIO_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
			}
		}
	}
	
	public void freeAudioBuffer(ShortBuffer buffer) {
		synchronized (this.audioBuffers) {
			this.audioBuffers.add(buffer);
		}
	}
	
}
