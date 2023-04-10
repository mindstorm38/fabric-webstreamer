package fr.theorozier.webstreamer.util;

@FunctionalInterface
public interface Converter<FROM, TO, EXC extends Exception> {
    TO convert(FROM from) throws EXC;
}
