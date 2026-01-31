package de.tebrox.vertexCore.database;

public interface DatabaseSettings {

    // "h2" | "mysql" | "json"
    String backend();

    // Async
    default boolean useQueue() { return true; }
    default long timeoutMillis() { return 5000; }

    // Pool
    default int poolSize() { return 5; }

    // MySQL only
    default String mysqlUrl() { return null; }
    default String mysqlUser() { return null; }
    default String mysqlPassword() { return null; }

    // Optional: Prefix für Tabellen (für mysql/h2)
    default String tablePrefix() { return ""; }
}
