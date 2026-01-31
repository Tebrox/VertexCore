package de.tebrox.vertexCore.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class AsyncQueue {
    private final Executor executor;
    private final long timeoutMillis;

    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

    public AsyncQueue(Executor executor, long timeoutMillis) {
        this.executor = executor;
        this.timeoutMillis = timeoutMillis;
    }

    public synchronized <T> CompletableFuture<T> submit(Supplier<T> task) {
        CompletableFuture<T> next = tail.thenComposeAsync(v -> CompletableFuture.supplyAsync(task, executor), executor);

        if(timeoutMillis > 0) {
            next = next.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        tail = next.handle((r, err) -> null);

        return next;
    }

    public synchronized CompletableFuture<Void> submitVoid(Runnable task) {
        return submit(() -> { task.run(); return null; });
    }
}
