package de.tebrox.vertexCore.command.internal;

import org.bukkit.command.CommandSender;

import java.util.List;

@FunctionalInterface
public interface CommandSuggester {
    List<String> suggest(CommandSender sender, String alias, String[] args);
}
