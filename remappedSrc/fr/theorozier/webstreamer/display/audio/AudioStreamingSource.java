package fr.theorozier.webstreamer.display.audio;

import fr.theorozier.webstreamer.WebStreamerMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayDeque;
import java.util.Objects;

import static org.lwjgl.openal.AL11.*;

@Environment(EnvType.CLIENT)
public class AudioStreamingSource {

	private int sourceId;
	
	/** Real system nano timestamp when play started. */
	private long playTimestamp;
	private long playBufferTimestamp;
	
	private ArrayDeque<AudioStreamingBuffer> queue = new ArrayDeque<>();
	private long lastBufferTimestamp;
	
	public AudioStreamingSource() {
		this.sourceId = alGenSources();
		alSourcei(this.sourceId, AL_LOOPING, AL_FALSE);
		alSourcei(this.sourceId, AL_SOURCE_RELATIVE, AL_FALSE);
		this.setVolume(1f);
		this.setAttenuation(50f);
	}
	
	public int getSourceId() {
		return this.sourceId;
	}
	
	public boolean isValid() {
		return this.sourceId != 0;
	}
	
	public void checkValid() {
		if (!this.isValid()) {
			throw new IllegalArgumentException("this audio source has already been freed");
		}
	}
	
	public void free() {
		this.checkValid();
		this.queue.forEach(AudioStreamingBuffer::free);
		this.queue.clear();
		this.queue = null;
		alSourceStop(this.sourceId);
		alDeleteSources(this.sourceId);
		this.sourceId = 0;
	}
	
	/** Manually stop the source, when doing that all queued buffers are freed and cleared. */
	public void stop() {
		this.checkValid();
		alSourceStop(this.sourceId);
		this.queue.forEach(AudioStreamingBuffer::free);
		this.queue.clear();
	}
	
	public void setPosition(Vec3i pos) {
		this.checkValid();
		alSourcefv(this.sourceId, AL_POSITION, new float[] {(float) pos.getX() + 0.5f, (float) pos.getY() + 0.5f, (float) pos.getZ() + 0.5f});
	}
	
	public void setVolume(float volume) {
		this.checkValid();
		alSourcef(this.sourceId, AL_GAIN, volume);
	}
	
	public void setAttenuation(float attenuation) {
		this.checkValid();
		alSourcei(this.sourceId, AL_DISTANCE_MODEL, AL_LINEAR_DISTANCE);
		alSourcef(this.sourceId, AL_MAX_DISTANCE, attenuation);
		alSourcef(this.sourceId, AL_ROLLOFF_FACTOR, 1.0F);
		alSourcef(this.sourceId, AL_REFERENCE_DISTANCE, 0.0F);
	}
	
	public boolean isPlaying() {
		this.checkValid();
		return alGetSourcei(this.sourceId, AL_SOURCE_STATE) == AL_PLAYING;
	}
	
	public void playFrom(long timestamp) {
		
		this.checkValid();
	
		boolean playing = this.isPlaying();
		if (!playing) {
			this.removeAndFreeBuffersBefore(timestamp);
		}
		
		this.unqueueAndFree();
		
		if (this.queue.isEmpty()) {
			// If no buffer is queued, just don't play.
			return;
		}
		
		AudioStreamingBuffer firstBuffer = this.queue.peekFirst();
		
		if (!playing && firstBuffer.timestamp > timestamp) {
			// If we are not playing, we should only start playing when the first buffer is reached.
			return;
		}
		
		long firstBufferTimestamp = -1L;
		int buffersCount = this.queue.size();
		
		int[] buffers = new int[buffersCount];
		for (int i = 0; i < buffersCount; ++i) {
			AudioStreamingBuffer buffer = this.queue.removeFirst();
			buffers[i] = buffer.getBufferId();
			if (firstBufferTimestamp == -1L) {
				firstBufferTimestamp = buffer.timestamp;
			}
		}
		
		alSourceQueueBuffers(this.sourceId, buffers);
		
		if (!playing) {
			alSourcePlay(this.sourceId);
			this.playTimestamp = System.nanoTime();
			this.playBufferTimestamp = firstBufferTimestamp;
		}
		
	}
	
	/**
	 * Queue the given streaming buffer on this source.
	 * @param buffer A non-null streaming buffer.
	 */
	public void queueBuffer(AudioStreamingBuffer buffer) {
		
		Objects.requireNonNull(buffer, "given buffer should not be null");
		
		this.checkValid();
		
		if (buffer.timestamp <= this.lastBufferTimestamp) {
			WebStreamerMod.LOGGER.error("given {} us, expected more than {} us", buffer.timestamp, this.lastBufferTimestamp);
			return;
		}
		
		this.queue.addLast(buffer);
		this.lastBufferTimestamp = buffer.timestamp;
		
	}
	
	/**
	 * Unqueue processed buffers and free them.
	 */
	public void unqueueAndFree() {
		int numProcessed = alGetSourcei(this.sourceId, AL_BUFFERS_PROCESSED);
		if (numProcessed > 0) {
			int[] buffers = new int[numProcessed];
			alSourceUnqueueBuffers(this.sourceId, buffers);
			if (!checkErrors("audio unqueue buffers")) {
				alDeleteBuffers(buffers);
				checkErrors("audio delete buffers");
			}
		}
	}
	
	private void removeAndFreeBuffersBefore(long timestamp) {
		// System.out.println("removeAndFreeBuffersBefore(" + timestamp + ")");
		while (!this.queue.isEmpty()) {
			AudioStreamingBuffer buffer = this.queue.peekFirst();
			if (buffer.timestamp + buffer.duration > timestamp) {
				break;
			}
			// System.out.println("=> removing buffer at: " + buffer.timestamp);
			this.queue.removeFirst().free();
		}
	}
	
	/**
	 * Check for OpenAL errors.
	 * @param sectionName The section name to use when logging the error.
	 * @return True if there is an OpenAL error.
	 */
	static boolean checkErrors(String sectionName) {
		int i = alGetError();
		if (i != 0) {
			WebStreamerMod.LOGGER.error("{}: {}", sectionName, getErrorMessage(i));
			return true;
		} else {
			return false;
		}
	}
	
	static String getErrorMessage(int errorCode) {
		return switch (errorCode) {
			case AL_INVALID_NAME -> "invalid name.";
			case AL_INVALID_OPERATION -> "invalid operation.";
			case AL_INVALID_ENUM -> "illegal enum.";
			case AL_INVALID_VALUE -> "invalid value.";
			case AL_OUT_OF_MEMORY -> "unable to allocate memory.";
			default -> "an unrecognized error occurred.";
		};
	}

}
