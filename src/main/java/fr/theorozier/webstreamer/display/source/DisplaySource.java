package fr.theorozier.webstreamer.display.source;

import net.minecraft.nbt.NbtCompound;

import java.net.URL;

public interface DisplaySource {

    URL getUrl();
    
    void writeNbt(NbtCompound nbt);
    
    void readNbt(NbtCompound nbt);
    
}
