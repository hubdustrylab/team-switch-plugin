package dev.hubdustry.teamswitch.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record SwitchSettings(boolean enabled, int cooldownSeconds) {
    public static final SwitchSettings DISABLED = new SwitchSettings(false, 60);

    public static SwitchSettings load(Path file) throws IOException {
        if (Files.notExists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                    # Restart the server after you change this file.
                    enabled=true
                    cooldownSeconds=60
                    """);
        }

        var properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }

        String enabled = properties.getProperty("enabled", "true").strip();
        if (!enabled.equalsIgnoreCase("true") && !enabled.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Set enabled to true or false.");
        }
        int cooldown;
        try {
            cooldown = Integer.parseInt(properties.getProperty("cooldownSeconds", "60").strip());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Set cooldownSeconds to an integer from 0 to 3600.", error);
        }
        if (cooldown < 0 || cooldown > 3600) {
            throw new IllegalArgumentException("Set cooldownSeconds to a value from 0 to 3600.");
        }
        return new SwitchSettings(Boolean.parseBoolean(enabled), cooldown);
    }
}
