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
	private double videoFrameRate = 0.0;

	/** The last frame grabbed. Usually the same object for every call. */
	private Frame lastFrame = null;

	/** True if the last frame was already sent. */
	private boolean lastFrameSent = false;

	/** Current frame number. */
	private int lastFrameNumber = -1;

	/** Buffers for OpenAL audio buffers to play. */
	private final IntArrayFIFOQueue alAudioBuffers = new IntArrayFIFOQueue();

	public FrameGrabber(InputStream inputStream) {
		// TODO: Maybe pre-downloading could be a good idea to avoid lags on grabs.
		this.grabber = new FFmpegFrameGrabber(inputStream);
	}

	public void start() throws IOException {

		this.grabber.startUnsafe();
		this.videoFrameRate = this.grabber.getVideoFrameRate();

		// Also, preload the first image frame
		this.lastFrameNumber = 0;
		this.lastFrameSent = false;

		int i = 0;
		Frame frame;
		while ((frame = this.grabber.grab()) != null) {
			if (frame.samples != null) {
				double ts = (double) frame.timestamp / 1000000.0;
				System.out.println("[" + (i++) + "] found audio buffer on start at " + ts);
				this.pushAudioBuffer(frame);
			} else if (frame.image != null) {
				this.lastFrame = frame;
				break;
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
	 * Grab the frame at the corresponding timestamp, the grabber will attempt to
	 * get the closest frame before timestamp.
	 * @param timestamp The timestamp in microseconds.
	 * @return The grabbed frame or null if frame has not updated since last grab.
	 */
	public Frame grabUntil(long timestamp) throws IOException {

		if (timestamp < 0) {
			throw new IllegalArgumentException();
		}

		int targetFrameNumber = (int) ((double) timestamp / 1000000.0 * this.videoFrameRate);
		if (targetFrameNumber <= this.lastFrameNumber) {
			if (this.lastFrameSent) {
				return null;
			} else {
				this.lastFrameSent = true;
				return this.lastFrame;
			}
		}

		Frame frame;
		while ((frame = this.grabber.grab()) != null) {
			if (frame.samples != null) {
				this.pushAudioBuffer(frame);
			} else if (frame.image != null) {
				this.lastFrameNumber++;
				this.lastFrameSent = true;
				this.lastFrame = frame; // last frame might be useless
				if (targetFrameNumber == this.lastFrameNumber) {
					return frame;
				}
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
