package fr.theorozier.webstreamer.display.render;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.lwjgl.openal.AL10;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/**
 * A specialized frame grabber for displays.
 */
public class FrameGrabber {

	private final FFmpegFrameGrabber grabber;
	private long refTimestamp;
	private long deltaTimestamp;
	private Frame lastFrame;

	/** Buffers for OpenAL audio buffers to play. */
	private final IntArrayFIFOQueue alAudioBuffers = new IntArrayFIFOQueue();

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
				this.pushAudioBuffer(frame);
			}
		}

	}

	public void stop() {

		try {
			this.grabber.releaseUnsafe();
		} catch (IOException ignored) { }

		while (!this.alAudioBuffers.isEmpty()) {
			AL10.alDeleteBuffers(this.alAudioBuffers.dequeueInt());
		}

	}

	/**
	 * Grab the image frame at the corresponding timestamp, the grabber will attempt
	 * to get the closest frame before timestamp.
	 * @param timestamp The timestamp in microseconds.
	 * @return The grabbed frame or null if frame has not updated since last grab.
	 */
	public Frame grabUntil(long timestamp) throws IOException {

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


//		int targetFrameNumber = (int) ((double) timestamp / 1000000.0 * this.videoFrameRate);
//
//		// System.out.println("ts: " + ((double) timestamp / 1000000.0) + ", frame n°: " + targetFrameNumber + ", last frame n°: " + this.lastFrameNumber + ", fps: " + this.videoFrameRate);
//
//		if (targetFrameNumber <= this.lastFrameNumber) {
//			if (this.lastFrameSent) {
//				return null;
//			} else {
//				this.lastFrameSent = true;
//				return this.lastFrame;
//			}
//		}
//
//		Frame frame;
//		while ((frame = this.grabber.grab()) != null) {
//			if (frame.samples != null) {
//				//this.lastFrameNumber++;
//				this.lastFrameSent = true;
//				this.lastFrame = frame; // last frame might be useless
//				// System.out.println("frame ts: " + ((double) frame.timestamp / 1000000.0) + ", fps: " + this.videoFrameRate);
//				/*if (targetFrameNumber == this.lastFrameNumber) {
//					return frame;
//				}*/
//			}
//		}
//
//		return null;

	}

	/*private static double microToSec(long micro) {
		return (double) micro / 1000000.0;
	}*/

	public void grabRemaining() throws IOException {
		// FIXME: Might be useless
		Frame frame;
		while ((frame = this.grabber.grab()) != null) {
			if (frame.samples != null) {
				this.pushAudioBuffer(frame);
			}
		}
	}

	public void grabAudioAndUpload(DisplaySoundSource source) {
		while (!this.alAudioBuffers.isEmpty()) {
			source.enqueueRaw(this.alAudioBuffers.dequeueInt());
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

		this.alAudioBuffers.enqueue(bufferId);

	}

}
