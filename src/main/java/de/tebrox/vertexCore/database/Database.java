package de.tebrox.vertexCore.database;

import de.tebrox.vertexCore.database.DataObject;
import de.tebrox.vertexCore.database.DatabaseSettings;
import de.tebrox.vertexCore.VertexCoreApi;
import de.tebrox.vertexCore.database.internal.TableNamer;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class Database<T extends DataObject> implements AutoCloseable {

    private final Plugin owner;
    private final DatabaseSettings settings;
    private final Class<T> type;
    private final String table;

    public Database(Plugin owner, DatabaseSettings settings, Class<T> type) {
        this.owner = owner;
        this.settings = settings;
        this.type = type;
        this.table = TableNamer.tableName(settings.tablePrefix(), type);
    }

    public void saveObject(T obj) {
        String json = VertexCoreApi.get().json().toJson(type, obj);
        VertexCoreApi.get().backendFor(owner, settings).set(table, obj.getUniqueId(), json);
    }

    public T loadObject(String uniqueId) {
        String json = VertexCoreApi.get().backendFor(owner, settings).get(table, uniqueId);
        if (json == null) return null;

        T obj = VertexCoreApi.get().json().fromJson(type, json);
        obj.setUniqueId(uniqueId);
        return obj;
    }

    public boolean objectExists(String uniqueId) {
        return VertexCoreApi.get().backendFor(owner, settings).exists(table, uniqueId);
    }

    public void deleteObject(String uniqueId) {
        VertexCoreApi.get().backendFor(owner, settings).delete(table, uniqueId);
    }

    public List<T> loadObjects() {
        List<String[]> rows = VertexCoreApi.get().backendFor(owner, settings).loadAllRaw(table);
        List<T> out = new ArrayList<>(rows.size());
        for (String[] row : rows) {
            String id = row[0];
            String json = row[1];
            T obj = VertexCoreApi.get().json().fromJson(type, json);
            obj.setUniqueId(id);
            out.add(obj);
        }
        return out;
    }

    // ---------------- async core: CompletableFuture ----------------

    public CompletableFuture<T> loadObjectAsync(String uniqueId) {
        if (settings.useQueue()) {
            return VertexCoreApi.get().databaseService()
                    .queueFor(owner, settings.timeoutMillis())
                    .submit(() -> loadObject(uniqueId));
        }
        return CompletableFuture.supplyAsync(() -> loadObject(uniqueId), VertexCoreApi.get().asyncExecutor())
                .orTimeout(settings.timeoutMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public CompletableFuture<Void> saveObjectAsync(T obj) {
        if (settings.useQueue()) {
            return VertexCoreApi.get().databaseService()
                    .queueFor(owner, settings.timeoutMillis())
                    .submitVoid(() -> saveObject(obj));
        }
        CompletableFuture<Void> f = CompletableFuture.runAsync(() -> saveObject(obj), VertexCoreApi.get().asyncExecutor());
        return settings.timeoutMillis() > 0 ? f.orTimeout(settings.timeoutMillis(), java.util.concurrent.TimeUnit.MILLISECONDS) : f;
    }

    public CompletableFuture<List<T>> loadObjectsAsync() {
        if (settings.useQueue()) {
            return VertexCoreApi.get().databaseService()
                    .queueFor(owner, settings.timeoutMillis())
                    .submit(this::loadObjects);
        }
        CompletableFuture<List<T>> f = CompletableFuture.supplyAsync(this::loadObjects, VertexCoreApi.get().asyncExecutor());
        return settings.timeoutMillis() > 0 ? f.orTimeout(settings.timeoutMillis(), java.util.concurrent.TimeUnit.MILLISECONDS) : f;
    }

    public CompletableFuture<Void> deleteObjectAsync(String uniqueId) {
        if (settings.useQueue()) {
            return VertexCoreApi.get().databaseService()
                    .queueFor(owner, settings.timeoutMillis())
                    .submitVoid(() -> deleteObject(uniqueId));
        }
        CompletableFuture<Void> f = CompletableFuture.runAsync(() -> deleteObject(uniqueId), VertexCoreApi.get().asyncExecutor());
        return settings.timeoutMillis() > 0 ? f.orTimeout(settings.timeoutMillis(), java.util.concurrent.TimeUnit.MILLISECONDS) : f;
    }


    // ---------------- convenience: deliver result on main thread ----------------

    public void loadObjectAsyncMain(String uniqueId, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        loadObjectAsync(uniqueId).whenComplete((result, err) ->
                CompletableFuture.runAsync(() -> {
                    if (err != null) onError.accept(unwrap(err));
                    else onSuccess.accept(result);
                }, VertexCoreApi.get().mainExecutor())
        );
    }

    public void loadObjectsAsyncMain(Consumer<List<T>> onSuccess, Consumer<Throwable> onError) {
        loadObjectsAsync().whenComplete((result, err) ->
                CompletableFuture.runAsync(() -> {
                    if (err != null) onError.accept(unwrap(err));
                    else onSuccess.accept(result);
                }, VertexCoreApi.get().mainExecutor())
        );
    }

    public void saveObjectAsyncMain(T obj, Runnable onSuccess, Consumer<Throwable> onError) {
        saveObjectAsync(obj).whenComplete((v, err) ->
                CompletableFuture.runAsync(() -> {
                    if (err != null) onError.accept(unwrap(err));
                    else onSuccess.run();
                }, VertexCoreApi.get().mainExecutor())
        );
    }

    // Optional: generic helper
    public <R> void supplyAsyncMain(CompletableFuture<R> future, BiConsumer<R, Throwable> callbackOnMain) {
        future.whenComplete((r, err) ->
                CompletableFuture.runAsync(() -> callbackOnMain.accept(r, err == null ? null : unwrap(err)),
                        VertexCoreApi.get().mainExecutor())
        );
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null) return ce.getCause();
        if (t instanceof java.util.concurrent.ExecutionException ee && ee.getCause() != null) return ee.getCause();
        return t;
    }

    @Override
    public void close() {
        // optional: pool für das plugin schließen
        VertexCoreApi.get().closeFor(owner);
    }
}
