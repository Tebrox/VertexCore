package de.tebrox.vertexCore;

import de.tebrox.vertexCore.command.VertexCoreAdminCommands;
import de.tebrox.vertexCore.command.VertexCoreCommand;
import de.tebrox.vertexCore.command.internal.CommandServiceImpl;
import de.tebrox.vertexCore.database.DatabaseService;
import de.tebrox.vertexCore.database.PluginDataRegistry;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VertexCore extends JavaPlugin {

    private CommandServiceImpl commandService;

    @Override
    public void onEnable() {
        PluginDataRegistry registry = new PluginDataRegistry();
        DatabaseService db = new DatabaseService(this, registry);

        this.commandService = new CommandServiceImpl();

        VertexCoreApi.init(this, registry, db, this.commandService);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            this.commandService.registerInto(event.registrar());
        });

        VertexCoreApi.get().commands().register(this, new VertexCoreAdminCommands(this, registry));

        getLogger().info("VertexCore enabled.");

    }

    @Override
    public void onDisable() {
        if(this.commandService != null) this.commandService.shutdown();

        getLogger().info("VertexCore disabled.");
    }
}
