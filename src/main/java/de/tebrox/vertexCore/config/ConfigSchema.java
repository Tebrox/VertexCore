package de.tebrox.vertexCore.config;

import de.tebrox.vertexCore.config.annotation.ConfigComment;
import de.tebrox.vertexCore.config.annotation.ConfigKey;
import de.tebrox.vertexCore.config.annotation.ConfigSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Logger;

final class ConfigSchema<T extends ConfigObject> {

    record Entry(String path, List<Field> accessChain, Field field, List<String> comments, Object defaultValue) {}

    private final Class<T> type;
    private final List<Entry> entries;
    private final List<String> headerComments;

    private ConfigSchema(Class<T> type, List<Entry> entries, List<String> headerComments) {
        this.type = type;
        this.entries = entries;
        this.headerComments = headerComments;
    }

    static <T extends ConfigObject> ConfigSchema<T> of(Class<T> type) {
        if (!ConfigObject.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(type.getName() + " must implement ConfigObject");
        }

        List<String> header = new ArrayList<>();
        ConfigComment classComment = type.getAnnotation(ConfigComment.class);
        if (classComment != null) header.addAll(Arrays.asList(classComment.value()));

        T defaults = newInstanceStatic(type);

        List<Entry> list = new ArrayList<>();
        collectEntries(defaults, type, "", new ArrayList<>(), new ArrayDeque<>(), list);

        // Optional: detect duplicate keys (helps when scanning nested settings)
        detectDuplicates(list);

        list.sort(Comparator.comparing(Entry::path));
        return new ConfigSchema<>(type, List.copyOf(list), List.copyOf(header));
    }

    T newInstance() {
        return newInstanceStatic(type);
    }

