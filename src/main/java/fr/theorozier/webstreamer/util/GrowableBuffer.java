package fr.theorozier.webstreamer.util;

import java.util.Objects;
import java.util.function.BiFunction;

public class GrowableBuffer<B> {
	
	private final BiFunction<B, Integer, B> realloc;
	private B buffer;

	public GrowableBuffer(B buffer, BiFunction<B, Integer, B> realloc) {
		this.buffer = Objects.requireNonNull(buffer);
		this.realloc = Objects.requireNonNull(realloc);
	}
	
}
