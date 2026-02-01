package de.tebrox.vertexCore.command.api;

import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

public final class CommandContext {

    private final CommandSender sender;
    private final String label;
    private final String[] rawArgs;


    public CommandContext(CommandSender sender, String label, String[] rawArgs) {
        this.sender = sender;
        this.label = label;
        this.rawArgs = rawArgs;
    }

    public CommandSender sender() { return sender; }
    public String label() { return label; }
    public String[] rawArgs() { return rawArgs; }
    public List<String> rawArgsList() { return Arrays.asList(rawArgs); }

    public void reply(String message) {
        sender.sendMessage(message);
    }
}
