package de.tebrox.vertexCore.database.migration;

public interface MigrationProgress {
    void onStart(String table, long total);
    void onProgress(String table, long processed, long migrated, long skipped, long failed);
    void onDone(String table, MigrationResult result);
}
