package de.tebrox.vertexCore.command.internal;

import java.util.*;

public final class CommandRegistry {

    private final Map<String, CommandNode> roots = new HashMap<>();

    public CommandNode getRoot(String name) {
        if (name == null) return null;
        return roots.get(name.toLowerCase(Locale.ROOT));
    }

    public void putRoot(String name, CommandNode node) {
        roots.put(name.toLowerCase(Locale.ROOT), node);
    }

    public Collection<CommandNode> allRoots() {
        return roots.values();
    }
}
