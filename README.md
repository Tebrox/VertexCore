<p align="center">
  <img src="https://raw.githubusercontent.com/Tebrox/VertexCore/master/assets/banner.png" alt="VertexCore Banner">
</p>

# VertexCore

VertexCore is a **Paper-only core plugin** designed as a shared foundation for plugin developers.

It provides reusable infrastructure for configuration handling, database access and command execution,
reducing duplicated boilerplate across multiple plugins.

> VertexCore is **not** a gameplay plugin.  
> It is intended to be used as a dependency by other plugins.

---

## Features

### Configuration System
- Annotation-based configuration definitions
- Automatic file generation and loading
- Validation, default values and comments
- Config objects mapped directly to Java classes

### Database System
- Unified database abstraction
- Supported backends:
    - JSON (flatfile)
    - H2
    - MySQL / MariaDB
- Async and sync access
- Built-in migration support
- Config-driven database settings

### Command System
- Centralized command execution framework
- Support for root commands and subcommands
- Argument injection and resolvers
- Permission and visibility handling
- Tab completion support

---

## Requirements

- Paper 1.21+
- Java 17+

---

## Installation

1. Download the latest release
2. Place `VertexCore.jar` into your server’s `plugins` folder
3. Restart the server

Plugins using VertexCore must declare it as a dependency.

---

## Developer Usage

VertexCore is distributed via **jitpack.io**.

### Gradle

```gradle
repositories {
    mavenCentral()
    maven { url "https://jitpack.io" }
}

dependencies {
    compileOnly("com.github.Tebrox:VertexCore:v1.0.0")
}
```

### Maven

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.Tebrox</groupId>
    <artifactId>VertexCore</artifactactId>
    <version>v1.0.0</version>
    <scope>provided</scope>
</dependency>
```

---

## Documentation

- Full documentation is available in the **GitHub Wiki**
- Covers configuration, database, command system and migration

---

## License

MIT License
