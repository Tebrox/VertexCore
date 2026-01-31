package de.tebrox.vertexCore.config;

import java.util.*;

final class YamlCommentWriter {

    static <T extends ConfigObject> String write(ConfigSchema<T> schema, T instance, Map<String, Object> existing) {
        StringBuilder sb = new StringBuilder(2048);

        if (!schema.headerComments().isEmpty()) {
            for (String line : schema.headerComments()) {
                sb.append("# ").append(line).append('\n');
            }
            sb.append('\n');
        }

        Node root = new Node();

        // known keys (with comments)
        for (ConfigSchema.Entry e : schema.entries()) {
            Object val = schema.readFieldValue(instance, e);
            root.put(e.path(), val, e.comments());
        }

        // merge unknown keys (values preserved, no comments)
        if (existing != null && !existing.isEmpty()) {
            root.mergeUnknown(existing);
        }

        root.writeYaml(sb, 0);
        return sb.toString();
    }

    private static final class Node {
        private final Map<String, Node> children = new LinkedHashMap<>();
        private Object value = null;
        private List<String> comments = List.of();

        void put(String path, Object value, List<String> comments) {
            String[] parts = path.split("\\.");
            Node cur = this;
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                cur = cur.children.computeIfAbsent(p, k -> new Node());
                if (i == parts.length - 1) {
                    cur.value = value;
                    cur.comments = (comments == null) ? List.of() : List.copyOf(comments);
                }
            }
        }

        void mergeUnknown(Map<String, Object> existing) {
            for (var e : existing.entrySet()) {
                String key = e.getKey();
                Object val = e.getValue();

                Node child = children.get(key);

                if (child == null) {
                    // unknown root key: create as subtree if map, else as leaf value
                    Node n = new Node();
                    n.comments = List.of();

                    if (val instanceof Map<?, ?> mapVal) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) mapVal;
                        n.mergeMapIntoChildren(m);
                    } else {
                        n.value = val;
                    }

                    children.put(key, n);
                    continue;
                }

                // key exists (known section or known leaf)
                if (val instanceof Map<?, ?> mapVal) {
                    // IMPORTANT: do NOT set child.value to a map for section nodes.
                    // Always merge map entries into children to avoid duplicate output.
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) mapVal;
                    child.mergeMapIntoChildren(m);
                    continue;
                }

                // scalar/list value:
                // known wins -> only set if known node currently has no value AND no children
                if (child.value == null && child.children.isEmpty()) {
                    child.value = val;
                }
            }
        }

        private void mergeMapIntoChildren(Map<String, Object> m) {
            for (var e : m.entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();

                Node c = children.get(k);
                if (c == null) {
                    c = new Node();
                    c.comments = List.of();
                    children.put(k, c);
                }

                if (v instanceof Map<?, ?> nested) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nm = (Map<String, Object>) nested;
                    c.mergeMapIntoChildren(nm);
                } else {
                    // only set if not already set (known wins)
                    if (c.value == null) c.value = v;
                }
            }
        }


        void writeYaml(StringBuilder sb, int indent) {
            Iterator<Map.Entry<String, Node>> it = children.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Node> entry = it.next();
                String key = entry.getKey();
                Node node = entry.getValue();

                if (node.comments != null && !node.comments.isEmpty()) {
                    for (String c : node.comments) {
                        indent(sb, indent).append("# ").append(c).append('\n');
                    }
                }

                if (!node.children.isEmpty()) {
                    indent(sb, indent).append(escapeKey(key)).append(":");

                    if (node.value != null && isScalar(node.value)) {
                        sb.append(' ').append(formatScalar(node.value)).append('\n');
                    } else {
                        sb.append('\n');
                    }

                    if (node.value != null && !isScalar(node.value)) {
                        writeComplexValue(sb, indent + 2, node.value);
                    }

                    node.writeYaml(sb, indent + 2);
                } else {
                    indent(sb, indent).append(escapeKey(key)).append(":");
                    if (node.value == null) {
                        sb.append(" null\n");
                    } else if (isScalar(node.value)) {
                        sb.append(' ').append(formatScalar(node.value)).append('\n');
                    } else {
                        sb.append('\n');
                        writeComplexValue(sb, indent + 2, node.value);
                    }
                }

                if (indent == 0 && it.hasNext()) sb.append('\n');
            }
        }

        private static void writeComplexValue(StringBuilder sb, int indent, Object value) {
            if (value instanceof List<?> list) {
                writeList(sb, indent, list);
                return;
            }
            if (value instanceof Map<?, ?> map) {
                writeMap(sb, indent, map);
                return;
            }
            indent(sb, indent).append(formatScalar(String.valueOf(value))).append('\n');
        }

        private static void writeList(StringBuilder sb, int indent, List<?> list) {
            if (list.isEmpty()) {
                indent(sb, indent).append("[]\n");
                return;
            }
            for (Object o : list) {
                indent(sb, indent).append("- ");
                if (o == null) {
                    sb.append("null\n");
                } else if (isScalar(o)) {
                    sb.append(formatScalar(o)).append('\n');
                } else {
                    sb.append('\n');
                    writeComplexValue(sb, indent + 2, o);
                }
            }
        }

        private static void writeMap(StringBuilder sb, int indent, Map<?, ?> map) {
            if (map.isEmpty()) {
                indent(sb, indent).append("{}\n");
                return;
            }

            List<Map.Entry<String, Object>> entries = new ArrayList<>();
            for (var e : map.entrySet()) {
                entries.add(Map.entry(String.valueOf(e.getKey()), e.getValue()));
            }
            entries.sort(Comparator.comparing(Map.Entry::getKey));

            for (var e : entries) {
                indent(sb, indent).append(escapeKey(e.getKey())).append(":");
                Object v = e.getValue();
                if (v == null) {
                    sb.append(" null\n");
                } else if (isScalar(v)) {
                    sb.append(' ').append(formatScalar(v)).append('\n');
                } else {
                    sb.append('\n');
                    writeComplexValue(sb, indent + 2, v);
                }
            }
        }

        private static boolean isScalar(Object v) {
            return v instanceof String
                    || v instanceof Number
                    || v instanceof Boolean
                    || (v != null && v.getClass().isEnum());
        }

        private static String escapeKey(String key) {
            if (key == null || key.isEmpty()) return "\"\"";
            boolean risky = key.contains(":") || key.contains("#") || key.contains("\n") || key.startsWith(" ") || key.endsWith(" ");
            if (!risky) return key;
            return "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }

        private static String formatScalar(Object v) {
            if (v == null) return "null";
            if (v instanceof Boolean || v instanceof Number) return String.valueOf(v);
            if (v.getClass().isEnum()) return ((Enum<?>) v).name();

            String s = String.valueOf(v);

            boolean needsQuotes = s.isEmpty()
                    || s.startsWith(" ")
                    || s.endsWith(" ")
                    || s.contains(":")
                    || s.contains("#")
                    || s.contains("{")
                    || s.contains("}")
                    || s.contains("[")
                    || s.contains("]")
                    || s.contains("\n");

            if (!needsQuotes) return s;

            s = s.replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + s + "\"";
        }

        private static StringBuilder indent(StringBuilder sb, int indent) {
            for (int i = 0; i < indent; i++) sb.append(' ');
            return sb;
        }
    }
}