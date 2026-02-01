package de.tebrox.vertexCore.command.internal;

import de.tebrox.vertexCore.command.api.VisibilityPolicy;
import de.tebrox.vertexCore.config.ConfigObject;
import org.bukkit.permissions.PermissionDefault;

import java.lang.reflect.Method;
import java.util.*;

public final class CommandNode {

    private final String name; // literal
    private final Map<String, CommandNode> children = new HashMap<>();
    private final List<String> aliases = new ArrayList<>();

    private String description;
    private String permission;
    private PermissionDefault permissionDefault = PermissionDefault.OP;
    private VisibilityPolicy visibility = VisibilityPolicy.ALWAYS;

    // PlayerOnly
    private boolean playerOnly = false;
    private boolean hideFromConsole = true;

    // Root config source (for aliases + enabled toggles)
    private Class<? extends ConfigObject> consumerConfigClass = ConfigObject.class;
    private String consumerConfigFile; // optional override
    private String aliasPathTemplate = "commands.{root}.aliases";

    // Root toggles
    private boolean disablePrimary = false;
    private String disablePrimaryPathTemplate = "commands.{root}.disablePrimary";
    private final List<String> extraAliases = new ArrayList<>();

    // Enabled toggle (can be on any node)
    private boolean enabledDefault = true;
    private String enabledConfigPath;
    private boolean hideWhenDisabled = true;
    private String disabledMessage;

    // Executor
    private Object handler;
    private Method method;
    private List<ParamSpec> params = List.of();

    private CommandSuggester suggester;

    public CommandNode(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String name() { return name; }
    public Map<String, CommandNode> children() { return children; }
    public List<String> aliases() { return aliases; }

    public String description() { return description; }
    public void description(String description) { this.description = description; }

    public String permission() { return permission; }
    public void permission(String permission) { this.permission = permission; }

    public PermissionDefault permissionDefault() { return permissionDefault; }

    public void permissionDefault(PermissionDefault def) { if(def != null) this.permissionDefault = def; }

    public VisibilityPolicy visibility() { return visibility; }
    public void visibility(VisibilityPolicy visibility) { this.visibility = visibility; }

    public boolean playerOnly() { return playerOnly; }
    public void playerOnly(boolean playerOnly) { this.playerOnly = playerOnly; }

    public boolean hideFromConsole() { return hideFromConsole; }
    public void hideFromConsole(boolean hideFromConsole) { this.hideFromConsole = hideFromConsole; }

    public Class<? extends ConfigObject> consumerConfigClass() { return consumerConfigClass; }
    public void consumerConfigClass(Class<? extends ConfigObject> c) { this.consumerConfigClass = (c == null) ? ConfigObject.class : c; }

    public String consumerConfigFile() { return consumerConfigFile; }
    public void consumerConfigFile(String v) { this.consumerConfigFile = v; }

    public String aliasPathTemplate() { return aliasPathTemplate; }
    public void aliasPathTemplate(String v) { this.aliasPathTemplate = v; }

    public boolean disablePrimary() { return disablePrimary; }
    public void disablePrimary(boolean v) { this.disablePrimary = v; }

    public String disablePrimaryPathTemplate() { return disablePrimaryPathTemplate; }

    public void disablePrimaryPathTemplate(String template) { this.disablePrimaryPathTemplate = (template == null || template.isBlank()) ? null : template.trim(); }

    public List<String> extraAliases() { return extraAliases; }

    public boolean enabledDefault() { return enabledDefault; }
    public void enabledDefault(boolean v) { this.enabledDefault = v; }

    public String enabledConfigPath() { return enabledConfigPath; }
    public void enabledConfigPath(String v) { this.enabledConfigPath = v; }

    public boolean hideWhenDisabled() { return hideWhenDisabled; }
    public void hideWhenDisabled(boolean v) { this.hideWhenDisabled = v; }

    public String disabledMessage() { return disabledMessage; }
    public void disabledMessage(String v) { this.disabledMessage = v; }

    public Object handler() { return handler; }
    public Method method() { return method; }
    public List<ParamSpec> params() { return params; }

    public void bindExecutor(Object handler, Method method, List<ParamSpec> params) {
        this.handler = handler;
        this.method = method;
        this.params = params == null ? List.of() : List.copyOf(params);
        if (!this.method.canAccess(handler)) this.method.setAccessible(true);
    }

    public boolean hasExecutor() {
        return method != null && handler != null;
    }

    public CommandNode childOrCreate(String literal) {
        String key = literal.toLowerCase(Locale.ROOT);
        return children.computeIfAbsent(key, k -> new CommandNode(literal));
    }

    public CommandNode findChild(String token) {
        if (token == null) return null;
        String key = token.toLowerCase(Locale.ROOT);

        CommandNode direct = children.get(key);
        if (direct != null) return direct;

        // alias match
        for (CommandNode n : children.values()) {
            for (String a : n.aliases) {
                if (a != null && a.equalsIgnoreCase(token)) return n;
            }
        }
        return null;
    }

    public CommandSuggester suggester() { return suggester; }
    public void suggester(CommandSuggester s) { this.suggester = s; }

}
