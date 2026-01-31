package de.tebrox.vertexCore.database.backend;

import de.tebrox.vertexCore.database.DatabaseBackend;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class FlatfileDatabaseBackend implements DatabaseBackend {

    public static FlatfileDatabaseBackend start(Plugin owner) {
        // ✅ Root immer "data"
        File root = new File(owner.getDataFolder(), "data");
        if (!root.exists() && !root.mkdirs()) {
            throw new RuntimeException("Failed to create json root: " + root.getAbsolutePath());
        }
        return new FlatfileDatabaseBackend(root);
    }

    private final File root;

    private FlatfileDatabaseBackend(File root) {
        this.root = root;
    }

    @Override
    public String get(String table, String uniqueId) {
        File f = file(table, uniqueId);
        if (!f.exists()) return null;
        try {
            return Files.readString(f.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read: " + f.getAbsolutePath(), e);
        }
    }

    @Override
    public void set(String table, String uniqueId, String json) {
        File f = file(table, uniqueId);
        File parent = f.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("Failed to create folder: " + parent.getAbsolutePath());
        }
        try {
            Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write: " + f.getAbsolutePath(), e);
        }
    }

    @Override
    public void delete(String table, String uniqueId) {
        File f = file(table, uniqueId);
        if (f.exists()) f.delete();
    }

    @Override
    public boolean exists(String table, String uniqueId) {
        return file(table, uniqueId).exists();
    }

    @Override
    public List<String[]> loadAllRaw(String table) {
        File dir = tableDir(table);
        List<String[]> out = new ArrayList<>();
        if (!dir.exists()) return out;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return out;

        for (File f : files) {
            try {
                String id = f.getName().substring(0, f.getName().length() - 5);
                String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                out.add(new String[]{id, json});
            } catch (Exception e) {
                throw new RuntimeException("Failed to read: " + f.getAbsolutePath(), e);
            }
        }
        return out;
    }

    private File tableDir(String table) {
        // ✅ pro Database-Klasse ein Unterordner mit ihrem Namen (table)
        return new File(root, sanitize(table));
    }

    private File file(String table, String uniqueId) {
        return new File(tableDir(table), sanitize(uniqueId) + ".json");
    }

    private static String sanitize(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9._-]", "_");
    }

    @Override
    public void close() {}
}
