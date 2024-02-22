package fr.theorozier.webstreamer.display.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.Vec3i;

/**
 * An actual display layer.
 */
public interface DisplayLayer {

    /**
     * Each display entity that is using this layer will push its position to this function,
     * the distance from the local player and the audio parameters of the display. This is
     * used to avoid playing the same sound multiple time.
     *
     * @param pos The position of the display.
     * @param dist The distance between the local player and the display.
     * @param audioDistance The audio distance configured for the display.
     * @param audioVolume The audio volume configured for the display.
     */
    void pushAudioSource(Vec3i pos, float dist, float audioDistance, float audioVolume);

    /**
     * @return True if this layer should be lost while currently used.
     */
    boolean isLost();

    /**
     * @return The Minecraft render layer using for the rendering pipeline.
     */
    RenderLayer getRenderLayer();

}
