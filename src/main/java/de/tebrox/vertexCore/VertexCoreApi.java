package de.tebrox.vertexCore;

import de.tebrox.vertexCore.database.*;
import de.tebrox.vertexCore.util.Async;
import de.tebrox.vertexCore.util.AsyncQueue;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Executor;

public final class VertexCoreApi {

    private static VertexCoreApi instance;

    public static VertexCoreApi get() {
        if (instance == null) throw new IllegalStateException("VertexCoreApi not initialized");
        return instance;
    }

    private final Plugin corePlugin;
    private final PluginDataRegistry registry;
    private final DatabaseService db;

    private VertexCoreApi(Plugin corePlugin, PluginDataRegistry registry, DatabaseService db) {
        this.corePlugin = corePlugin;
        this.registry = registry;
        this.db = db;
    }

    public static void init(Plugin corePlugin, PluginDataRegistry registry, DatabaseService db) {
        instance = new VertexCoreApi(corePlugin, registry, db);
    }
    public Plugin getCorePlugin() {
        return corePlugin;
    }

    public JsonCodec json() {
        return db.json();
    }

    public DatabaseBackend backendFor(org.bukkit.plugin.Plugin owner, DatabaseSettings settings) {
        return db.backendFor(owner, settings);
    }

    public AsyncQueue queueFor(Plugin owner, long timeoutMillis) {
        return db.queueFor(owner, timeoutMillis);
    }

    public void closeFor(org.bukkit.plugin.Plugin owner) {
        db.closeFor(owner);
    }

    public Executor asyncExecutor() {
        return Async.async(corePlugin);
    }

    public Executor mainExecutor() {
        return Async.main(corePlugin);
    }

    public DatabaseService databaseService() {
        return db;
    }

    public PluginDataRegistry registry() {
        return registry;
    }

}
