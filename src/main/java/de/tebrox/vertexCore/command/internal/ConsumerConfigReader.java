package de.tebrox.vertexCore.command.internal;

import de.tebrox.vertexCore.config.ConfigObject;
import de.tebrox.vertexCore.config.annotation.StoreAt;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.Objects;

public final class ConsumerConfigReader {

    public record NodeConfigSource(Class<? extends ConfigObject> configClass, String fileNameOverride) {}

    public List<String> readStringList(Plugin owner, NodeConfigSource source, String path) {
        YamlConfiguration yml = loadYaml(owner, source);
        if (yml == null) return List.of();
        List<String> list = yml.getStringList(path);
        return list == null ? List.of() : list;
    }

    public Boolean readBooleanOrNull(Plugin owner, NodeConfigSource source, String path) {
        YamlConfiguration yml = loadYaml(owner, source);
        if (yml == null) return null;
        if (!yml.contains(path)) return null;
        return yml.getBoolean(path);
    }

    private YamlConfiguration loadYaml(Plugin owner, NodeConfigSource source) {
        Objects.requireNonNull(owner, "owner");
        File f = resolveFile(owner, source);
        return YamlConfiguration.loadConfiguration(f);
    }

    private File resolveFile(Plugin owner, NodeConfigSource source) {
        if (source != null && source.fileNameOverride() != null && !source.fileNameOverride().isBlank()) {
            return new File(owner.getDataFolder(), source.fileNameOverride().trim());
        }

        if (source != null && source.configClass() != null && source.configClass() != ConfigObject.class) {
            StoreAt storeAt = source.configClass().getAnnotation(StoreAt.class);
            if (storeAt == null) {
                throw new IllegalArgumentException(source.configClass().getName() + " is missing @StoreAt(\"file.yml\")");
            }
            return new File(owner.getDataFolder(), storeAt.value());
        }

        return new File(owner.getDataFolder(), "config.yml");
    }
}
