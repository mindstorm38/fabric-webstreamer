package fr.theorozier.webstreamer.display.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.lwjgl.openal.AL10;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * A specialized frame grabber for displays.
 */
@Environment(EnvType.CLIENT)
public class FrameGrabber {

	private final DisplayLayerResources pools;
	private final URI uri;
	
	private ByteBuffer buffer;
	private FFmpegFrameGrabber grabber;
	private long refTimestamp;
	private long deltaTimestamp;
	private Frame lastFrame;

	private ShortBuffer tempAudioBuffer;
	
	/**
	 * Buffers for OpenAL audio buffers to play.
	 * Buffers are in fact sorted in this queue because they are pushed while iterating
	 * grabbed frames, and audio frames are assumed to be sorted, as well as image frames.
	 */
	private final ArrayDeque<TimedAudioBuffer> audioBuffers = new ArrayDeque<>();

	/** Internal timestamped audio buffers, temporarily stored into the grabber. */
	private record TimedAudioBuffer(int alBufferId, long timestamp) {
		/** If the audio buffer is never requested for upload, we need to delete it here. */
		void delete() {
			AL10.alDeleteBuffers(this.alBufferId);
		}
	}
	
	public FrameGrabber(DisplayLayerResources pools, URI uri) {
		this.pools = pools;
		this.uri = uri;
	}

	public void start() throws IOException {

		if (this.grabber != null || this.buffer != null) {
			throw new IllegalStateException("already started");
		}
		
		try {

			HttpRequest req = HttpRequest.newBuilder(this.uri).GET().timeout(Duration.ofSeconds(1)).build();
			this.buffer = this.pools.allocRawFileBuffer();
			this.pools.getHttpClient().send(req, info -> new BufferResponseSubscriber(this.buffer));
			ByteArrayInputStream grabberStream = new ByteArrayInputStream(this.buffer.array(), this.buffer.position(), this.buffer.remaining());

			this.grabber = new FFmpegFrameGrabber(grabberStream);
			this.grabber.startUnsafe();

			this.tempAudioBuffer = this.pools.allocSoundBuffer();

			this.refTimestamp = 0L;
			this.deltaTimestamp = 0L;
			this.lastFrame = null;

			Frame frame;
			while ((frame = this.grabber.grab()) != null) {
				if (frame.image != null) {
					this.refTimestamp = frame.timestamp;
					this.lastFrame = frame;
					this.lastFrame.timestamp = 0L;
					break;
				} else if (frame.samples != null) {
					// It is intentional for audio frames to keep their real timestamp
					// because we usually get audio frames before the first image frame
					// and therefor we can't know the relative timestamp.
					this.pushAudioBuffer(frame);
				}
			}

		} catch (IOException | InterruptedException | RuntimeException e) {

			if (this.grabber != null) {
				this.grabber.releaseUnsafe();
			}

			if (this.buffer != null) {
				this.pools.freeRawFileBuffer(this.buffer);
				this.buffer = null;
			}

			if (this.tempAudioBuffer != null) {
				this.pools.freeSoundBuffer(this.tempAudioBuffer);
				this.tempAudioBuffer = null;
			}

			if (e instanceof InterruptedException) {
				throw new IOException(e);
			} else if (e instanceof IOException) {
				throw (IOException) e;
			} else {
				throw (RuntimeException) e;
			}

		}

	}

	public void stop() {

		if (this.grabber == null || this.buffer == null || this.tempAudioBuffer == null) {
			throw new IllegalStateException();
		}
		
		try {
			this.grabber.releaseUnsafe();
		} catch (IOException ignored) { }
		
		this.pools.freeRawFileBuffer(this.buffer);
		this.pools.freeSoundBuffer(this.tempAudioBuffer);
		
		this.buffer = null;
		this.grabber = null;
		this.tempAudioBuffer = null;

		while (!this.audioBuffers.isEmpty()) {
			this.audioBuffers.poll().delete();
		}

	}

