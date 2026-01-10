package com.example.wg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.Yaml;

public class ConfigLoader {
    private ConfigLoader() {
    }

    public static BotConfig load() {
        Map<String, String> values = new HashMap<>();
        Path configPath = resolveConfigPath();
        Path baseDir = configPath != null ? configPath.toAbsolutePath().getParent() : Paths.get(".").toAbsolutePath().normalize();
        if (configPath != null && Files.exists(configPath)) {
            values.putAll(loadYaml(configPath));
        }

        System.getenv().forEach((key, value) -> {
            if (key.startsWith("WG_") || key.equals("BOT_TOKEN") || key.equals("ADMIN_IDS")) {
                values.put(key, value);
            }
        });

        String token = require(values, "BOT_TOKEN");
        Set<Long> adminIds = parseAdminIds(require(values, "ADMIN_IDS"));
        Path scriptsDir = resolvePath(values.getOrDefault("WG_SCRIPTS_DIR", "scripts"), baseDir);
        scriptsDir = ensureScriptsDir(scriptsDir);
        Path clientDir = resolvePath(values.getOrDefault("WG_CLIENT_DIR", "/etc/wireguard/clients"), baseDir);
        String wgInterface = values.getOrDefault("WG_INTERFACE", "wg0");
        Map<String, String> extraEnv = values.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("WG_"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new BotConfig(token, adminIds, scriptsDir, clientDir, wgInterface, extraEnv);
    }

    private static Path resolveConfigPath() {
        String explicit = System.getenv("WG_CONFIG");
        if (explicit != null && !explicit.isBlank()) {
            return Paths.get(explicit);
        }
        Path cwd = Paths.get(".").toAbsolutePath().normalize();
        Path yaml = cwd.resolve("config.yaml");
        if (Files.exists(yaml)) {
            return yaml;
        }
        Path yml = cwd.resolve("config.yml");
        if (Files.exists(yml)) {
            return yml;
        }
        return null;
    }

    private static Map<String, String> loadYaml(Path path) {
        Yaml yaml = new Yaml();
        try (InputStream input = Files.newInputStream(path)) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, String> values = new HashMap<>();
            for (var entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String key = entry.getKey().toString().trim().toUpperCase(Locale.ROOT);
                if (entry.getValue() instanceof Iterable<?> iterable) {
                    String joined = joinIterable(iterable);
                    values.put(key, joined);
                } else {
                    values.put(key, entry.getValue().toString().trim());
                }
            }
            return values;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config: " + path, e);
        }
    }

    private static Path resolvePath(String value, Path baseDir) {
        Path path = Paths.get(value);
        if (path.isAbsolute()) {
            return path;
        }
        return baseDir.resolve(path).normalize();
    }

    private static Path ensureScriptsDir(Path scriptsDir) {
        if (Files.exists(scriptsDir)) {
            return scriptsDir;
        }
        try {
            Path tempDir = Files.createTempDirectory("wg-scripts-");
            tempDir.toFile().deleteOnExit();
            for (String script : scriptNames()) {
                copyResource("scripts/" + script, tempDir.resolve(script));
            }
            return tempDir;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare scripts directory: " + scriptsDir, e);
        }
    }

    private static void copyResource(String resource, Path target) throws IOException {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing resource: " + resource);
            }
            try (OutputStream output = Files.newOutputStream(target)) {
                input.transferTo(output);
            }
            setExecutable(target);
        }
    }

    private static void setExecutable(Path target) throws IOException {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
            Files.setPosixFilePermissions(target, perms);
        } catch (UnsupportedOperationException ignored) {
            target.toFile().setExecutable(true, false);
        }
    }

    private static Set<String> scriptNames() {
        return Set.of("wg-status.sh", "wg-list.sh", "wg-add.sh", "wg-revoke.sh");
    }

    private static String joinIterable(Iterable<?> iterable) {
        StringBuilder builder = new StringBuilder();
        for (Object item : iterable) {
            if (item == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(item.toString().trim());
        }
        return builder.toString();
    }

    private static String require(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config: " + key);
        }
        return value;
    }

    private static Set<Long> parseAdminIds(String value) {
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .map(Long::valueOf)
            .collect(Collectors.toSet());
    }
}
