package de.tebrox.vertexCore.command.paper;

import de.tebrox.vertexCore.command.internal.CommandNode;
import de.tebrox.vertexCore.command.internal.ExecutionEngine;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public final class VertexBasicCommand implements BasicCommand {

    private final Plugin owner;
    private final String label;
    private final Supplier<CommandNode> rootSupplier;
    private final ExecutionEngine engine;

    public VertexBasicCommand(Plugin owner, String label, Supplier<CommandNode> rootSupplier, ExecutionEngine engine) {
        this.owner = owner;
        this.label = label;
        this.rootSupplier = rootSupplier;
        this.engine = engine;
    }
    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        CommandNode root = rootSupplier.get();
        if(root == null) return;

        CommandSender sender = commandSourceStack.getSender();
        engine.execute(owner, sender, label, root, args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        CommandNode root = rootSupplier.get();
        if(root == null) return List.of();

        CommandSender sender = commandSourceStack.getSender();
        return engine.suggest(owner, sender, root, args);
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return true;
    }

    @Override
    public @Nullable String permission() {
        return null;
    }
}
