# Fork and integrate

## Build a fork

Select **Fork** to keep a link to this repository.
Select **Use this template** to start a new repository.

1. Clone your repository.
2. Set the plugin values in [gradle.properties](../gradle.properties).

   ```properties
   pluginId=arena-team-switch
   pluginDisplayName=Arena Team Switch
   pluginAuthor=Your team
   pluginDescription=Team selection for Arena PvP servers.
   pluginCommand=arena-team
   pluginVersion=1.0.0
   ```

3. Run `bash ./gradlew clean build`. On Windows, run `./gradlew.bat clean build`.
4. Install `build/libs/arena-team-switch-1.0.0.jar`.
5. In a PvP match, run `/arena-team`.

Start `pluginId` and `pluginCommand` with a lowercase letter.
Use only lowercase letters, digits, and hyphens in these values.
Use a command name that is available on the server.
This example uses the data directory `config/mods/arena-team-switch/`.

To change panel text, edit the UTF-8 [message files](../runtime/src/main/resources/teamswitch).
Keep the format specifiers, such as `%d` and `%s`.
Run the tests after you change a message file.

## Add Team Switch to a server plugin

Use Java 17 or later.
Put a checkout of this project in `vendor/team-switch-plugin`.
Use the Git revision that you tested.

Add this line to the host's `settings.gradle`.

```groovy
includeBuild('vendor/team-switch-plugin')
```

Add this dependency to the host's `build.gradle`.

```groovy
dependencies {
    implementation 'dev.hubdustry:team-switch-runtime'
}
```

Use the same Mindustry version in both builds.
Create one `TeamSwitch` instance with the client command name.
Call `registerClientCommands` from the host's command hook.
Call `init` from the host's `init` method.
Pass a directory under the host's `getConfigFolder()`.

Put the runtime classes and resources in the host JAR.
Use the host's `plugin.json` as the JAR descriptor.
Use Mindustry and Arc as compile-only dependencies.
See the [example source](../examples/embedded/src/main/java/example/server/ExampleServerPlugin.java) and [build](../examples/embedded/build.gradle).

## Test the example

1. Run `bash ./gradlew -p examples/embedded build`.
   On Windows, run `./gradlew.bat -p examples/embedded build`.
2. Install `examples/embedded/build/libs/server-with-team-switch.jar`.
3. Run `example-status` in the server console.
4. In a PvP match, run `/changeteam`.

The example uses `config/mods/server-with-team-switch/team-switch/config.properties`.
Use one Team Switch instance on each server.
Include the [GPL-3.0 license](../LICENSE) when you distribute the JAR.
Give users access to the corresponding source.
