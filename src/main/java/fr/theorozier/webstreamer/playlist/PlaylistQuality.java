package fr.theorozier.webstreamer.playlist;

import java.net.URI;

/**
 * A generic record that maps a quality name to the URI of the resource.
 */
public record PlaylistQuality(String name, URI uri) { }
