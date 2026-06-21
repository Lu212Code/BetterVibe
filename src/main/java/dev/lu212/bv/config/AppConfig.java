package dev.lu212.bv.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.lu212.bv.AppDefaults;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AppConfig {

    private final Path configFile;
    private final Gson gson;
    private final Map<String, Object> data;

    public AppConfig() {
        this.configFile = Path.of(AppDefaults.CONFIG_FILE);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.data = new ConcurrentHashMap<>(loadRaw());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRaw() {
        if (Files.exists(configFile)) {
            try {
                var raw = Files.readString(configFile);
                var map = gson.fromJson(raw, Map.class);
                return map != null ? map : Map.of();
            } catch (IOException e) {
                System.err.println("Could not load config: " + e.getMessage());
            }
        }
        return Map.of();
    }

    public void save() {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, gson.toJson(data));
        } catch (IOException e) {
            System.err.println("Could not save config: " + e.getMessage());
        }
    }

    public String get(String key, String defaultValue) {
        var val = data.get(key);
        return val instanceof String s ? s : defaultValue;
    }

    public void set(String key, String value) {
        data.put(key, value);
    }

    public int getInt(String key, int defaultValue) {
        var val = data.get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultValue;
    }

    public void setInt(String key, int value) {
        data.put(key, value);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        var val = data.get(key);
        if (val instanceof Boolean b) return b;
        return defaultValue;
    }

    public void setBoolean(String key, boolean value) {
        data.put(key, value);
    }

    public Map<String, Object> getAll() {
        return Map.copyOf(data);
    }
}
