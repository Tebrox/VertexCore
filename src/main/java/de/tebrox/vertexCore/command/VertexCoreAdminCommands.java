package de.tebrox.vertexCore.command;

import de.tebrox.vertexCore.VertexCoreApi;
import de.tebrox.vertexCore.command.annotation.*;
import de.tebrox.vertexCore.command.api.CommandContext;
import de.tebrox.vertexCore.command.api.VisibilityPolicy;
import de.tebrox.vertexCore.database.DatabaseBackend;
import de.tebrox.vertexCore.database.DatabaseSettings;
import de.tebrox.vertexCore.database.PluginDataRegistry;
import de.tebrox.vertexCore.database.migration.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

@VAliasConfigSource(
        // VertexCore eigene config class wäre optional; sonst default config.yml
        rootAliasesPath = "commands.{root}.aliases"
)
public final class VertexCoreAdminCommands {

    private static final List<String> BACKENDS = List.of("json", "h2", "mysql");
    private static final List<String> FLAGS = List.of("--dry-run", "--overwrite", "--delete-source", "--confirm");

    private final Plugin corePlugin;
    private final PluginDataRegistry registry;

    public VertexCoreAdminCommands(Plugin corePlugin, PluginDataRegistry registry) {
        this.corePlugin = corePlugin;
        this.registry = registry;
    }

    @VCommand("vertexcore")
    @VAlias({"vc"})
    @VDesc("VertexCore admin commands")
    public void root(CommandContext ctx) {
        ctx.reply("Usage: /" + ctx.label() + " migrate <plugin> <from> <to> [--dry-run] [--overwrite] [--delete-source --confirm]");
        ctx.reply("Backends: json | h2 | mysql");
    }

    @VSub("vertexcore migrate")
    @VDesc("Migrate VertexCore database data for a plugin")
    @VPerm(value = "vertexcore.migrate", visibility = VisibilityPolicy.IF_EXECUTABLE)
    public void migrate(CommandContext ctx) {
        // We parse raw args manually to keep your existing UX/flags behavior intact.
        // rawArgs includes everything after the registered label (e.g. "migrate ...").
        String[] args = ctx.rawArgs();

        // expected: migrate <plugin> <from> <to> [flags...]
        if (args.length == 0 || !args[0].equalsIgnoreCase("migrate")) {
            usage(ctx);
            return;
        }

        if (args.length < 4) {
            usage(ctx);
            return;
        }

        String pluginName = args[1];
        String from = args[2].toLowerCase(Locale.ROOT);
        String to = args[3].toLowerCase(Locale.ROOT);

        if (!BACKENDS.contains(from) || !BACKENDS.contains(to)) {
            ctx.reply("Backends: json | h2 | mysql");
            return;
        }

        PluginDataRegistry.Entry entry = registry.get(pluginName);
        if (entry == null) {
            ctx.reply("Plugin is not registered for migration: " + pluginName);
            return;
        }

        Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (targetPlugin == null || !targetPlugin.isEnabled()) {
            ctx.reply("Plugin not found or not enabled: " + pluginName);
            return;
        }

        DatabaseSettings base = entry.settingsSupplier().get();
        DatabaseSettings sourceSettings = wrapBackend(base, from);
        DatabaseSettings targetSettings = wrapBackend(base, to);

        MigrationOptions opt = new MigrationOptions();
        opt.dryRun = hasFlag(args, "--dry-run");
        opt.overwrite = hasFlag(args, "--overwrite");
        opt.deleteSourceAfter = hasFlag(args, "--delete-source");
        opt.batchSize = 200;

        boolean wantsDelete = opt.deleteSourceAfter;
        boolean confirmed = hasFlag(args, "--confirm");

        if (wantsDelete && !confirmed) {
            ctx.reply("Refusing to delete source data without confirmation.");
            ctx.reply("Add --confirm to proceed: /" + ctx.label() + " migrate " + pluginName + " " + from + " " + to + " --delete-source --confirm");
            return;
        }

        ctx.reply("Starting migration: plugin=" + targetPlugin.getName() + " from=" + from + " to=" + to
                + (opt.dryRun ? " (dry-run)" : ""));

        VertexCoreApi api = VertexCoreApi.get();
        DatabaseBackend source = api.backendFor(targetPlugin, sourceSettings);
        DatabaseBackend target = api.backendFor(targetPlugin, targetSettings);

        MigrationProgress progress = new ConsoleMigrationProgress(targetPlugin.getLogger());
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner();

        api.databaseService()
                .queueFor(targetPlugin, base.timeoutMillis())
                .submit(() -> {
                    runner.migrateAll(source, target, base.tablePrefix(), entry.dataClasses(), opt, progress);
                    return null;
                })
                .whenComplete((v, err) -> Bukkit.getScheduler().runTask(corePlugin, () -> {
                    if (err != null) {
                        Throwable u = unwrap(err);
                        ctx.reply("Migration failed: " + (u.getMessage() == null ? u.getClass().getSimpleName() : u.getMessage()));
                        u.printStackTrace();
                    } else {
                        ctx.reply("Migration done.");
                    }
                }));
    }

