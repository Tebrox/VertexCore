package de.tebrox.vertexCore.database;

import de.tebrox.vertexCore.database.backend.FlatfileDatabaseBackend;
import de.tebrox.vertexCore.database.backend.JdbcDatabaseBackend;
import de.tebrox.vertexCore.util.AsyncQueue;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseService implements Listener {
    private final Plugin core;
    private final JsonCodec json = new JsonCodec();
    private final PluginDataRegistry registry;

    private final Map<String, DatabaseBackend> backends = new ConcurrentHashMap<>();
    private final Map<String, AsyncQueue> queues = new ConcurrentHashMap<>();

    public DatabaseService(Plugin core, PluginDataRegistry registry) {
        this.core = core;
        this.registry = registry;
        Bukkit.getPluginManager().registerEvents(this, core);
    }

    public JsonCodec json() {
        return json;
    }

    public DatabaseBackend backendFor(Plugin owner, DatabaseSettings settings) {
        String fp = fingerprint(owner, settings);

        return backends.computeIfAbsent(fp, k -> {
            return switch (settings.backend().toLowerCase()) {
                case "json" -> FlatfileDatabaseBackend.start(owner);
                case "h2" -> new JdbcDatabaseBackend(JdbcDatabaseBackend.createDataSource(owner, settings), /*dialect*/ "h2");
                case "mysql" -> new JdbcDatabaseBackend(JdbcDatabaseBackend.createDataSource(owner, settings), /*dialect*/ "mysql");
                default -> throw new IllegalArgumentException("Unknown backend: " + settings.backend());
            };
        });
    }

    public AsyncQueue queueFor(Plugin owner, long timeoutMillis) {
        String key = owner.getName().toLowerCase();
        return queues.computeIfAbsent(key, k -> new AsyncQueue(r -> Bukkit.getScheduler().runTaskAsynchronously(core, r), timeoutMillis));
    }

    public void closeFor(Plugin owner) {
        String prefix = owner.getName().toLowerCase() + "|";
        backends.entrySet().removeIf(e -> {
            if(e.getKey().startsWith(prefix)) {
                e.getValue().close();
                return true;
            }
            return false;
        });
        queues.remove(owner.getName().toLowerCase());
    }

    public CompletableFuture<Void> warmupFor(Plugin plugin) {
        PluginDataRegistry.Entry entry = registry.get(plugin.getName());
        if (entry == null) return CompletableFuture.completedFuture(null);

        DatabaseSettings settings = entry.settingsSupplier().get();

        return CompletableFuture.runAsync(() -> {
            DatabaseBackend backend = backendFor(plugin, settings);
            backend.warmup();
            plugin.getLogger().info("[VertexCore] Database warmup done (" + settings.backend() + ")");
        }, r -> Bukkit.getScheduler().runTaskAsynchronously(core, r));
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        Plugin p = event.getPlugin();
        if(!registry.isRegistered(p.getName())) return;

        warmupFor(p).exceptionally(err -> {
            Throwable u = unwrap(err);
            p.getLogger().severe("[VertexCore] Database warmup failed: " + u.getClass().getName()
                    + (u.getMessage() != null ? " - " + u.getMessage() : ""));
            u.printStackTrace();
            return null;
        });
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        closeFor(event.getPlugin());
    }

    public void closeAll() {
        backends.values().forEach(DatabaseBackend::close);
        backends.clear();
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null) return ce.getCause();
        if (t instanceof java.util.concurrent.ExecutionException ee && ee.getCause() != null) return ee.getCause();
        return t;
    }

    private static String fingerprint(Plugin owner, DatabaseSettings s) {
        String b = s.backend().toLowerCase();
        String base = owner.getName().toLowerCase() + "|" + b + "|" + s.poolSize() + "|" + s.tablePrefix();

        if (b.equals("mysql")) {
            return base + "|" + s.mysqlUrl() + "|" + s.mysqlUser();
        }
        return base;
    }

}
