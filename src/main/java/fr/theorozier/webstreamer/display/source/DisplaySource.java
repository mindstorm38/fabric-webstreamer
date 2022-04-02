package fr.theorozier.webstreamer.display.source;

import net.minecraft.nbt.NbtCompound;

import java.net.URL;

public interface DisplaySource {

    String getType();
    
    URL getUrl();
    
    void writeNbt(NbtCompound nbt);
    
    void readNbt(NbtCompound nbt);
    
    static DisplaySource newSourceFromType(String type) {
        return switch (type) {
            case RawDisplaySource.TYPE -> new RawDisplaySource();
            case TwitchDisplaySource.TYPE -> new TwitchDisplaySource();
            default -> null;
        };
    }
    
}
