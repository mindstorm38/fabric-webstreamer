package fr.theorozier.webstreamer.util;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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
    private boolean submitted;
    private Future<TO> future;

    public AsyncProcessor(Converter<FROM, TO, EXC> converter, boolean allowDuplicates) {
        this.converter = converter;
        this.allowDuplicates = allowDuplicates;
    }

    public AsyncProcessor(Converter<FROM, TO, EXC> converter) {
        this(converter, false);
    }

    @SuppressWarnings("unchecked")
    public void fetch(ExecutorService executor, Consumer<TO> onSuccess, Consumer<EXC> onError) {

        if (this.future != null && this.future.isDone()) {
            // If the thread is already interrupted, return early to avoid
            // InterruptedException in "Future::get".
            if (Thread.interrupted())
                return;
            try {
                onSuccess.accept(this.future.get());
            } catch (InterruptedException | CancellationException e) {
                // Cancel should not happen.
            } catch (ExecutionException ee) {
                try {
                    onError.accept((EXC) ee.getCause());
                } catch (ClassCastException cce) {
                    // In case if runtime exceptions.
                }
            } finally {
                this.future = null;
            }
        }

        if (this.future == null && this.pending != null && !this.submitted) {
            FROM from = this.pending;
            this.future = executor.submit(() -> this.converter.convert(from));
            this.submitted = true;
        }

    }

    public void push(FROM from) {
        if (this.allowDuplicates || !Objects.equals(from, this.pending)) {
            this.pending = from;
            this.submitted = false;
        }
    }
    
    /**
     * @return Return <code>true</code> if this async processor is in idle state.
     */
    public boolean idle() {
        return this.future == null;
    }

}
