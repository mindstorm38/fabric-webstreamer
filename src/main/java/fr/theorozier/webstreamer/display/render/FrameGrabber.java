package fr.theorozier.webstreamer.display.render;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

public class FrameGrabber {

	private final FFmpegFrameGrabber grabber;

	/** The last frame grabbed. Usually the same object for every call. */
	private Frame lastFrame = null;

	/** True if the last frame was already sent. */
	private boolean lastFrameSent = false;

	/** Current frame number. */
	private int lastFrameNumber = -1;

	/** The actual video frame rate. */
	private double videoFrameRate = 0.0;

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
		this.lastFrame = this.grabber.grabImage();

	}

	public void stop() {
		try {
			this.grabber.releaseUnsafe();
		} catch (IOException ignored) { }
	}

	/**
	 * Grab the frame at the corresponding timestamp, the grabber will attempt to
	 * get the closest frame before timestamp.
	 * @param timestamp The timestamp in microseconds.
	 * @return The grabbed frame or null if frame has not updated since last grab.
	 */
	public Frame grabUntil(long timestamp, Consumer<Frame> soundConsumer) throws IOException {

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
				soundConsumer.accept(frame);
			}
			if (frame.image != null) {
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

}