    private static <T extends ConfigObject> T newInstanceStatic(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(type.getName() + " must have a no-args constructor", e);
        }
    }

    void applyYamlToObject(YamlConfiguration yaml, T instance, Logger log) {
        for (Entry e : entries) {
            if (!yaml.contains(e.path())) continue;

            Object raw = yaml.get(e.path());
            Field field = e.field();
            Class<?> ft = field.getType();

            try {
                Object converted = convert(raw, ft);
                if (converted != null || raw == null) {
                    // allow explicit nulls
                    Object target = resolveTarget(instance, e);
                    field.set(target, converted);
                } else {
                    log.warning("[VertexCore Config] Cannot convert '" + e.path() + "' value '" + raw + "' to " + ft.getSimpleName());
                }
            } catch (Exception ex) {
                log.warning("[VertexCore Config] Failed to set '" + e.path() + "': " + ex.getMessage());
            }
        }
    }

    Object readFieldValue(T instance, Entry e) {
        try {
            Object target = resolveTarget(instance, e);
            return e.field().get(target);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    void writeFieldValue(T instance, Entry e, Object value) {
        try {
            Object target = resolveTarget(instance, e);
            e.field().set(target, value);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    Object defaultValueOf(Entry e) {
        return e.defaultValue();
    }

    List<Entry> entries() { return entries; }
    List<String> headerComments() { return headerComments; }

    // ---------------- nested scanning ----------------

    /**
     * Collects entries recursively.
     *
     * ConfigSection resolution (both supported):
     *   FIELD @ConfigSection > TYPE @ConfigSection > inheritedPrefix
     */
    private static void collectEntries(
            Object defaultsObj,
            Class<?> currentType,
            String inheritedPrefix,
            List<Field> accessChain,
            ArrayDeque<Class<?>> stack,
            List<Entry> out
    ) {
        // cycle protection
        if (stack.contains(currentType)) return;
        stack.push(currentType);

        for (Field f : currentType.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (Modifier.isFinal(f.getModifiers())) continue;

            f.setAccessible(true);

            // Leaf entry: @ConfigKey on this field
            ConfigKey key = f.getAnnotation(ConfigKey.class);
            if (key != null) {
                List<String> comments = new ArrayList<>();
                ConfigComment c = f.getAnnotation(ConfigComment.class);
                if (c != null) comments.addAll(Arrays.asList(c.value()));

                Object defVal;
                try {
                    defVal = f.get(defaultsObj);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }

                out.add(new Entry(join(inheritedPrefix, key.value()), List.copyOf(accessChain), f, comments, defVal));
                continue;
            }

            // Nested object: recurse if it looks like a settings POJO
            if (!isNestable(f.getType())) continue;

            // Prefix resolution: FIELD > TYPE > inherited
            String nestedPrefix;
            ConfigSection fieldSection = f.getAnnotation(ConfigSection.class);
            if (fieldSection != null) {
                nestedPrefix = join(inheritedPrefix, fieldSection.value());
            } else {
                ConfigSection typeSection = f.getType().getAnnotation(ConfigSection.class);
                if (typeSection != null) {
                    nestedPrefix = join(inheritedPrefix, typeSection.value());
                } else {
                    nestedPrefix = inheritedPrefix;
                }
            }

            Object nestedDefaults;
            try {
                nestedDefaults = f.get(defaultsObj);
                if (nestedDefaults == null) {
                    nestedDefaults = newInstancePojoOrNull(f.getType());
                    if (nestedDefaults == null) continue; // cannot instantiate -> skip recursion
                    f.set(defaultsObj, nestedDefaults);
                }
            } catch (Exception ignored) {
                continue;
            }

            List<Field> nestedChain = new ArrayList<>(accessChain);
            nestedChain.add(f);

            collectEntries(nestedDefaults, f.getType(), nestedPrefix, nestedChain, stack, out);
        }

        stack.pop();
    }

    private Object resolveTarget(T root, Entry e) {
        Object cur = root;

        for (Field step : e.accessChain()) {
            try {
                Object nxt = step.get(cur);
                if (nxt == null) {
                    Object created = newInstancePojoOrNull(step.getType());
                    if (created == null) {
                        throw new IllegalStateException("Cannot instantiate nested config object: " + step.getType().getName());
                    }
                    step.set(cur, created);
                    nxt = created;
                }
                cur = nxt;
            } catch (IllegalAccessException ex) {
                throw new RuntimeException("Failed to resolve nested config path for " + e.path(), ex);
            }
        }

        return cur;
    }

    private static boolean isNestable(Class<?> t) {
        if (t.isPrimitive()) return false;
        if (t.isEnum()) return false;

        if (t == String.class) return false;
        if (t == Boolean.class) return false;
        if (Number.class.isAssignableFrom(t)) return false;

        if (List.class.isAssignableFrom(t)) return false;
        if (Map.class.isAssignableFrom(t)) return false;

        // Treat everything else as nested POJO settings object
        return true;
    }

    private static Object newInstancePojoOrNull(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String join(String prefix, String key) {
        if (prefix == null || prefix.isBlank()) return key;
        if (key == null || key.isBlank()) return prefix;
        return prefix + "." + key;
    }

    private static void detectDuplicates(List<Entry> entries) {
        Map<String, Entry> seen = new HashMap<>();
        for (Entry e : entries) {
            Entry prev = seen.putIfAbsent(e.path(), e);
            if (prev != null) {
                throw new IllegalStateException("Duplicate config key detected: '" + e.path() + "' (fields: "
                        + prev.field().getDeclaringClass().getName() + "#" + prev.field().getName()
                        + " and "
                        + e.field().getDeclaringClass().getName() + "#" + e.field().getName()
                        + ")");
            }
        }
    }

    // ---------------- conversion ----------------

    private static Object convert(Object raw, Class<?> target) {
        if (raw == null) return null;
        if (target.isInstance(raw)) return raw;

        if (target == String.class) return String.valueOf(raw);

        if (target == int.class || target == Integer.class) {
            if (raw instanceof Number n) return n.intValue();
            return tryParseInt(raw.toString());
        }

        if (target == long.class || target == Long.class) {
            if (raw instanceof Number n) return n.longValue();
            return tryParseLong(raw.toString());
        }

        if (target == boolean.class || target == Boolean.class) {
            if (raw instanceof Boolean b) return b;
            return Boolean.parseBoolean(raw.toString());
        }

        if (target == double.class || target == Double.class) {
            if (raw instanceof Number n) return n.doubleValue();
            return tryParseDouble(raw.toString());
        }

        if (target.isEnum()) {
            String s = raw.toString();
            @SuppressWarnings("rawtypes")
            Class<? extends Enum> et = target.asSubclass(Enum.class);
            for (Object c : et.getEnumConstants()) {
                if (((Enum<?>) c).name().equalsIgnoreCase(s)) return c;
            }
            return null;
        }

        if (List.class.isAssignableFrom(target) && raw instanceof List<?> list) {
            // MVP: List<String>
            List<String> out = new ArrayList<>();
            for (Object o : list) out.add(String.valueOf(o));
            return out;
        }

        if (Map.class.isAssignableFrom(target) && raw instanceof Map<?, ?> map) {
            // MVP: Map<String, Object>
            Map<String, Object> out = new LinkedHashMap<>();
            for (var e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }

        return null;
    }

    private static Integer tryParseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ignored) { return null; }
    }

    private static Long tryParseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception ignored) { return null; }
    }

    private static Double tryParseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception ignored) { return null; }
    }
}
