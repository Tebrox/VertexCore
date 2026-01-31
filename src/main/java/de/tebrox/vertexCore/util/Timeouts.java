package de.tebrox.vertexCore.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class Timeouts {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "VertexCore-Timeouts");
        t.setDaemon(true);

        return t;
    });

    private Timeouts() {}

    public static ScheduledExecutorService scheduler() {
        return SCHEDULER;
    }

    public static void shutdown() {
        SCHEDULER.shutdownNow();
    }
}
