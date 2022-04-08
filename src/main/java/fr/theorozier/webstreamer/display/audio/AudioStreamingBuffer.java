package fr.theorozier.webstreamer.display.audio;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.bytedeco.javacv.Frame;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Objects;

import static org.lwjgl.openal.AL11.*;

/**
 * A streaming buffer abstraction, always mono channel / 16 bits.
 */
@Environment(EnvType.CLIENT)
public class AudioStreamingBuffer {
	
	private int bufferId;
	/** When the buffer should be played. */
	public final long timestamp;
	/** Duration in microseconds. */
	public final long duration;
	
	private AudioStreamingBuffer(int bufferId, long timestamp, long duration) {
		this.bufferId = bufferId;
		this.timestamp = timestamp;
		this.duration = duration;
	}
	
	public int getBufferId() {
		return this.bufferId;
	}
	
	public boolean isValid() {
		return this.bufferId != 0;
	}
	
	public void checkValid() {
		if (!this.isValid()) {
			throw new IllegalStateException("this audio buffer has already been freed");
		}
	}
	
	public void free() {
		this.checkValid();
		alDeleteBuffers(this.bufferId);
		this.bufferId = 0;
	}
	
	public static AudioStreamingBuffer fromFrame(ShortBuffer tempBuffer, Frame frame) {
		Objects.requireNonNull(frame.samples, "given frame has no audio sample");
		return fromRawData(tempBuffer, frame.samples[0], frame.audioChannels, frame.sampleRate, frame.timestamp);
	}
	
	public static AudioStreamingBuffer fromRawData(ShortBuffer tempBuffer, Buffer rawBuffer, int channels, int frequency, long timestamp) {
		
		if (channels != 1 && channels != 2) {
			throw new IllegalArgumentException("illegal channels count, only 1 or 2 are allowed");
		}
		
		boolean stereo = (channels == 2);
		int samples;
		
		if (rawBuffer instanceof ByteBuffer sampleByte) {
			int count = sampleByte.remaining();
			if (stereo) {
				samples = count / 2;
				for (int i = 0; i < count; i += 2) {
					short sampleLeft = (short) ((int) sampleByte.get(i) << 8);
					short sampleRight = (short) ((int) sampleByte.get(i + 1) << 8);
					tempBuffer.put((short) ((sampleLeft + sampleRight) / 2));
				}
			} else {
				samples = count;
				for (int i = 0; i < count; i++) {
					tempBuffer.put((short) (sampleByte.get(i) << 8));
				}
			}
			tempBuffer.flip();
		} else if (rawBuffer instanceof ShortBuffer sampleShort) {
			int count = sampleShort.remaining();
			if (stereo) {
				samples = count / 2;
				for (int i = 0; i < count; i += 2) {
					short sampleLeft = sampleShort.get(i);
					short sampleRight = sampleShort.get(i + 1);
					tempBuffer.put((short) (((int) sampleLeft + (int) sampleRight) / 2));
				}
				tempBuffer.flip();
			} else {
				samples = count;
				// No operation is needed, just change the temp buffer.
				// No flip is needed because input raw buffer is already.
				tempBuffer = sampleShort;
			}
		} else {
			throw new IllegalArgumentException("unsupported sample format");
		}
		
		int bufferId = alGenBuffers();
		alBufferData(bufferId, AL_FORMAT_MONO16, tempBuffer, frequency);
		
		AudioStreamingSource.checkErrors("audio buffer data");
		
		long duration = samples * 1000000L / frequency;
		return new AudioStreamingBuffer(bufferId, timestamp, duration);
		
	}
	
}
