package fr.theorozier.webstreamer.display.source;

import net.minecraft.nbt.NbtCompound;

import java.net.URI;

/**
 * <p>The null display source always return no URI, so nothing will be rendered on the 
 * display, this is the default display source and is a singleton.</p>
 */
public class NullDisplaySource implements DisplaySource {
	
	public static final NullDisplaySource INSTANCE = new NullDisplaySource();
	
	public static final String TYPE = "none";
	
	private NullDisplaySource() { }
	
	@Override
	public String getType() {
		return TYPE;
	}
	
	@Override
	public URI getUri() {
		return null;
	}
	
	@Override
	public void resetUri() {
	
	}
	
	@Override
	public String getStatus() {
		return "NONE";
	}
	
	@Override
	public void writeNbt(NbtCompound nbt) {
	
	}
	
	@Override
	public void readNbt(NbtCompound nbt) {
	
	}
	
}
