package fr.theorozier.webstreamer.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * A utility for asynchronously process data, using an executor and a future.
 *
 * @param <FROM> Input type of the conversion.
 * @param <TO> Output type of the conversion.
 * @param <EXC> The exception type.
 */
public class AsyncProcess<FROM, TO, EXC extends Exception> {

    private final Converter<FROM, TO, EXC> converter;

    private FROM pendingFrom;
    private Future<TO> futureTo;

    public AsyncProcess(Converter<FROM, TO, EXC> converter) {
        this.converter = converter;
    }

    @SuppressWarnings("unchecked")
    public void fetch(ExecutorService executor, Consumer<TO> success, Consumer<EXC> error) {

        if (this.futureTo != null && this.futureTo.isDone()) {
            try {
                success.accept(this.futureTo.get());
            } catch (InterruptedException | CancellationException e) {
                // Cancel should not happen.
            } catch (ExecutionException ee) {
                try {
                    error.accept((EXC) ee.getCause());
                } catch (ClassCastException cce) {
                    // Should not fail because of generic enforcement.
                }
            } finally {
                this.futureTo = null;
            }
        }

        if (this.futureTo == null && this.pendingFrom != null) {
            FROM from = this.pendingFrom;
            this.futureTo = executor.submit(() -> this.converter.convert(from));
            this.pendingFrom = null;
        }

    }

    public void push(FROM from) {
        this.pendingFrom = from;
    }

    public boolean active() {
        return this.futureTo != null;
    }

    public interface Converter<FROM, TO, EXC extends Exception> {
        TO convert(FROM from) throws EXC;
    }

}
