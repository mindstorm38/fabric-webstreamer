package fr.theorozier.webstreamer.display.source;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * <p>This interface is implemented by the various sources types that exists. A display 
 * source represent the static information about a source that will be saved in the 
 * display block entity.</p>
 * 
 * <p>This is important to understand that sources are not responsible for choosing the
 * type of display render layer that will be used to render the display. The render layer
 * can depend on the URI file extension for example.</p>
 */
public abstract class DisplaySource implements Cloneable {

    /**
     * @return Type name of this display source, display sources should define a 
     * <code>TYPE</code> constant that contains the same value.
     */
    public abstract String getType();
    
    /**
     * @return The resource URI to display. This URI components will defines how the 
     * display will be rendered. This function should be blocking and implement caching
     * to avoid multiple computation of the value.
     */
    public abstract URI getUri();
    
    /**
     * Force any internal cache on the URI to be reset, the next call to {@link #getUri()}
     * should recompute the URI, if relevant.
     */
    public abstract void resetUri();
    
    /**
     * @return The status that is shown on the display block as overlay when the item is
     * selected in the hotbar.
     */
    public abstract String getStatus();
    
    /**
     * Write this source's NBT data to be saved in the block entity.
     * 
     * @param nbt The NBT compound to save the data in.
     */
    public abstract void writeNbt(NbtCompound nbt);
    
    /**
     * Read this source's NBT data from saved data of the block entity.
     * 
     * @param nbt The NBT compound to load the data from.
     */
    public abstract void readNbt(NbtCompound nbt);

    @Override
    public DisplaySource clone() {
        try {
            return (DisplaySource) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException();
        }
    }

    @NotNull
    public static DisplaySource newSourceFromType(String type) {
        return switch (type) {
            case RawDisplaySource.TYPE -> new RawDisplaySource();
            case TwitchDisplaySource.TYPE -> new TwitchDisplaySource();
            // By default, we create an empty display source.
            default -> new RawDisplaySource();
        };
    }
    
}