    private void usage(CommandContext ctx) {
        ctx.reply("Usage: /" + ctx.label() + " migrate <plugin> <from> <to> [--dry-run] [--overwrite] [--delete-source --confirm]");
        ctx.reply("Backends: json | h2 | mysql");
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equalsIgnoreCase(flag)) return true;
        return false;
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null) return ce.getCause();
        if (t instanceof java.util.concurrent.ExecutionException ee && ee.getCause() != null) return ee.getCause();
        return t;
    }

    private static DatabaseSettings wrapBackend(DatabaseSettings base, String backend) {
        return new DatabaseSettings() {
            @Override public String backend() { return backend; }
            @Override public boolean useQueue() { return base.useQueue(); }
            @Override public long timeoutMillis() { return base.timeoutMillis(); }
            @Override public int poolSize() { return base.poolSize(); }
            @Override public String mysqlUrl() { return base.mysqlUrl(); }
            @Override public String mysqlUser() { return base.mysqlUser(); }
            @Override public String mysqlPassword() { return base.mysqlPassword(); }
            @Override public String tablePrefix() { return base.tablePrefix(); }
        };
    }

    @VSuggest("vertexcore")
    public List<String> rootSuggest(CommandSender sender, String alias, String[] args) {
        if (!sender.hasPermission("vertexcore.migrate")) return List.of();

        // /vertexcore <TAB>
        if (args.length == 0) return List.of("migrate"); // brigadier kann hier auch 0 liefern
        if (args.length == 1) return filter(List.of("migrate"), args[0]);

        // Wenn schon subcommand getippt, dann keine Root-Suggestions mehr
        return List.of();
    }

    @VSuggest("vertexcore migrate")
    public List<String> migrateSuggest(CommandSender sender, String alias, String[] args) {
        if (!sender.hasPermission("vertexcore.migrate")) return List.of();

        // args enthält bei unserem System die kompletten args hinter dem root-label
        // z.B. "/vc migrate X" -> args = ["migrate", "X"]

        // /vertexcore <TAB>
        if (args.length == 0) return List.of("migrate");

        // /vertexcore <partial>
        if (args.length == 1) {
            return filter(List.of("migrate"), args[0]);
        }

        if (!args[0].equalsIgnoreCase("migrate")) return List.of();

        // /vertexcore migrate <TAB> -> ONLY registered plugins
        if (args.length == 2) {
            List<String> names = registry.registeredPluginNames().stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            return filter(names, args[1]);
        }

        // /vertexcore migrate <plugin> <TAB> -> from backend
        if (args.length == 3) {
            return filter(BACKENDS, args[2]);
        }

        // /vertexcore migrate <plugin> <from> <TAB> -> to backend
        if (args.length == 4) {
            return filter(BACKENDS, args[3]);
        }

        // flags (1:1 UX)
        if (args.length >= 5) {
            Set<String> used = new HashSet<>();
            for (int i = 4; i < args.length; i++) used.add(args[i].toLowerCase(Locale.ROOT));

            boolean hasDelete = used.contains("--delete-source");
            boolean hasConfirm = used.contains("--confirm");

            List<String> remaining = new ArrayList<>();
            for (String f : FLAGS) {
                if (used.contains(f)) continue;

                // UX: --confirm nur anbieten, wenn --delete-source schon gesetzt ist
                if (f.equals("--confirm") && !hasDelete) continue;

                remaining.add(f);
            }

            // Wenn delete gesetzt ist aber confirm fehlt, ist confirm der wichtigste Vorschlag
            if (hasDelete && !hasConfirm && !remaining.contains("--confirm")) {
                remaining.add(0, "--confirm");
            }

            return filter(remaining, args[args.length - 1]);
        }

        return List.of();
    }

    private static List<String> filter(List<String> options, String token) {
        String t = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t))
                .collect(Collectors.toList());
    }
}
