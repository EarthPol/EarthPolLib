package com.earthpol.earthpollib.lifecycle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Resources owned by one plugin. Call {@link #close()} from its onDisable method. */
public final class PluginResources implements AutoCloseable {
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private boolean closed;

    /** Registers a resource for cleanup in reverse registration order. */
    public synchronized <T extends AutoCloseable> T register(T resource) {
        Objects.requireNonNull(resource, "resource");
        if (closed) {
            IllegalStateException failure = new IllegalStateException("Plugin resources are closed");
            try {
                resource.close();
            } catch (Exception ex) {
                failure.addSuppressed(ex);
            }
            throw failure;
        }
        resources.push(resource);
        return resource;
    }

    public void onClose(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        register(cleanup::run);
    }

    /** Attempts every cleanup even if one fails. Repeated calls have no effect. */
    @Override
    public void close() {
        Deque<AutoCloseable> pending;
        synchronized (this) {
            if (closed) return;
            closed = true;
            pending = new ArrayDeque<>(resources);
            resources.clear();
        }
        IllegalStateException failure = null;
        for (AutoCloseable resource : pending) {
            try {
                resource.close();
            } catch (Exception ex) {
                if (failure == null) failure = new IllegalStateException("Plugin resource cleanup failed");
                failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }
}
