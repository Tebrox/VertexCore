package de.tebrox.vertexCore.database.migration;

public final class MigrationResult {
    public final long total;
    public final long migrated;
    public final long skipped;
    public final long failed;

    public MigrationResult(long total, long migrated, long skipped, long failed) {
        this.total = total;
        this.migrated = migrated;
        this.skipped = skipped;
        this.failed = failed;
    }
}
