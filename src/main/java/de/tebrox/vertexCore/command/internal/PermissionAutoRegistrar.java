package de.tebrox.vertexCore.command.internal;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class PermissionAutoRegistrar {

    private PermissionAutoRegistrar() {}

    private record PermissionMeta(String node, PermissionDefault def) {}

    public static void registerFromRegistry(Plugin owner, CommandRegistry registry) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(registry, "registry");

        Set<PermissionMeta> metas = collectPermissions(registry);
        if (metas.isEmpty()) return;

        var pm = Bukkit.getPluginManager();

        for (PermissionMeta meta : metas) {
            String node = meta.node();

            if (pm.getPermission(node) != null) {
                continue;
            }

            Permission perm = new Permission(node);

            // Fallback falls irgendwo null reingerät
            PermissionDefault def = meta.def() != null ? meta.def() : PermissionDefault.OP;
            perm.setDefault(def);

            pm.addPermission(perm);
        }
    }

    private static Set<PermissionMeta> collectPermissions(CommandRegistry registry) {
        Set<PermissionMeta> out = new LinkedHashSet<>();
        ArrayDeque<CommandNode> q = new ArrayDeque<>();

        for (CommandNode root : registry.allRoots()) {
            if (root != null) q.add(root);
        }

        while (!q.isEmpty()) {
            CommandNode n = q.removeFirst();

            String perm = n.permission();
            if (perm != null) {
                String trimmed = perm.trim();
                if (!trimmed.isEmpty()) {
                    out.add(new PermissionMeta(trimmed, n.permissionDefault()));
                }
            }

            for (CommandNode child : n.children().values()) {
                if (child != null) q.add(child);
            }
        }

        return out;
    }
}
