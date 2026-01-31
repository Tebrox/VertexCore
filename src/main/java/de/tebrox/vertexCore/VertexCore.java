package de.tebrox.vertexCore;

import de.tebrox.vertexCore.command.VertexCoreCommand;
import de.tebrox.vertexCore.database.DatabaseService;
import de.tebrox.vertexCore.database.PluginDataRegistry;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VertexCore extends JavaPlugin {

    @Override
    public void onEnable() {
        PluginDataRegistry registry = new PluginDataRegistry();
        DatabaseService db = new DatabaseService(this, registry);
        VertexCoreApi.init(this, registry, db);

        PluginCommand cmd = getCommand("vertexcore");
        if(cmd != null) {
            VertexCoreCommand handler = new VertexCoreCommand(this, VertexCoreApi.get().registry());
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        getLogger().info("VertexCore enabled.");

    }

    @Override
    public void onDisable() {
        getLogger().info("VertexCore disabled.");
    }
}
