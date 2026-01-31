package de.tebrox.vertexCore.database.migration;

import de.tebrox.vertexCore.database.migration.DatabaseMigrator;
import de.tebrox.vertexCore.database.migration.MigrationOptions;
import de.tebrox.vertexCore.database.migration.MigrationProgress;
import de.tebrox.vertexCore.database.migration.MigrationResult;
import de.tebrox.vertexCore.database.DatabaseBackend;
import de.tebrox.vertexCore.database.internal.TableNamer;

import java.util.ArrayList;
import java.util.List;

public final class DatabaseMigrationRunner {

    private final DatabaseMigrator migrator = new DatabaseMigrator();

    public List<MigrationResult> migrateAll(
            DatabaseBackend source,
            DatabaseBackend target,
            String tablePrefix,
            Class<?>[] dataClasses,
            MigrationOptions opt,
            MigrationProgress progress
    ) {
        List<MigrationResult> results = new ArrayList<>(dataClasses.length);
        for (Class<?> clazz : dataClasses) {
            String table = TableNamer.tableName(tablePrefix, clazz);
            results.add(migrator.migrateTableRaw(source, target, table, opt, progress));
        }
        return results;
    }
}
