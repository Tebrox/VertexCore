package de.tebrox.vertexCore.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class Async {

    private Async() {}

    public static Executor async(Plugin plugin) {
        return runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    public static Executor main(Plugin plugin) {
        return runnable -> Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public static <T> CompletableFuture<T> supplyAsync(Plugin plugin, Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, async(plugin));
    }

    public static CompletableFuture<Void> runAsync(Plugin plugin, Runnable runnable) {
        return CompletableFuture.runAsync(runnable, async(plugin));
    }
}
