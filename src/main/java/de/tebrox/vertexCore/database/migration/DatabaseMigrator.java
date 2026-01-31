package de.tebrox.vertexCore.database.migration;

import de.tebrox.vertexCore.database.DatabaseBackend;

import java.util.List;

public final class DatabaseMigrator {
    public MigrationResult migrateTableRaw(
            DatabaseBackend source,
            DatabaseBackend target,
            String table,
            MigrationOptions opt,
            MigrationProgress progress) {
        List<String[]> rows = source.loadAllRaw(table);
        long total = rows.size();

        if(progress != null) progress.onStart(table, total);

        long migrated = 0;
        long skipped = 0;
        long failed = 0;
        long processed = 0;

        for(String[] row : rows) {
            processed++;

            String id = row[0];
            String json = row[1];

            try {
                boolean exists = target.exists(table, id);

                if (exists && !opt.overwrite) {
                    skipped++;
                } else {
                    if (!opt.dryRun) {
                        target.set(table, id, json);
                        if (opt.deleteSourceAfter) {
                            source.delete(table, id);
                        }
                    }
                    migrated++;
                }
            } catch (Exception e) {
                failed++;
            }

            if (progress != null && opt.batchSize > 0 && (processed % opt.batchSize == 0)) {
                progress.onProgress(table, processed, migrated, skipped, failed);
            }
        }

        if (progress != null) progress.onProgress(table, processed, migrated, skipped, failed);

        MigrationResult result = new MigrationResult(total, migrated, skipped, failed);
        if (progress != null) progress.onDone(table, result);
        return result;
    }
}