package de.tebrox.vertexCore.command.internal;

import de.tebrox.vertexCore.command.api.CommandService;
import de.tebrox.vertexCore.command.internal.ConsumerConfigReader.NodeConfigSource;
import de.tebrox.vertexCore.command.internal.resolver.BuiltInResolvers;
import de.tebrox.vertexCore.command.internal.resolver.ResolverRegistry;
import de.tebrox.vertexCore.command.paper.VertexBasicCommand;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandServiceImpl implements CommandService {

    private final Map<Plugin, CommandRegistry> byOwner = new ConcurrentHashMap<>();
    private final Map<String, Plugin> rootOwner = new ConcurrentHashMap<>();

    private final AnnotationParser parser = new AnnotationParser();
    private final ResolverRegistry resolvers = new ResolverRegistry();
    private final ConsumerConfigReader consumerConfigReader = new ConsumerConfigReader();
    private final ExecutionEngine engine;

    public CommandServiceImpl() {
        BuiltInResolvers.registerAll(resolvers);
        this.engine = new ExecutionEngine(resolvers, consumerConfigReader);
    }

    @Override
    public void register(Plugin owner, Object handler) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(handler, "handler");

        CommandRegistry parsed = parser.parse(handler);
        PermissionAutoRegistrar.registerFromRegistry(owner, parsed);

        // unique roots across plugins
        for (CommandNode root : parsed.allRoots()) {
            String key = root.name().toLowerCase(Locale.ROOT);
            Plugin existing = rootOwner.putIfAbsent(key, owner);
            if (existing != null && existing != owner) {
                throw new IllegalStateException("Root command '" + root.name() + "' already registered by: " + existing.getName());
            }
        }

        CommandRegistry current = byOwner.computeIfAbsent(owner, p -> new CommandRegistry());
        for (CommandNode root : parsed.allRoots()) {
            current.putRoot(root.name(), root);
        }
    }

    @Override
    public void unregisterAll(Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        CommandRegistry removed = byOwner.remove(owner);
        if (removed != null) {
            for (CommandNode root : removed.allRoots()) {
                rootOwner.remove(root.name().toLowerCase(Locale.ROOT), owner);
            }
        }
        // Paper registry cannot be hard-unregistered cleanly at runtime:
        // Root supplier becomes null -> inert.
    }

    @Override
    public void shutdown() {
        byOwner.clear();
        rootOwner.clear();
    }

    // Called by VertexCore LifecycleEvents.COMMANDS handler
    public void registerInto(Commands registrar) {
        Map<String, RootInfo> roots = snapshotRoots();

        for (RootInfo info : roots.values()) {
            registrar.register(
                    info.name,
                    info.description == null ? "" : info.description,
                    info.aliases,
                    new VertexBasicCommand(info.owner, info.name, info.rootSupplier, engine)
            );
        }
    }

    private Map<String, RootInfo> snapshotRoots() {
        Map<String, RootInfo> out = new HashMap<>();

        for (Map.Entry<Plugin, CommandRegistry> e : byOwner.entrySet()) {
            Plugin owner = e.getKey();
            CommandRegistry reg = e.getValue();

            for (CommandNode root : reg.allRoots()) {
                String rootName = root.name();
                String key = rootName.toLowerCase(Locale.ROOT);

                // resolve aliases (config wins, otherwise @VAlias + @VRootToggle aliases)
                List<String> resolvedAliases = resolveRootAliases(owner, root);

                // primary label + paper aliases (disablePrimary support)
                String primaryLabel = rootName;
                List<String> paperAliases = resolvedAliases;

                boolean disablePrimary = resolveDisablePrimary(owner, root);

                if (disablePrimary) {
                    if (paperAliases.isEmpty()) {
                        // SOFT FAIL
                        owner.getLogger().warning("[VertexCore] Command '" + rootName
                                + "' has disablePrimary=true but no aliases were found. Falling back to primary command registration.");
                        primaryLabel = rootName;
                        paperAliases = resolvedAliases; // empty
                    } else {
                        primaryLabel = paperAliases.get(0);
                        List<String> rest = new ArrayList<>();
                        for (String a : paperAliases) {
                            if (a == null) continue;
                            if (!a.equalsIgnoreCase(primaryLabel)) rest.add(a);
                        }
                        paperAliases = rest;
                        // IMPORTANT: rootName is NOT registered as alias -> /rootName does not exist.
                    }
                }

                // supplier always returns the "real" root node by its actual root name
                out.putIfAbsent(key, new RootInfo(
                        owner,
                        primaryLabel,
                        root.description(),
                        paperAliases,
                        () -> {
                            CommandRegistry r = byOwner.get(owner);
                            return (r == null) ? null : r.getRoot(rootName);
                        }
                ));
            }
        }

        return out;
    }

    private List<String> resolveRootAliases(Plugin owner, CommandNode root) {
        // 1) Consumer main config stringList (if present)
        String template = root.aliasPathTemplate();
        if (template != null && !template.isBlank()) {
            String path = template.replace("{root}", root.name());
            NodeConfigSource src = new NodeConfigSource(root.consumerConfigClass(), root.consumerConfigFile());
            List<String> fromCfg = consumerConfigReader.readStringList(owner, src, path);
            List<String> sanitized = sanitizeAliases(fromCfg);
            if (!sanitized.isEmpty()) return sanitized;
        }

        // 2) Fallback: @VAlias on root + @VRootToggle(aliases=...)
        List<String> fallback = new ArrayList<>();
        fallback.addAll(root.aliases());
        fallback.addAll(root.extraAliases());
        return sanitizeAliases(fallback);
    }

    private static List<String> sanitizeAliases(List<String> list) {
        if (list == null || list.isEmpty()) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : list) {
            if (s == null) continue;
            String a = s.trim();
            if (!a.isEmpty()) out.add(a);
        }
        return out.isEmpty() ? List.of() : new ArrayList<>(out);
    }

    private boolean resolveDisablePrimary(Plugin owner, CommandNode root) {
        String template = root.disablePrimaryPathTemplate();
        if (template != null && !template.isBlank()) {
            String path = template.replace("{root}", root.name());
            NodeConfigSource src = new NodeConfigSource(root.consumerConfigClass(), root.consumerConfigFile());
            Boolean cfg = consumerConfigReader.readBooleanOrNull(owner, src, path);
            if (cfg != null) return cfg;
        }
        return root.disablePrimary();
    }


    private static final class RootInfo {
        final Plugin owner;
        final String name;
        final String description;
        final List<String> aliases;
        final java.util.function.Supplier<CommandNode> rootSupplier;

        RootInfo(Plugin owner, String name, String description, List<String> aliases, java.util.function.Supplier<CommandNode> rootSupplier) {
            this.owner = owner;
            this.name = name;
            this.description = description;
            this.aliases = aliases;
            this.rootSupplier = rootSupplier;
        }
    }
}
