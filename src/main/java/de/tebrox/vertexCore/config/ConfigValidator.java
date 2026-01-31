package de.tebrox.vertexCore.config;

import de.tebrox.vertexCore.config.annotation.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class ConfigValidator {

    private ConfigValidator() {}

    public static <T extends ConfigObject> void validateAndFix(ConfigSchema<T> schema, T instance, Logger log) {
        for (ConfigSchema.Entry e : schema.entries()) {
            Field f = e.field();
            Object value = schema.readFieldValue(instance, e);
            Object defaultValue = schema.defaultValueOf(e);

            // @NotNull
            if (f.isAnnotationPresent(NotNull.class) && value == null) {
                warn(log, e.path(), "is null but @NotNull. Resetting to default.");
                schema.writeFieldValue(instance, e, defaultValue);
                continue;
            }

            // @AllowedValues (String)
            AllowedValues allowed = f.getAnnotation(AllowedValues.class);
            if (allowed != null && value instanceof String s) {
                boolean ok = contains(allowed.value(), s, allowed.ignoreCase());
                if (!ok) {
                    warn(log, e.path(), "value '" + s + "' not in allowed set " + Arrays.toString(allowed.value()) + ". Resetting to default.");
                    schema.writeFieldValue(instance, e, defaultValue);
                    continue;
                }
            }

            // @Regex (String)
            Regex rx = f.getAnnotation(Regex.class);
            if (rx != null && value instanceof String s) {
                Pattern p = Pattern.compile(rx.value());
                if (!p.matcher(s).matches()) {
                    warn(log, e.path(), "value '" + s + "' does not match regex '" + rx.value() + "'. Resetting to default.");
                    schema.writeFieldValue(instance, e, defaultValue);
                    continue;
                }
            }

            // @Min/@Max (Number) + optional @Clamp
            Min min = f.getAnnotation(Min.class);
            Max max = f.getAnnotation(Max.class);
            boolean clamp = f.isAnnotationPresent(Clamp.class);

            if ((min != null || max != null) && value instanceof Number n) {
                double d = n.doubleValue();
                double orig = d;

                if (min != null && d < min.value()) {
                    if (clamp) d = min.value();
                    else {
                        warn(log, e.path(), "value " + orig + " < min " + min.value() + ". Resetting to default.");
                        schema.writeFieldValue(instance, e, defaultValue);
                        continue;
                    }
                }
                if (max != null && d > max.value()) {
                    if (clamp) d = max.value();
                    else {
                        warn(log, e.path(), "value " + orig + " > max " + max.value() + ". Resetting to default.");
                        schema.writeFieldValue(instance, e, defaultValue);
                        continue;
                    }
                }

                if (clamp && d != orig) {
                    Object casted = castNumberLike(n, d);
                    warn(log, e.path(), "value " + orig + " clamped to " + d + ".");
                    schema.writeFieldValue(instance, e, casted);
                    continue;
                }
            }

            // Enum invalid -> convert may have left null
            if (f.getType().isEnum() && value == null) {
                warn(log, e.path(), "enum value is invalid/null. Resetting to default.");
                schema.writeFieldValue(instance, e, defaultValue);
                continue;
            }

            // List null entries cleanup if @NotNull
            if (value instanceof List<?> list && f.isAnnotationPresent(NotNull.class) && list.contains(null)) {
                warn(log, e.path(), "list contains null entries but @NotNull is set. Removing nulls.");
                List<Object> cleaned = new ArrayList<>(list);
                cleaned.removeIf(Objects::isNull);
                schema.writeFieldValue(instance, e, cleaned);
            }
        }
    }

    private static boolean contains(String[] arr, String s, boolean ignoreCase) {
        for (String a : arr) {
            if (ignoreCase) {
                if (a.equalsIgnoreCase(s)) return true;
            } else {
                if (a.equals(s)) return true;
            }
        }
        return false;
    }

    private static Object castNumberLike(Number original, double d) {
        if (original instanceof Integer) return (int) Math.round(d);
        if (original instanceof Long) return (long) Math.round(d);
        if (original instanceof Float) return (float) d;
        if (original instanceof Double) return d;
        return d;
    }

    private static void warn(Logger log, String path, String msg) {
        log.warning("[VertexCore Config] " + path + ": " + msg);
    }
}