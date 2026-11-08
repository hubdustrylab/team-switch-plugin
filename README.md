# Team Switch

Team selection for Mindustry PvP servers. Install the JAR or add the runtime to your server plugin.
The server checks team sizes before each change.

## Install

Use Mindustry v160 or v160.7 and Java 17 or later.

1. Download the plugin JAR from [Releases](https://github.com/hubdustrylab/team-switch-plugin/releases).
2. Stop the server.
3. If you update the plugin, remove the old JAR from `config/mods/`.
4. Put the new JAR in `config/mods/`.
5. Start the server.

## Change teams

1. In a PvP match, run `/teamswitch`.
2. Select a team with fewer players than your team.
3. Check the player list for that team.
4. Select **Confirm**.

To find a team, enter its name in the search box. Press Enter or select the search icon.
The server checks the team sizes again when you select **Confirm**.
Your unit stays with your old team. You start at a core on your new team.

## Languages

The plugin uses the language each player selects in Mindustry.
It includes all 35 languages in v160 and v160.7 and the `router` mode.
After you change the language, restart Mindustry. Then connect to the server again.
The plugin uses English if it has no match for the locale.

## Settings

The plugin creates `config/mods/team-switch/config.properties` on its first start.
Restart the server after you change the file.

| Key | Default | Valid values | Function |
| --- | --- | --- | --- |
| `enabled` | `true` | `true` or `false` | Turns team changes on or off. |
| `cooldownSeconds` | `60` | 0 to 3600 | Sets the delay between team changes for each player on the same map. |

Set `cooldownSeconds` to `0` to remove the delay.

## Build

Run `bash ./gradlew build`. On Windows, run `./gradlew.bat build`.
The JAR is in `build/libs/`.
Add `-PmindustryVersion=v160` to check the minimum server version.

See [Fork and integrate](docs/integration.md) to build a fork or add Team Switch to a server plugin.

## License

Project code uses [GPL-3.0-only](LICENSE).
The player count, confirmation label, and team names use the [Mindustry language files](https://github.com/Anuken/Mindustry/tree/v160.7/core/assets/bundles).
