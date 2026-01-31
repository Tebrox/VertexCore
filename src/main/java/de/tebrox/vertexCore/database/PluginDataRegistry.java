package de.tebrox.vertexCore.database;

import de.tebrox.vertexCore.database.DatabaseSettings;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class PluginDataRegistry {

    public record Entry(Supplier<DatabaseSettings> settingsSupplier, Class<?>[] dataClasses) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public void register(Plugin plugin, Supplier<DatabaseSettings> settingsSupplier, Class<?>... dataClasses) {
        entries.put(plugin.getName().toLowerCase(), new Entry(settingsSupplier, dataClasses));
    }

    public Entry get(String pluginName) {
        return entries.get(pluginName.toLowerCase());
    }

    public boolean isRegistered(String pluginName) {
        return entries.containsKey(pluginName.toLowerCase());
    }

    public Set<String> registeredPluginNames() {
        return entries.keySet();
    }
}
