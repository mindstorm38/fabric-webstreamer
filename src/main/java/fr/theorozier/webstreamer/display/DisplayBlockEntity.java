package fr.theorozier.webstreamer.display;

import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.display.source.DisplaySource;
import fr.theorozier.webstreamer.display.source.RawDisplaySource;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * <p>The block entity that is backing {@link DisplayBlock}, it's mainly composed of the
 * {@link DisplaySource}, size and audio configuration.</p>
 */
public class DisplayBlockEntity extends BlockEntity {
    
    private DisplaySource source = new RawDisplaySource();
    private float width = 1;
    private float height = 1;
    private float audioDistance = 10f;
    private float audioVolume = 1f;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(WebStreamerMod.DISPLAY_BLOCK_ENTITY, pos, state);
    }
    
    public void setSource(@NotNull DisplaySource source) {
        Objects.requireNonNull(source);
        this.source = source;
        this.markRenderDataSourceDirty();
        this.markDirty();
    }
    
    @NotNull
    public DisplaySource getSource() {
        return source;
    }
    
    /**
     * Reset the internal source URI, that is currently used with Twitch sources in order
     * to reset the channels' cache. This also mark the render data as dirty in order to
     * update the URI on next render.
     */
    public void resetSourceUri() {
        this.source.resetUri();
        this.markRenderDataSourceDirty();
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        this.markDirty();
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }
    
    public float calcWidthOffset() {
        return (this.width - 1) / -2f;
    }
    
    public float calcHeightOffset() {
        return (this.height - 1) / -2f;
    }
    
    public void setAudioConfig(float distance, float volume) {
        this.audioDistance = distance;
        this.audioVolume = volume;
        this.markDirty();
    }
    
    public float getAudioDistance() {
        return audioDistance;
    }
    
    public float getAudioVolume() {
        return audioVolume;
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt) {
        
        super.writeNbt(nbt);
        
        NbtCompound displayNbt = new NbtCompound();
        nbt.put("display", displayNbt);
    
        displayNbt.putFloat("width", this.width);
        displayNbt.putFloat("height", this.height);
        displayNbt.putFloat("audioDistance", this.audioDistance);
        displayNbt.putFloat("audioVolume", this.audioVolume);
        
        if (this.source != null) {
            displayNbt.putString("type", this.source.getType());
            this.source.writeNbt(displayNbt);
        } else {
            displayNbt.putString("type", "");
        }
    
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        
        super.readNbt(nbt);
        
        if (nbt.get("display") instanceof NbtCompound displayNbt) {
    
            if (displayNbt.get("width") instanceof NbtFloat width) {
                this.width = width.floatValue();
            } else {
                this.width = 1;
            }
            
            if (displayNbt.get("height") instanceof NbtFloat height) {
                this.height = height.floatValue();
            } else {
                this.height = 1;
            }
            
            if (displayNbt.get("audioDistance") instanceof NbtFloat audioDistance) {
                this.audioDistance = audioDistance.floatValue();
            } else {
                this.audioDistance = 10f;
            }
            
            if (displayNbt.get("audioVolume") instanceof NbtFloat audioVolume) {
                this.audioVolume = audioVolume.floatValue();
            } else {
                this.audioVolume = 1f;
            }
    
            if (displayNbt.get("type") instanceof NbtString type) {
                this.source = DisplaySource.newSourceFromType(type.asString());
                this.source.readNbt(displayNbt);
            } else {
                this.source = new RawDisplaySource();
            }
            
            this.markRenderDataSourceDirty();
    
        }
        
    }
    
    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
    
    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }
    
    /** Utility method to make a log message prefixed by this display's position. */
    public String makeLog(String message) {
        return "[" + this.pos.getX() + "/" + this.pos.getY() + "/" + this.pos.getZ() + "] " + message;
    }
    
    /*
     * == RENDER DATA
     * 
     * The render data is present on both server and client side, but is only actually
     * used on client side for storing the display render data.
     */
    
    private final Object cachedRenderDataGuard = new Object();
    private Object cachedRenderData;
    
    /**
     * <b>Should only be called from client side.</b>
     * 
     * @return A <code>DisplayRenderData</code> class, only valid on client side.
     */
    public Object getRenderData() {
        synchronized (this.cachedRenderDataGuard) {
            if (this.cachedRenderData == null) {
                this.cachedRenderData = new fr.theorozier.webstreamer.display.render.DisplayRenderData(this);
            }
            return this.cachedRenderData;
        }
    }
    
    /**
     * This internal method is used to mark internal render data URL as dirty.
     * This might be used from any side and is thread-safe.
     * This will actually do something only on the client-side and only if the
     * render data has been requested before.
     */
    private void markRenderDataSourceDirty() {
        synchronized (this.cachedRenderDataGuard) {
            if (this.cachedRenderData != null) {
                ((fr.theorozier.webstreamer.display.render.DisplayRenderData) this.cachedRenderData).markSourceDirty();
            }
        }
    }
    
}
