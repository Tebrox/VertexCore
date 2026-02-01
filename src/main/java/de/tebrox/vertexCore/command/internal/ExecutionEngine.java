package de.tebrox.vertexCore.command.internal;

import de.tebrox.vertexCore.command.api.CommandContext;
import de.tebrox.vertexCore.command.api.VisibilityPolicy;
import de.tebrox.vertexCore.command.internal.ConsumerConfigReader.NodeConfigSource;
import de.tebrox.vertexCore.command.internal.resolver.ArgumentResolver;
import de.tebrox.vertexCore.command.internal.resolver.ResolverRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;

public final class ExecutionEngine {

    private final ResolverRegistry resolvers;
    private final ConsumerConfigReader configReader;

    public ExecutionEngine(ResolverRegistry resolvers, ConsumerConfigReader configReader) {
        this.resolvers = resolvers;
        this.configReader = configReader;
    }

    public void execute(Plugin owner, CommandSender sender, String label, CommandNode root, String[] args) {
        if (root == null) {
            sender.sendMessage("This command is not available.");
            return;
        }

        int index = 0;
        CommandNode node = root;

        while (index < args.length) {
            CommandNode next = node.findChild(args[index]);
            if (next == null) break;
            node = next;
            index++;
        }

        if (node.hasExecutor()) {
            if (!isEnabled(owner, root, node)) {
                sender.sendMessage(disabledMessage(node));
                return;
            }

            if (node.playerOnly() && !(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return;
            }

            if (!canUse(sender, node)) {
                sender.sendMessage("No permission.");
                return;
            }

            String[] remaining = Arrays.copyOfRange(args, index, args.length);
            invoke(sender, label, node, remaining);
            return;
        }

        sender.sendMessage("Usage: /" + label + " <subcommand>");
        List<String> visibleSubs = visibleChildren(owner, sender, root, node);
        if (!visibleSubs.isEmpty()) sender.sendMessage("Available: " + String.join(", ", visibleSubs));
    }

    public List<String> suggest(Plugin owner, CommandSender sender, CommandNode root, String[] args) {
        if (root == null) return List.of();

        int index = 0;
        CommandNode node = root;

        // Walk known child literals
        while (index < args.length) {
            CommandNode next = node.findChild(args[index]);
            if (next == null) break;
            node = next;
            index++;
        }

        // Determine prefix (current token)
        String prefix = (args.length == 0) ? "" : args[args.length - 1];

        // 1) Custom suggester always wins (1:1 UX hooks)
        CommandSuggester custom = node.suggester();
        if (custom != null) {
            // If you want the *used* alias later, pass it from VertexBasicCommand.
            return custom.suggest(sender, root.name(), args);
        }

        // 2) IMPORTANT: suggest subcommand literals even if node has an executor.
        // We are "typing a subcommand literal" when:
        // - we haven't matched all args to nodes (index < args.length) -> current token is not a known child yet
        // - OR we are at a boundary where the next token would be a child (prefix is empty / user just typed space)
        //
        // Example cases:
        // - "/v <tab>" -> index=0, node=root, children exist -> suggest children
        // - "/v o<tab>" -> index=0 (no match yet), node=root -> suggest children filtered by "o"
        // - "/v open <tab>" -> index=1, node=open -> now we're in args area (children typically none)
        boolean typingChildLiteral = (index < args.length); // current token didn't match a child
        boolean hasChildren = !node.children().isEmpty();

        if (hasChildren && (args.length == 0 || typingChildLiteral)) {
            return suggestChildLiterals(owner, sender, root, node, prefix);
        }

        // 3) If node has no executor, nothing else to suggest
        if (!node.hasExecutor()) {
            return List.of();
        }

        // 4) Argument suggestions (resolvers)
        if (!isEnabled(owner, root, node) && node.hideWhenDisabled()) return List.of();
        if (node.playerOnly() && !(sender instanceof Player) && node.hideFromConsole()) return List.of();
        if (!canUse(sender, node)) return List.of();

        // argIndex = how many non-literal args are already consumed
        int argIndex = args.length - index;
        List<ParamSpec> params = node.params();

        // If we are currently typing the first argument, argIndex might be 1 while params start at 0.
        // Example: "/v open <tab>" -> index=1, args.length=2 (["open",""]) on some clients,
        // Paper may or may not include empty token. This makes it safe:
        if (argIndex < 0) argIndex = 0;
        if (argIndex > 0 && (prefix == null || prefix.isEmpty())) {
            // when a new token is starting, we want the next param
            // (keeps behaviour stable even if Paper gives empty last token)
            // But do NOT decrement below 0.
            argIndex = Math.max(0, argIndex - 1);
        }

        if (argIndex >= params.size()) return List.of();

        ParamSpec ps = params.get(argIndex);
        ArgumentResolver<?> r = resolvers.resolverFor(ps.type());
        if (r == null) return List.of();

        return r.suggest(sender, prefix == null ? "" : prefix);
    }


    private void invoke(CommandSender sender, String label, CommandNode node, String[] remaining) {
        Method m = node.method();
        Object handler = node.handler();

        List<ParamSpec> specs = node.params();
        Object[] invokeArgs = new Object[1 + specs.size()];
        invokeArgs[0] = new CommandContext(sender, label, remaining);

        for (int i = 0; i < specs.size(); i++) {
            ParamSpec spec = specs.get(i);
            String token = (i < remaining.length) ? remaining[i] : null;

            if (token == null) {
                if (spec.optional()) {
                    invokeArgs[i + 1] = defaultValue(spec.type());
                    continue;
                }
                sender.sendMessage("Missing argument: " + spec.name());
                return;
            }

            ArgumentResolver<?> resolver = resolvers.resolverFor(spec.type());
            if (resolver == null) {
                sender.sendMessage("No resolver for: " + spec.type().getSimpleName());
                return;
            }

            try {
                invokeArgs[i + 1] = resolver.parse(sender, token);
            } catch (IllegalArgumentException ex) {
                sender.sendMessage("Invalid " + spec.name() + ": " + ex.getMessage());
                return;
            }
        }

        try {
            m.invoke(handler, invokeArgs);
        } catch (Throwable t) {
            sender.sendMessage("An error occurred while executing this command.");
            t.printStackTrace();
        }
    }

    // ---------------- enabled / visibility ----------------

    private boolean isEnabled(Plugin owner, CommandNode root, CommandNode node) {
        String template = node.enabledConfigPath();
        if (template != null && !template.isBlank()) {
            String path = template.replace("{root}", root.name());
            NodeConfigSource src = new NodeConfigSource(root.consumerConfigClass(), root.consumerConfigFile());
            Boolean cfg = configReader.readBooleanOrNull(owner, src, path);
            if (cfg != null) return cfg;
        }
        return node.enabledDefault();
    }

    private static String disabledMessage(CommandNode node) {
        String msg = node.disabledMessage();
        if (msg != null && !msg.isBlank()) return msg;
        return "This command is currently disabled.";
    }

    private static boolean canUse(CommandSender sender, CommandNode node) {
        String perm = node.permission();
        if (perm == null || perm.isBlank()) return true;
        return sender.hasPermission(perm);
    }

    private boolean isVisible(Plugin owner, CommandSender sender, CommandNode root, CommandNode node) {
        if (!isEnabled(owner, root, node) && node.hideWhenDisabled()) return false;
        if (node.playerOnly() && !(sender instanceof Player) && node.hideFromConsole()) return false;

        return switch (node.visibility()) {
            case ALWAYS -> true;
            case HIDDEN -> false;
            case IF_EXECUTABLE -> {
                if (!isEnabled(owner, root, node)) yield false;
                if (node.playerOnly() && !(sender instanceof Player)) yield false;
                yield canUse(sender, node);
            }
        };
    }

    private List<String> visibleChildren(Plugin owner, CommandSender sender, CommandNode root, CommandNode node) {
        List<String> out = new ArrayList<>();
        for (CommandNode c : node.children().values()) {
            if (isVisible(owner, sender, root, c)) out.add(c.name());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private List<String> suggestChildLiterals(Plugin owner, CommandSender sender, CommandNode root, CommandNode node, String prefix) {
        String pre = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (CommandNode c : node.children().values()) {
            if (!isVisible(owner, sender, root, c)) continue;
            if (c.name().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(c.name());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == double.class) return 0d;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
