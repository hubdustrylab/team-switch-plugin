package example.server;

import arc.util.CommandHandler;
import arc.util.Log;
import dev.hubdustry.teamswitch.TeamSwitch;
import mindustry.mod.Plugin;

public final class ExampleServerPlugin extends Plugin {
    private final TeamSwitch teamSwitch = new TeamSwitch("changeteam");

    @Override
    public void registerClientCommands(CommandHandler handler) {
        teamSwitch.registerClientCommands(handler);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("example-status", "", "Show the example plugin status.",
                args -> Log.info("The example plugin is active."));
    }

    @Override
    public void init() {
        teamSwitch.init(getConfigFolder().child("team-switch").file().toPath());
    }
}
