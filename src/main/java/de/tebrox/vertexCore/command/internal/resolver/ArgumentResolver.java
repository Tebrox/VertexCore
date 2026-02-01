package de.tebrox.vertexCore.command.internal.resolver;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface ArgumentResolver<T> {
    Class<T> type();
    T parse(CommandSender sender, String input) throws IllegalArgumentException;

    default List<String> suggest(CommandSender sender, String prefix) {
        return List.of();
    }
}
