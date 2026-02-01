package de.tebrox.vertexCore.command.internal;

import de.tebrox.vertexCore.command.annotation.*;
import de.tebrox.vertexCore.command.api.CommandContext;
import de.tebrox.vertexCore.config.ConfigObject;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public final class AnnotationParser {

    public CommandRegistry parse(Object handler) {
        Objects.requireNonNull(handler, "handler");

        CommandRegistry reg = new CommandRegistry();
        Class<?> clazz = handler.getClass();

        // class-level config source for consumer main config
        VAliasConfigSource src = clazz.getAnnotation(VAliasConfigSource.class);
        Class<? extends ConfigObject> srcConfigClass = (src != null) ? src.config() : ConfigObject.class;
        String srcFile = (src != null) ? src.file() : "";
        String srcPath = (src != null) ? src.rootAliasesPath() : "commands.{root}.aliases";

        for (Method m : clazz.getDeclaredMethods()) {
            VCommand rootAnn = m.getAnnotation(VCommand.class);
            VSub subAnn = m.getAnnotation(VSub.class);
            VSuggest sugAnn = m.getAnnotation(VSuggest.class);

            if (rootAnn == null && subAnn == null && sugAnn == null) continue;

            String path = rootAnn != null ? rootAnn.value() : (subAnn != null ? subAnn.value() : sugAnn.value());

            List<String> tokens = tokenize(path);
            if (tokens.isEmpty()) continue;

            String rootName = tokens.get(0);
            CommandNode root = reg.getRoot(rootName);
            if (root == null) {
                root = new CommandNode(rootName);

                // apply config source to root node
                root.consumerConfigClass(srcConfigClass);
                root.consumerConfigFile((srcFile != null && !srcFile.isBlank()) ? srcFile.trim() : null);
                root.aliasPathTemplate(srcPath);

                reg.putRoot(rootName, root);
            }

            CommandNode node = root;
            for (int i = 1; i < tokens.size(); i++) {
                node = node.childOrCreate(tokens.get(i));
            }

            if (sugAnn != null) {
                validateSuggestSignature(m);

                // node finden wie gewohnt (root + children)
                node.suggester((sender, alias, args) -> {
                    try {
                        if (!m.canAccess(handler)) m.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        List<String> res = (List<String>) m.invoke(handler, sender, alias, args);
                        return res == null ? List.of() : res;
                    } catch (Throwable t) {
                        t.printStackTrace();
                        return List.of();
                    }
                });

                // Suggest-Methoden sollen KEIN Executor binden
                continue;
            }


            VDesc desc = m.getAnnotation(VDesc.class);
            if (desc != null) node.description(desc.value());

            VPerm perm = m.getAnnotation(VPerm.class);
            if (perm != null) {
                node.permission(perm.value());
                node.permissionDefault(perm.def());
                node.visibility(perm.visibility());
            }

            VAlias alias = m.getAnnotation(VAlias.class);
            if (alias != null) {
                for (String a : alias.value()) {
                    if (a == null) continue;
                    String t = a.trim();
                    if (!t.isEmpty()) node.aliases().add(t);
                }
            }

            VPlayerOnly po = m.getAnnotation(VPlayerOnly.class);
            if (po != null) {
                node.playerOnly(true);
                node.hideFromConsole(po.hideFromConsole());
            }

            VRootToggle toggle = m.getAnnotation(VRootToggle.class);
            if (toggle != null) {
                root.disablePrimary(toggle.disablePrimary());

                // NEW: optional path template
                try {
                    // falls du das Feld (disablePrimaryPath) ergänzt hast
                    String p = toggle.disablePrimaryPath();
                    root.disablePrimaryPathTemplate(p);
                } catch (Throwable ignored) {
                    // wenn du VRootToggle nicht erweitert hast, ignorieren
                }

                for (String a : toggle.aliases()) {
                    if (a == null) continue;
                    String t = a.trim();
                    if (!t.isEmpty()) root.extraAliases().add(t);
                }
            }


            VEnabled enabled = m.getAnnotation(VEnabled.class);
            if (enabled != null) {
                node.enabledDefault(enabled.value());
                String p = enabled.configPath();
                node.enabledConfigPath((p == null || p.isBlank()) ? null : p.trim());
                node.hideWhenDisabled(enabled.hideWhenDisabled());
                String msg = enabled.message();
                node.disabledMessage((msg == null || msg.isBlank()) ? null : msg);
            }

            node.bindExecutor(handler, m, buildParams(m));
        }

        return reg;
    }

    private static void validateMethodSignature(Method m) {
        Parameter[] params = m.getParameters();
        if (params.length == 0 || params[0].getType() != CommandContext.class) {
            throw new IllegalArgumentException("Command method must have CommandContext as first parameter: " +
                    m.getDeclaringClass().getName() + "#" + m.getName());
        }
    }

    private static List<ParamSpec> buildParams(Method m) {
        Parameter[] params = m.getParameters();
        List<ParamSpec> out = new ArrayList<>();

        // skip first = CommandContext
        for (int i = 1; i < params.length; i++) {
            Parameter p = params[i];
            VOptional opt = p.getAnnotation(VOptional.class);
            VArg arg = p.getAnnotation(VArg.class);

            String name = arg != null ? arg.value() : ("arg" + i);
            boolean optional = opt != null;

            out.add(new ParamSpec(p.getType(), name, optional));
        }
        return out;
    }

    private static List<String> tokenize(String path) {
        if (path == null) return List.of();
        String s = path.trim();
        if (s.isEmpty()) return List.of();
        String[] parts = s.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static void validateSuggestSignature(Method m) {
        // expected: List<String> method(CommandSender sender, String alias, String[] args)
        Class<?>[] p = m.getParameterTypes();
        if (p.length != 3) throw new IllegalArgumentException("Suggest method must have 3 params: CommandSender, String, String[]");
        if (p[0] != CommandSender.class) throw new IllegalArgumentException("Suggest[0] must be CommandSender");
        if (p[1] != String.class) throw new IllegalArgumentException("Suggest[1] must be String alias");
        if (p[2] != String[].class) throw new IllegalArgumentException("Suggest[2] must be String[] args");
        if (!java.util.List.class.isAssignableFrom(m.getReturnType())) {
            throw new IllegalArgumentException("Suggest method must return List<String>");
        }
    }

}
