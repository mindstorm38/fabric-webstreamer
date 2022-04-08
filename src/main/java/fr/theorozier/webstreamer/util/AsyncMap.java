package fr.theorozier.webstreamer.util;

import fr.theorozier.webstreamer.WebStreamerMod;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * <p>A utility for asynchronously process data, using an executor and a future.</p>
 * <p>This class is not thread safe, you should call methods from one thread.</p>
 */
public class AsyncMap<FROM, TO, EXC extends Exception> {

    private final Converter<FROM, TO, EXC> converter;
    private final Consumer<TO> cleanup;
    private final Int2ObjectOpenHashMap<TimedFuture<TO>> futures = new Int2ObjectOpenHashMap<>();
    private final long timeout;

    /** Internal future with a timeout. */
    private record TimedFuture<TO>(Future<TO> future, long time, long timeout) {

        public boolean isTimedOut(long now) {
            return now - this.time >= this.timeout;
        }

        public void cleanup(Consumer<TO> cleanup) {
            try {
                cleanup.accept(this.future.get());
            } catch (InterruptedException | CancellationException | ExecutionException ignored) { }
        }

    }

    /**
     * Construct an async map.
     * @param converter The function used to convert from the input element to an output,
     *                  allowing a specific exception type.
     * @param cleanup The function to clean up timed out futures.
     * @param timeout The timeout for futures to cleanup.
     */
    public AsyncMap(Converter<FROM, TO, EXC> converter, Consumer<TO> cleanup, long timeout) {
        this.converter = converter;
        this.cleanup = cleanup;
        this.timeout = timeout;
    }

    public void push(ExecutorService executor, FROM from, int key) {
        this.futures.computeIfAbsent(key, key0 ->
            new TimedFuture<>(executor.submit(() -> this.converter.convert(from)), System.nanoTime(), this.timeout)
        );
    }

    @SuppressWarnings("unchecked")
    public boolean pull(int key, Consumer<TO> onSuccess, Consumer<EXC> onError) {
        TimedFuture<TO> future = this.futures.get(key);
        if (future != null) {
            if (future.future.isDone()) {
                // If the thread is already interrupted, return early to avoid
                // InterruptedException in "Future::get".
                if (Thread.interrupted())
                    return true;
                try {
                    onSuccess.accept(future.future.get());
                } catch (InterruptedException | CancellationException e) {
                    // Cancel should not happen.
                } catch (ExecutionException ee) {
                    try {
                        onError.accept((EXC) ee.getCause());
                    } catch (ClassCastException cce) {
                        // In case if runtime exceptions.
                    }
                } finally {
                    this.futures.remove(key);
                }
            }
            return true;
        }
        return false;
    }

    public void cleanup(ExecutorService executor) {
        for (TimedFuture<TO> future : this.futures.values()) {
            executor.execute(() -> future.cleanup(this.cleanup));
        }
        this.futures.clear();
    }

    public void cleanupTimedOut(ExecutorService executor, long now) {
        Iterator<TimedFuture<TO>> it = this.futures.values().iterator();
        while (it.hasNext()) {
            TimedFuture<TO> item = it.next();
            if (item.isTimedOut(now)) {
                WebStreamerMod.LOGGER.debug("Cleaning up timed out future: {}", item);
                executor.execute(() -> {
                    try {
                        this.cleanup.accept(item.future.get());
                    } catch (InterruptedException | ExecutionException ignored) { }
                });
                it.remove();
            }
        }
    }

    public void cleanupTimedOut(ExecutorService executor) {
        this.cleanupTimedOut(executor, System.nanoTime());
    }

}
