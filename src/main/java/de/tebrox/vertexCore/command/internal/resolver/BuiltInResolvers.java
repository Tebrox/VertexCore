package de.tebrox.vertexCore.command.internal.resolver;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BuiltInResolvers {

    private BuiltInResolvers() {}

    public static void registerAll(ResolverRegistry registry) {
        registry.register(new ArgumentResolver<String>() {
            @Override public Class<String> type() { return String.class; }
            @Override public String parse(CommandSender sender, String input) { return input; }
        });

        registry.register(new ArgumentResolver<Integer>() {
            @Override public Class<Integer> type() { return Integer.class; }
            @Override public Integer parse(CommandSender sender, String input) {
                try { return Integer.parseInt(input); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Not a number"); }
            }
        });

        registry.register(new ArgumentResolver<Double>() {
            @Override public Class<Double> type() { return Double.class; }
            @Override public Double parse(CommandSender sender, String input) {
                try { return Double.parseDouble(input); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Not a number"); }
            }
        });

        registry.register(new ArgumentResolver<Boolean>() {
            @Override public Class<Boolean> type() { return Boolean.class; }
            @Override public Boolean parse(CommandSender sender, String input) {
                String s = input.toLowerCase(Locale.ROOT);
                return switch (s) {
                    case "true", "yes", "y", "1", "on" -> true;
                    case "false", "no", "n", "0", "off" -> false;
                    default -> throw new IllegalArgumentException("Not a boolean");
                };
            }

            @Override public List<String> suggest(CommandSender sender, String prefix) {
                return List.of("true", "false");
            }
        });

        registry.register(new ArgumentResolver<Player>() {
            @Override public Class<Player> type() { return Player.class; }

            @Override public Player parse(CommandSender sender, String input) {
                Player p = Bukkit.getPlayerExact(input);
                if (p == null) throw new IllegalArgumentException("Player not found");
                return p;
            }

            @Override public List<String> suggest(CommandSender sender, String prefix) {
                String pre = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(p.getName());
                }
                return out;
            }
        });

        registry.register(new ArgumentResolver<World>() {
            @Override public Class<World> type() { return World.class; }

            @Override public World parse(CommandSender sender, String input) {
                World w = Bukkit.getWorld(input);
                if (w == null) throw new IllegalArgumentException("World not found");
                return w;
            }

            @Override public List<String> suggest(CommandSender sender, String prefix) {
                String pre = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                for (World w : Bukkit.getWorlds()) {
                    if (w.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(w.getName());
                }
                return out;
            }
        });
    }
}
