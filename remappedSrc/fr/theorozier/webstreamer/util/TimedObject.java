package fr.theorozier.webstreamer.util;

public record TimedObject<T>(T obj, long time) {
	
	public TimedObject(T obj) {
		this(obj, System.nanoTime());
	}
	
}
