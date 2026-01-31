package de.tebrox.vertexCore.database.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class TableNamer {
    private TableNamer() {};

    public static String tableName(String prefix, Class<?> type) {
        String base = type.getSimpleName().toLowerCase(); // e.g. playerdata
        String name = sanitize(prefix) + sanitize(base);

        return name;
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    private static String shortHash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append(String.format("%02x", dig[i]));
            return sb.toString(); // 8 chars
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}