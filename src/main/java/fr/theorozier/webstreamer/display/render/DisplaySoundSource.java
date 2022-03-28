package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.theorozier.webstreamer.WebStreamerMod;
import net.minecraft.util.math.Vec3i;
import org.bytedeco.javacv.Frame;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class DisplaySoundSource {
	
	private int sourceId;
	
	public DisplaySoundSource() {
		this.sourceId = AL10.alGenSources();
		AL10.alSourcei(this.sourceId, AL10.AL_LOOPING, AL10.AL_FALSE);
		AL10.alSourcei(this.sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
		this.setAttenuation(20);
		this.setVolume(1.0f);
	}

	public void free() {
		AL10.alSourceStop(this.sourceId);
		this.unqueueAndDelete();
		AL10.alDeleteSources(this.sourceId);
	}
	
	public void setPosition(Vec3i pos) {
		AL10.alSourcefv(this.sourceId, AL10.AL_POSITION, new float[] {(float) pos.getX() + 0.5f, (float) pos.getY() + 0.5f, (float) pos.getZ() + 0.5f});
	}
	
	public void setVolume(float volume) {
		AL10.alSourcef(this.sourceId, AL10.AL_GAIN, volume);
	}
	
	public void disableAttenuation() {
		AL10.alSourcei(this.sourceId, AL10.AL_DISTANCE_MODEL, AL10.AL_NONE);
	}
	
	public void setAttenuation(float attenuation) {
		AL10.alSourcei(this.sourceId, AL10.AL_DISTANCE_MODEL, AL11.AL_LINEAR_DISTANCE);
		AL10.alSourcef(this.sourceId, AL10.AL_MAX_DISTANCE, attenuation);
		AL10.alSourcef(this.sourceId, AL10.AL_ROLLOFF_FACTOR, 1.0F);
		AL10.alSourcef(this.sourceId, AL10.AL_REFERENCE_DISTANCE, 0.0F);
	}

	/*public void upload(Frame frame) {
		this.uploadAndEnqueue(frame);
		this.unqueueAndDelete();
	}*/

	public void enqueueRaw(int bufferId) {

		AL10.alSourceQueueBuffers(this.sourceId, bufferId);
		checkErrors("Queue buffers");

		if (AL10.alGetSourcef(this.sourceId, AL10.AL_SOURCE_STATE) !=  AL10.AL_PLAYING) {
			System.out.println("playing again...");
			AL10.alSourcePlay(this.sourceId);
		}

		this.unqueueAndDelete();

	}

	/*private void uploadAndEnqueue(Frame frame) {
		
		RenderSystem.assertOnRenderThread();
		
		if (frame.samples == null || frame.samples.length <= 0)
			return;
		
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
		
		if (checkErrors("Buffer data")) {
			AL10.alDeleteBuffers(bufferId);
			return;
		}
		
		this.enqueueRaw(bufferId);

	}*/

	private void unqueueAndDelete() {
		int numProcessed = AL10.alGetSourcei(this.sourceId, AL10.AL_BUFFERS_PROCESSED);
		if (numProcessed > 0) {
			int[] buffers = new int[numProcessed];
			AL10.alSourceUnqueueBuffers(this.sourceId, buffers);
			checkErrors("Unqueue buffers");
			AL10.alDeleteBuffers(buffers);
			checkErrors("Remove processed buffers");
		}
	}
	
	// Static util extracted from "net.minecraft.client.sound.ALUtil" //
	
	private static boolean checkErrors(String sectionName) {
		int i = AL10.alGetError();
		if (i != 0) {
			WebStreamerMod.LOGGER.error("{}: {}", sectionName, getErrorMessage(i));
			return true;
		} else {
			return false;
		}
	}
	
	private static String getErrorMessage(int errorCode) {
		return switch (errorCode) {
			case AL10.AL_INVALID_NAME -> "Invalid name.";
			case AL10.AL_INVALID_OPERATION -> "Invalid operation.";
			case AL10.AL_INVALID_ENUM -> "Illegal enum.";
			case AL10.AL_INVALID_VALUE -> "Invalid value.";
			case AL10.AL_OUT_OF_MEMORY -> "Unable to allocate memory.";
			default -> "An unrecognized error occurred.";
		};
	}

}
