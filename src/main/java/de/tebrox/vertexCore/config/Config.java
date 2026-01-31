package de.tebrox.vertexCore.config;

import de.tebrox.vertexCore.config.annotation.StoreAt;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public final class Config<T extends ConfigObject> {

    private final Plugin plugin;
    private final Class<T> type;
    private final ConfigSchema<T> schema;
    private final File file;

    public Config(Plugin plugin, Class<T> type) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.type = Objects.requireNonNull(type, "type");
        this.schema = ConfigSchema.of(type);
        this.file = resolveFile(plugin, type);
        ensureFolder(plugin);
    }

    public T loadConfigObject() {
        T instance = schema.newInstance();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        schema.applyYamlToObject(yaml, instance, plugin.getLogger());

        //Validation + Fix (warn + default/clamp)
        ConfigValidator.validateAndFix(schema, instance, plugin.getLogger());

        //Save back (adds missing keys/comments; preseves unknown keys)
        saveConfigObject(instance);

        return instance;
    }

    public void saveConfigObject(T instance) {
        Objects.requireNonNull(instance, "instance");
        if (!type.isInstance(instance)) {
            throw new IllegalArgumentException("Instance type mismatch: expected " + type.getName() + " but got " + instance.getClass().getName()
            );
        }

        try {
            YamlConfiguration existingYaml = YamlConfiguration.loadConfiguration(file);
            Map<String, Object> existingMap = YamlMapReader.toMap(existingYaml);

            String out = YamlCommentWriter.write(schema, instance, existingMap);
            FileIO.writeUtf8(file, out);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write config: " + file.getAbsolutePath(), e);
        }
    }

    public File getFile() {
        return file;
    }

    private static void ensureFolder(Plugin plugin) {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new RuntimeException("Failed to create plugin data folder: " + plugin.getDataFolder());
        }
    }

    private static <T extends ConfigObject> File resolveFile(Plugin plugin, Class<T> type) {
        StoreAt storeAt = type.getAnnotation(StoreAt.class);
        if (storeAt == null) {
            throw new IllegalArgumentException(type.getName() + " is missing @StoreAt(\"file.yml\")");
        }
        return new File(plugin.getDataFolder(), storeAt.value());
    }
}
