package de.tebrox.vertexCore.database;

import java.util.List;

public interface DatabaseBackend extends AutoCloseable {
    String get(String table, String uniqueId);
    void set(String table, String uniqueId, String json);
    void delete(String table, String uniqueId);
    boolean exists(String table, String uniqueId);

    List<String[]> loadAllRaw(String table); // each entry: [uniqueId, json]

    default void warmup() {}

    @Override void close();
}
