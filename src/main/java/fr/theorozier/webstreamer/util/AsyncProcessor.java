package fr.theorozier.webstreamer.util;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * <p>A utility for asynchronously process data, using an executor and a future.</p>
 * <p>This class is not thread safe, you should call methods from one thread.</p>
 *
 * @param <FROM> Input type of the conversion.
 * @param <TO> Output type of the conversion.
 * @param <EXC> The exception type.
 */
public class AsyncProcessor<FROM, TO, EXC extends Exception> {

    private final Converter<FROM, TO, EXC> converter;
    
    /** True to allow two equal values to be pushed one after another. */
    private final boolean allowDuplicates;

    private FROM requestedFrom;
    private boolean requested;
    
    private Future<TO> future;
    private FROM futureFrom;
    
    /**
     * Construct a new asynchronous value processor.
     * @param converter A converter from input to output type with a specified exception type.
     * @param allowDuplicates Set to true if this processor should accept duplicated value when calling {@link #push}.
     *                        This could be useful if the converter function is not stable and can return different
     *                        values for the same input depending on the context. For example with HTTP requests.
     */
    public AsyncProcessor(Converter<FROM, TO, EXC> converter, boolean allowDuplicates) {
        this.converter = converter;
        this.allowDuplicates = allowDuplicates;
    }

    public AsyncProcessor(Converter<FROM, TO, EXC> converter) {
        this(converter, false);
    }

    @SuppressWarnings("unchecked")
    public void fetch(ExecutorService executor, BiConsumer<FROM, TO> onSuccess, BiConsumer<FROM, EXC> onError) {

        if (this.future != null && this.future.isDone()) {
            // If the thread is already interrupted, return early to avoid
            // InterruptedException in "Future::get".
            if (Thread.interrupted())
                return;
            try {
                onSuccess.accept(this.futureFrom, this.future.get());
            } catch (InterruptedException | CancellationException e) {
                // Cancel should not happen.
            } catch (ExecutionException ee) {
                try {
                    onError.accept(this.futureFrom, (EXC) ee.getCause());
                } catch (ClassCastException cce) {
                    // In case if runtime exceptions.
                }
            } finally {
                this.future = null;
                this.futureFrom = null;
            }
        }

        if (this.future == null && this.requested) {
            FROM from = this.requestedFrom;
            this.futureFrom = from;
            this.future = executor.submit(() -> this.converter.convert(from));
            this.requested = false;
        }

    }
    
    public void fetch(ExecutorService executor, Consumer<TO> onSuccess, Consumer<EXC> onError) {
        this.fetch(executor, (from, to) -> onSuccess.accept(to), (from, err) -> onError.accept(err));
    }

    public void push(FROM from) {
        Objects.requireNonNull(from);
        if (this.allowDuplicates || this.requestedFrom == null || !this.requestedFrom.equals(from)) {
            this.requestedFrom = from;
            this.requested = true;
        }
    }
    
    /**
     * If a value is currently pushed, remove it. Doing so will prevent it from being processed by a call to
     * {@link #fetch}. This also reset the last requested value, allowing potential duplicate values in
     * {@link #push}.
     */
    public void reset() {
        this.requestedFrom = null;
        this.requested = false;
    }
    
    public boolean requested() {
        return this.requested;
    }
    
    public boolean active() {
        return this.future != null;
    }
    
    public boolean idle() {
        return this.future == null;
    }

}
