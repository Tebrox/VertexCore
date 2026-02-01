package de.tebrox.vertexCore.command.api;

import org.bukkit.plugin.Plugin;

public interface CommandService {
    void register(Plugin owner, Object handler);
    void unregisterAll(Plugin owner);
    void shutdown();
}
