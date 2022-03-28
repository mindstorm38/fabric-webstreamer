package fr.theorozier.webstreamer.display.render;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.io.IOException;
import java.io.InputStream;

public class FrameGrabber {

	private final FFmpegFrameGrabber grabber;

	/** The last frame grabbed. Usually the same object for every call. */
	private Frame lastFrame = null;

	/** Current frame number. */
	private int lastFrameNumber = -1;

	/** The actual video frame rate. */
	private double videoFrameRate = 0.0;

	public FrameGrabber(InputStream inputStream) {
		this.grabber = new FFmpegFrameGrabber(inputStream);
	}

	public void start() throws IOException {

		this.grabber.startUnsafe();
		this.videoFrameRate = this.grabber.getVideoFrameRate();

		// Also, preload the first image frame
		this.lastFrameNumber = 0;
		this.lastFrame = this.grabber.grabImage();

	}

	public void stop() throws IOException {
		this.grabber.releaseUnsafe();
	}

	/**
	 * Grab the frame at the corresponding timestamp, the grabber will attempt to
	 * get the closest frame before timestamp.
	 * @param timestamp The timestamp in microseconds.
	 * @return The grabbed frame or null if frame has not updated since last grab.
	 */
	public Frame grabUntil(long timestamp) throws IOException {

		// TODO: Handle audio

		if (timestamp < 0) {
			throw new IllegalArgumentException();
		}

		int targetFrameNumber = (int) ((double) timestamp / 1000000.0 * this.videoFrameRate);
		if (targetFrameNumber <= this.lastFrameNumber) {
			return null;
		}

		Frame frame;
		while ((frame = this.grabber.grab()) != null) {
			if (frame.image != null) {
				this.lastFrameNumber++;
				this.lastFrame = frame; // last frame might be useless
				if (targetFrameNumber == this.lastFrameNumber) {
					return frame;
				}
			}
		}

		return null;

	}
	
	/*@Override
	public synchronized Frame grabFrame(boolean doAudio, boolean doVideo, boolean doProcessing, boolean keyFrames, boolean doData) throws Exception {
		
		if (this.nextFrame != null) {
			Frame ret = this.nextFrame;
			this.nextFrame = null;
			return ret;
		}
		
		Frame frame = super.grabFrame(doAudio, doVideo, doProcessing, keyFrames, doData);

		if (frame != null) {

			if (this.firstFrameTimestamp == 0) {
				this.firstFrameTimestamp = frame.timestamp;
				frame.timestamp = 0;
			} else {
				frame.timestamp -= this.firstFrameTimestamp;
				if (frame.timestamp < 0) {
					frame.timestamp = 0;
				}
			}

			if (frame.image != null) {
				this.videoFrameNumber++;
			}

		}
		
		return frame;
		
	}*/

}
