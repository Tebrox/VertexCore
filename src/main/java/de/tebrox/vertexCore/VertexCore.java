package de.tebrox.vertexCore;

import org.bukkit.plugin.java.JavaPlugin;

public final class VertexCore extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("VertexCore enabled.");

    }

    @Override
    public void onDisable() {
        getLogger().info("VertexCore disabled.");
    }
}
