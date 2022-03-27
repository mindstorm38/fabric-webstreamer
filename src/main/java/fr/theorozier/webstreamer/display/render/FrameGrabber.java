package fr.theorozier.webstreamer.display.render;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.io.InputStream;

public class FrameGrabber extends FFmpegFrameGrabber {
	
	private long firstFrameTimestamp = 0;
	private Frame firstFrame;
	
	public FrameGrabber(InputStream inputStream) {
		super(inputStream);
	}
	
	@Override
	public void start() throws Exception {
		
		super.start();
		
		// Also, preload the first image frame
		this.firstFrame = this.grabImage();
		
	}
	
	@Override
	public synchronized Frame grabFrame(boolean doAudio, boolean doVideo, boolean doProcessing, boolean keyFrames, boolean doData) throws Exception {
		
		if (this.firstFrame != null) {
			Frame ret = this.firstFrame;
			this.firstFrame = null;
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
		}
		
		return frame;
		
	}
	
}
