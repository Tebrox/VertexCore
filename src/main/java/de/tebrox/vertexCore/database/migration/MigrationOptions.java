package de.tebrox.vertexCore.database.migration;

public final class MigrationOptions {
    public boolean overwrite = false;
    public boolean deleteSourceAfter = false;
    public boolean dryRun = false;
    public int batchSize = 200;
}
