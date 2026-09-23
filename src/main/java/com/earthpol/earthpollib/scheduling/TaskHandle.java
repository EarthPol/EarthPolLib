package com.earthpol.earthpollib.scheduling;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/** A cancellable task. Cancellation does not interrupt an already running action. */
public final class TaskHandle implements AutoCloseable {
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final AtomicReference<ScheduledTask> scheduled = new AtomicReference<>();

    TaskHandle() {
        completion.whenComplete((unused, failure) -> {
            if (failure != null) cancelScheduled();
        });
    }

    /**
     * Completes after a one-shot action, or exceptionally on failure, cancellation, or retirement.
     * Repeating tasks remain pending until stopped. Continuations have no guaranteed thread:
     * schedule any entity/world operations explicitly, including from retirement callbacks.
     */
    public CompletionStage<Void> completion() {
        return completion.minimalCompletionStage();
    }

    public boolean isDone() {
        return completion.isDone();
    }

    public boolean isCancelled() {
        return completion.isCancelled();
    }

    public void cancel() {
        completion.completeExceptionally(new CancellationException("Task cancelled"));
    }

    @Override
    public void close() {
        cancel();
    }

    void bind(ScheduledTask task) {
        if (task == null) {
            retire();
            return;
        }
        scheduled.set(task);
        if (completion.isCompletedExceptionally()) cancelScheduled();
    }

    void retire() {
        completion.completeExceptionally(new CancellationException("Entity scheduler retired"));
    }

    void fail(Throwable failure) {
        completion.completeExceptionally(failure);
    }

    void execute(Runnable action, boolean repeating) {
        if (isDone()) return;
        try {
            action.run();
            if (!repeating) completion.complete(null);
        } catch (RuntimeException | Error ex) {
            fail(ex);
            throw ex;
        }
    }

    private void cancelScheduled() {
        ScheduledTask task = scheduled.get();
        if (task != null) task.cancel();
    }
}
