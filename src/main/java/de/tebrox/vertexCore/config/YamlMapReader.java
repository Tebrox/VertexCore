package de.tebrox.vertexCore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

final class YamlMapReader {
    static Map<String, Object> toMap(YamlConfiguration yaml) {
        Map<String, Object> out = new LinkedHashMap<>();
        for(String key : yaml.getKeys(false)) {
            Object val = yaml.get(key);
            out.put(key, convert(val));
        }
        return out;
    }

    private static Object convert(Object val) {
        if(val instanceof ConfigurationSection sec) {
            Map<String, Object> m = new LinkedHashMap<>();
            for(String k : sec.getKeys(false)) {
                m.put(k, convert(sec.get(k)));
            }
            return m;
        }
        return val;
    }
}
