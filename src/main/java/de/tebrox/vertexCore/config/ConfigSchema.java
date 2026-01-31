package de.tebrox.vertexCore.config;

import de.tebrox.vertexCore.config.annotation.ConfigComment;
import de.tebrox.vertexCore.config.annotation.ConfigKey;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Logger;

final class ConfigSchema<T extends ConfigObject> {

    record Entry(String path, Field field, List<String> comments, Object defaultValue) {}

    private final Class<T> type;
    private final List<Entry> entries;
    private final List<String> headerComments;

    private ConfigSchema(Class<T> type, List<Entry> entries, List<String> headerComments) {
        this.type = type;
        this.entries = entries;
        this.headerComments = headerComments;
    }

    static <T extends ConfigObject> ConfigSchema<T> of(Class<T> type) {
        if(!ConfigObject.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(type.getName() + " must implement ConfigObject");
        }

        List<String> header = new ArrayList<>();
        ConfigComment classComment = type.getAnnotation(ConfigComment.class);
        if(classComment != null) header.addAll(Arrays.asList(classComment.value()));

        T defaults = newInstanceStatic(type);

        List<Entry> list = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (Modifier.isFinal(f.getModifiers())) continue;

            ConfigKey key = f.getAnnotation(ConfigKey.class);
            if (key == null) continue;

            List<String> comments = new ArrayList<>();
            ConfigComment c = f.getAnnotation(ConfigComment.class);
            if (c != null) comments.addAll(Arrays.asList(c.value()));

            f.setAccessible(true);

            Object defVal;
            try {
                defVal = f.get(defaults);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }

            list.add(new Entry(key.value(), f, comments, defVal));
        }

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
                    field.set(instance, converted);
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
            return e.field().get(instance);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    void writeFieldValue(T instance, Entry e, Object value) {
        try {
            e.field().set(instance, value);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    Object defaultValueOf(Entry e) {
        return e.defaultValue();
    }

    List<Entry> entries() { return entries; }
    List<String> headerComments() { return headerComments; }

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
