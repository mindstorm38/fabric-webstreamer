package fr.theorozier.webstreamer.display;

/**
 * Interface to implement on entities that support interaction with a display block.
 */
public interface DisplayBlockInteract {

    /**
     * Called when a display block entity request the implementer to open the display screen.
     * @param blockEntity The display block entity.
     */
    void openDisplayBlockScreen(DisplayBlockEntity blockEntity);

}
