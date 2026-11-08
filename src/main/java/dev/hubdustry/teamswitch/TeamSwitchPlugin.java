package dev.hubdustry.teamswitch;

import arc.util.CommandHandler;
import mindustry.mod.Plugin;

import java.io.IOException;
import java.util.Properties;

/** Loads Team Switch as a standalone server plugin. */
public final class TeamSwitchPlugin extends Plugin {
    private final TeamSwitch teamSwitch = new TeamSwitch(command());

    @Override
    public void registerClientCommands(CommandHandler handler) {
        teamSwitch.registerClientCommands(handler);
    }

    @Override
    public void init() {
        teamSwitch.init(getConfigFolder().file().toPath());
    }

    private static String command() {
        try (var input = TeamSwitchPlugin.class.getResourceAsStream("/teamswitch/standalone.properties")) {
            if (input == null) throw new IllegalStateException("Missing plugin settings.");
            var properties = new Properties();
            properties.load(input);
            return properties.getProperty("command");
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read the plugin settings.", error);
        }
    }
}
