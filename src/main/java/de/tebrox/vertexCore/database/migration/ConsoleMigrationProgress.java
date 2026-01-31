package de.tebrox.vertexCore.database.migration;

import java.util.logging.Logger;

public final class ConsoleMigrationProgress implements MigrationProgress {

    private final Logger log;

    public ConsoleMigrationProgress(Logger log) {
        this.log = log;
    }

    @Override
    public void onStart(String table, long total) {
        log.info("[MIGRATE] START table=" + table + " total=" + total);
    }

    @Override
    public void onProgress(String table, long processed, long migrated, long skipped, long failed) {
        log.info("[MIGRATE] table=" + table
                + " processed=" + processed
                + " migrated=" + migrated
                + " skipped=" + skipped
                + " failed=" + failed);
    }

    @Override
    public void onDone(String table, MigrationResult r) {
        log.info("[MIGRATE] DONE table=" + table
                + " total=" + r.total
                + " migrated=" + r.migrated
                + " skipped=" + r.skipped
                + " failed=" + r.failed);
    }
}
