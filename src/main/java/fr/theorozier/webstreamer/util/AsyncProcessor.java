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
    private final boolean allowDuplicates;

    private FROM pending;
    private FROM requested;
    private Future<TO> future;

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
                onSuccess.accept(this.pending, this.future.get());
            } catch (InterruptedException | CancellationException e) {
                // Cancel should not happen.
            } catch (ExecutionException ee) {
                try {
                    onError.accept(this.pending, (EXC) ee.getCause());
                } catch (ClassCastException cce) {
                    // In case if runtime exceptions.
                }
            } finally {
                this.future = null;
                this.pending = null;
            }
        }

        if (this.future == null && this.requested != null) {
            FROM from = this.requested;
            this.pending = from;
            this.future = executor.submit(() -> this.converter.convert(from));
            this.requested = null;
        }

    }
    
    public void fetch(ExecutorService executor, Consumer<TO> onSuccess, Consumer<EXC> onError) {
        this.fetch(executor, (from, to) -> onSuccess.accept(to), (from, err) -> onError.accept(err));
    }

    public void push(FROM from) {
        Objects.requireNonNull(from);
        if (this.allowDuplicates || !Objects.equals(from, this.requested)) {
            this.requested = from;
        }
    }
    
    public boolean requested() {
        return this.requested != null;
    }
    
    public boolean active() {
        return this.future != null;
    }
    
    public boolean idle() {
        return this.future == null;
    }

}
