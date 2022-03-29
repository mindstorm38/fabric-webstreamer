package fr.theorozier.webstreamer.display.render;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.lwjgl.openal.AL10;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * A specialized frame grabber for displays.
 */
public class FrameGrabber {

	private final FFmpegFrameGrabber grabber;
	private long refTimestamp;
	private long deltaTimestamp;
	private Frame lastFrame;

	/** Buffers for OpenAL audio buffers to play. */
	private final ArrayDeque<TimedAudioBuffer> audioBuffers = new ArrayDeque<>();


	private record TimedAudioBuffer(int alBufferId, long timestamp) {
		void delete() {
			AL10.alDeleteBuffers(this.alBufferId);
		}
	}

	public FrameGrabber(InputStream inputStream) {
		// TODO: Maybe pre-downloading could be a good idea to avoid lags on grabs.
		this.grabber = new FFmpegFrameGrabber(inputStream);
	}

	public void start() throws IOException {

		this.grabber.startUnsafe();

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

	}

	public void stop() {

		try {
			this.grabber.releaseUnsafe();
		} catch (IOException ignored) { }

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

//		if (!this.firstGrabbed) {
//			this.firstGrabbed = true;
//			System.out.println("first grab at: " + ((double) timestamp / 1000000.0));
//			/*long realTimestamp = timestamp + this.refTimestamp;
//			// For the first grab on a grabber, we discard all sound buffers
//			// previous to it.
//			Iterator<TimedAudioBuffer> it = this.audioBuffers.iterator();
//			while (it.hasNext()) {
//				TimedAudioBuffer buf = it.next();
//				if (buf.timestamp < realTimestamp) {
//					buf.delete();
//					it.remove();
//				}
//			}*/
//		}

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
		// For the first grab on a grabber, we discard all sound buffers
		// previous to it.
		while (!this.audioBuffers.isEmpty()) {
			TimedAudioBuffer buf = this.audioBuffers.peek();
			if (buf.timestamp < realTimestamp) {
				buf.delete();
				this.audioBuffers.remove();
			}
		}
	}

	private void pushAudioBuffer(Frame frame) {

		Buffer sample = frame.samples[0];

		int bufferId = AL10.alGenBuffers();

		if (sample instanceof ByteBuffer sampleByte) {
			AL10.alBufferData(bufferId, AL10.AL_FORMAT_STEREO8, sampleByte, frame.sampleRate);
		} else if (sample instanceof ShortBuffer sampleShort) {
			AL10.alBufferData(bufferId, AL10.AL_FORMAT_STEREO16, sampleShort, frame.sampleRate);
		} else {
			AL10.alDeleteBuffers(bufferId);
			throw new IllegalArgumentException("Unsupported sample format.");
		}

		this.audioBuffers.add(new TimedAudioBuffer(bufferId, frame.timestamp));

	}

}