	/**
	 * Grab the image frame at the corresponding timestamp, the grabber will attempt
	 * to get the closest frame before timestamp.
	 * @param timestamp The timestamp in microseconds.
	 * @return The grabbed frame or null if frame has not updated since last grab.
	 */
	public Frame grabAt(long timestamp) throws IOException {

		if (this.lastFrame != null) {
			if (this.lastFrame.timestamp <= timestamp) {
				Frame frame = this.lastFrame;
				this.lastFrame = null;
				return frame;
			} else {
				return null;
			}
		}

		Frame frame;
		while ((frame = this.grabber.grab()) != null) {
			if (frame.image != null) {

				// Only image frames sees their timestamp modified.
				// Audio frame keep their real timestamp.
				frame.timestamp -= this.refTimestamp;

				if (this.deltaTimestamp == 0) {
					this.deltaTimestamp = frame.timestamp;
				}

				if (frame.timestamp <= timestamp) {
					// Delta of the current frame with the targeted timestamp
					long delta = timestamp - frame.timestamp;
					if (delta <= this.deltaTimestamp) {
						return frame;
					}
				} else {
					this.lastFrame = frame;
					break;
				}

			} else if (frame.samples != null) {
				this.pushAudioBuffer(frame);
			}
		}

		return null;

	}

	public void grabRemaining() throws IOException {
		Frame frame;
		while ((frame = this.grabber.grab()) != null) {
			if (frame.samples != null) {
				this.pushAudioBuffer(frame);
			}
		}
	}

	public void grabAudioAndUpload(DisplaySoundSource source) {
		while (!this.audioBuffers.isEmpty()) {
			source.enqueueRaw(this.audioBuffers.poll().alBufferId);
		}
	}

	/**
	 * Skip and delete all audio buffers that are timestamped before the given timestamp.
	 * @param timestamp The relative reference timestamp.
	 */
	public void skipAudioBufferBefore(long timestamp) {
		long realTimestamp = timestamp + this.refTimestamp;
		// This loop will ultimately break because if we don't break,
		// we remove an element, if when we reach empty queue, it breaks.
		while (!this.audioBuffers.isEmpty()) {
			// In first place we peek (not removing it) to check the timestamp.
			if (this.audioBuffers.peek().timestamp > realTimestamp) {
				break;
			}
			// If before timestamp, we remove and then delete it.
			this.audioBuffers.remove().delete();
		}
	}

	private void pushAudioBuffer(Frame frame) {

		Buffer sample = frame.samples[0];
		
		this.tempAudioBuffer.clear();
		
		if (sample instanceof ByteBuffer sampleByte) {
			int count = sampleByte.remaining();
			for (int i = 0; i < count; i += 2) {
				short sampleLeft = (short) ((int) sampleByte.get(i) << 8);
				short sampleRight = (short) ((int) sampleByte.get(i + 1) << 8);
				this.tempAudioBuffer.put((short) ((sampleLeft + sampleRight) / 2));
			}
		} else if (sample instanceof ShortBuffer sampleShort) {
			int count = sampleShort.remaining();
			for (int i = 0; i < count; i += 2) {
				short sampleLeft = sampleShort.get(i);
				short sampleRight = sampleShort.get(i + 1);
				this.tempAudioBuffer.put((short) (((int) sampleLeft + (int) sampleRight) / 2));
			}
		} else {
			return;
			// throw new IllegalArgumentException("unsupported sample format");
		}
		
		int bufferId = AL10.alGenBuffers();
		this.tempAudioBuffer.flip();
		AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, this.tempAudioBuffer, frame.sampleRate);
		this.audioBuffers.add(new TimedAudioBuffer(bufferId, frame.timestamp));

	}
	
	/**
	 * Internal class that serves as an HTTP response subscriber that fills a given {@link ByteBuffer}.
	 * The buffer is automatically rewinded before pushing data into it.
	 */
	private static class BufferResponseSubscriber implements HttpResponse.BodySubscriber<Object> {
		
		private final CompletableFuture<Object> future = new CompletableFuture<>();
		private final ByteBuffer buffer;
		private Flow.Subscription subscription;
		
		public BufferResponseSubscriber(ByteBuffer buffer) {
			this.buffer = buffer;
		}
		
		@Override
		public CompletionStage<Object> getBody() {
			return this.future;
		}
		
		@Override
		public void onSubscribe(Flow.Subscription subscription) {
			if (this.subscription != null) {
				this.subscription.cancel();
			}
			this.subscription = subscription;
			this.subscription.request(Long.MAX_VALUE);
			this.buffer.clear();
		}
		
		@Override
		public void onNext(List<ByteBuffer> item) {
			for (ByteBuffer buf : item) {
				try {
					this.buffer.put(buf);
				} catch (BufferOverflowException e) {
					System.out.println("pos before crashing: " + this.buffer.position() + ", incoming buf: " + buf.remaining());
					this.future.completeExceptionally(e);
				}
			}
		}
		
		@Override
		public void onError(Throwable throwable) {
			this.future.completeExceptionally(throwable);
		}
		
		@Override
		public void onComplete() {
			this.buffer.flip();
			this.future.complete(null);
		}
		
	}

}
