package fr.theorozier.webstreamer.display.render;

import fr.theorozier.webstreamer.display.DisplayBlockEntity;

import java.net.URI;

/**
 * An abstract display layer, not guaranteed to provide render layer, it can also be
 * used for grouping layers by specific properties.
 */
public interface DisplayLayer {

    /**
     * Called on each reader tick.
     */
    void tick();

    /**
     * This is called to clean up the layer if it has not been used in a long time.
     * Note that the implementor is responsible for freeing resources, if needed!
     *
     * @param now The time in monotonic nanosecond when the cleanup was started. If
     *            0 is given then the cleanup method is forced to free the layer and
     *            therefore return true.
     * @return True if this display layer can be forgotten by its manager.
     */
    boolean cleanup(long now);

    /**
     * @return The cost of this layer, this is called once on insertion, and compared
     * to see if any more layer can be added to the scene. For comparison, the cost of
     * a static image layer that does a single request at initialization is 1.
     */
    int cost();

    /**
     * Get the actual render layer from this abstract layer to use for rendering.
     * @param uri The computed URI of the display.
     * @param display The display block entity.
     * @return The display layer,
     * @throws OutOfLayerException If no more layer can be allocated.
     * @throws UnknownFormatException When the combination of URI and display
     * are not valid for creating a display layer.
     */
    DisplayLayerRender getRender(URI uri, DisplayBlockEntity display) throws OutOfLayerException, UnknownFormatException;

    class OutOfLayerException extends Exception {}
    class UnknownFormatException extends Exception {}

}
