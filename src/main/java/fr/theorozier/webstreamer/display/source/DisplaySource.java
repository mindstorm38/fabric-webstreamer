package fr.theorozier.webstreamer.display.source;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

public interface DisplaySource {

    String getType();
    
    URI getUri();
    
    String getStatus();
    
    void writeNbt(NbtCompound nbt);
    
    void readNbt(NbtCompound nbt);
    
    @NotNull
    static DisplaySource newSourceFromType(String type) {
        return switch (type) {
            case RawDisplaySource.TYPE -> new RawDisplaySource();
            case TwitchDisplaySource.TYPE -> new TwitchDisplaySource();
            default -> NullDisplaySource.INSTANCE;
        };
    }
    
}
