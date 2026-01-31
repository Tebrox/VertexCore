package de.tebrox.vertexCore.database;

import com.google.gson.*;
import de.tebrox.vertexCore.database.annotation.DbExpose;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonCodec {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public <T> String toJson(Class<T> type, T obj) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            for(Field f : type.getDeclaredFields()) {
                if(Modifier.isStatic(f.getModifiers())) continue;
                if(!f.isAnnotationPresent(DbExpose.class)) continue;
                f.setAccessible(true);
                map.put(f.getName(), f.get(obj));
            }
            return gson.toJson(map);
        } catch(Exception e) {
            throw new RuntimeException("JSON serialize failed: " + type.getName(), e);
        }
    }

    public <T> T fromJson(Class<T> type, String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            T instance = type.getDeclaredConstructor().newInstance();

            for(Field f : type.getDeclaredFields()) {
                if(Modifier.isStatic(f.getModifiers())) continue;
                if(!f.isAnnotationPresent(DbExpose.class)) continue;

                if(!obj.has(f.getName())) continue;
                f.setAccessible(true);

                JsonElement el = obj.get(f.getName());
                Object value = gson.fromJson(el, f.getGenericType());
                f.set(instance, value);
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialize failed: " + type.getName(), e);
        }
    }
}
