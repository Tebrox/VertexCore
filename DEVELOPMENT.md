# VertexCore – Development Guide

This document explains how plugin developers should use **VertexCore** as shared infrastructure for
**configuration management** and **database/storage backends**.

VertexCore is a **core infrastructure plugin**, not a feature framework.

---

## Design Goals

- Provide reusable infrastructure for multiple plugins
- Keep the core minimal and focused
- Reduce boilerplate for config + storage
- Keep feature logic inside feature plugins

If something is only useful for a single plugin, it likely does **not** belong in VertexCore.

---

## Scope

VertexCore provides:

- **Typed YAML configuration** via annotated config objects
- **Storage backends** via a unified API (**json**, **h2**, **mysql/mariadb**)
- **Async helpers** for storage operations (queue + main-thread delivery)
- Optional: plugin registration for **/vertexcore migrate** support

VertexCore does **not** provide gameplay features, commands/GUI frameworks, or permission systems.

---

## Using VertexCore in Your Plugin

### 1) Declare the dependency

`plugin.yml`:

```yml
depend:
  - VertexCore
```

---

## Configuration (YAML) – Typed Config Objects

VertexCore loads/saves YAML configs through **annotated POJOs** that implement `ConfigObject`.

### Example: Define a config class

```java
import de.tebrox.vertexCore.config.ConfigObject;
import de.tebrox.vertexCore.config.annotation.ConfigComment;
import de.tebrox.vertexCore.config.annotation.ConfigKey;
import de.tebrox.vertexCore.config.annotation.StoreAt;
import de.tebrox.vertexCore.config.annotation.Min;
import de.tebrox.vertexCore.config.annotation.Max;

@StoreAt("config.yml")
@ConfigComment({
    "Example config for MyPlugin",
    "Managed by VertexCore (missing keys and comments will be written back)."
})
public final class MyPluginConfig implements ConfigObject {

    @ConfigKey("storage.backend")
    @AllowedValues({"json","h2", "mysql"})
    @ConfigComment({"Storage backend: json | h2 | mysql"})
    public String backend = "json";

    @ConfigKey("storage.table-prefix")
    public String tablePrefix = "myplugin_";

    @ConfigKey("mysql.url")
    public String mysqlUrl = "jdbc:mysql://localhost:3306/minecraft";

    @ConfigKey("mysql.user")
    public String mysqlUser = "root";

    @ConfigKey("mysql.password")
    public String mysqlPassword = "password";

    @ConfigKey("cache.max-items")
    @Min(0) @Max(100000)
    public int maxItems = 5000;
}
```

### Example: Load the config

```java
import de.tebrox.vertexCore.config.Config;

public final class MyPlugin extends JavaPlugin {

    private MyPluginConfig cfg;

    @Override
    public void onEnable() {
        cfg = new Config<>(this, MyPluginConfig.class).loadConfigObject();
        getLogger().info("Backend is: " + cfg.backend);
    }
}
```

**Notes**
- The config file location is taken from `@StoreAt("...")`.
- VertexCore will validate/fix values (e.g. clamp via `@Min/@Max`) and write back missing keys/comments.

---

## Database / Storage Backends (JSON, H2, MySQL)

VertexCore exposes storage via:

- `DatabaseSettings` (backend choice + connection info)
- `DatabaseBackend` (low-level key/value JSON storage per table)
- `Database<T extends DataObject>` (typed helper using `JsonCodec` + `@DbExpose`)

### 1) Define `DatabaseSettings`

```java
import de.tebrox.vertexCore.database.DatabaseSettings;

public final class MyDbSettings implements DatabaseSettings {

    private final MyPluginConfig cfg;

    public MyDbSettings(MyPluginConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public String backend() {
        return cfg.backend; // "json" | "h2" | "mysql"
    }

    @Override
    public String tablePrefix() {
        return cfg.tablePrefix;
    }

    // MySQL only:
    @Override
    public String mysqlUrl() {
        return cfg.mysqlUrl;
    }

    @Override
    public String mysqlUser() {
        return cfg.mysqlUser;
    }

    @Override
    public String mysqlPassword() {
        return cfg.mysqlPassword;
    }

    // Optional tuning:
    @Override
    public int poolSize() { return 5; }

    @Override
    public boolean useQueue() { return true; }

    @Override
    public long timeoutMillis() { return 5000; }
}
```

### 2) Create a typed data object

Only fields annotated with `@DbExpose` are serialized.

```java
import de.tebrox.vertexCore.database.DataObject;
import de.tebrox.vertexCore.database.annotation.DbExpose;

public final class PlayerData implements DataObject {

    private String uniqueId;

    @DbExpose
    public int coins = 0;

    @DbExpose
    public long lastLogin = 0L;

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }
}
```

---

## Enabling `/vertexcore migrate` for Your Plugin (optional)

VertexCore provides a migration command that relies on a registry of:
- a `DatabaseSettings` supplier
- the involved `DataObject` classes (used to derive table names)

Register your plugin during `onEnable()`:

```java
import de.tebrox.vertexCore.VertexCoreApi;

@Override
public void onEnable() {
    cfg = new Config<>(this, MyPluginConfig.class).loadConfigObject();
    DatabaseSettings settings = new MyDbSettings(cfg);

    VertexCoreApi.get().registry().register(
        this,
        () -> settings,
        PlayerData.class // add more DataObject classes here
    );
}
```

---

## API Stability

- Only documented public APIs should be considered stable
- Internal implementation details may change
- Expect breaking changes across major versions

---

## Extension Rules

Add functionality to VertexCore only if:
- it benefits **multiple** plugins, and
- it clearly belongs to **infrastructure** (configs/storage), and
- it does not introduce feature-specific logic

Good examples:
- additional backend support
- config validation helpers
- migration utilities

Bad examples:
- gameplay systems
- plugin-specific commands/GUI logic
- hard dependencies on feature plugins
