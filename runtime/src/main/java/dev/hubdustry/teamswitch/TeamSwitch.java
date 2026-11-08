package dev.hubdustry.teamswitch;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import dev.hubdustry.teamswitch.config.SwitchSettings;
import dev.hubdustry.teamswitch.i18n.Messages;
import dev.hubdustry.teamswitch.ui.TeamPanel;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.BlockUnitc;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.type.UnitType;
import mindustry.ui.Menus;
import mindustry.ui.builder.MenuBuilder;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder.NodeBuilder;
import mindustry.world.blocks.storage.CoreBlock;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static dev.hubdustry.teamswitch.i18n.Messages.text;

/** Adds the team switch command and panel to a server plugin. */
public final class TeamSwitch {
    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final String DEFAULT_UNIT_ICON = "unit-alpha-ui";
    private static final long CONFIRM_LIFETIME_MS = 120_000;
    private static final long CLOCK_ORIGIN = System.nanoTime();

    private record Panel(long token, long openedAt, Team source, String query,
                         Map<String, Team> choices, Team destination) {}

    private final String command;
    private final SecureRandom tokens = new SecureRandom();
    private final Map<Player, Panel> panels = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private SwitchSettings settings = SwitchSettings.DISABLED;
    private int menuId = -1;

    public TeamSwitch(String command) {
        if (command == null || !COMMAND_NAME.matcher(command).matches()) {
            throw new IllegalArgumentException("Use a lowercase command name with up to 32 characters.");
        }
        this.command = command;
    }

    public void init(Path dataDirectory) {
        if (menuId != -1) throw new IllegalStateException("Team Switch is already active.");
        Path config = dataDirectory.resolve("config.properties");
        try {
            settings = SwitchSettings.load(config);
            Log.info("Team Switch uses @.", config);
        } catch (IOException | IllegalArgumentException error) {
            Log.err("Team Switch is off. Cannot read @. @", config, error.getMessage());
        }

        menuId = Menus.registerMenuBuilder(this::onMenu);
        Events.on(PlayerLeave.class, event -> panels.remove(event.player));
        Events.on(GameOverEvent.class, event -> closePanels());
        Events.on(WorldLoadEvent.class, event -> {
            cooldowns.clear();
            closePanels();
        });
    }

    public void registerClientCommands(CommandHandler handler) {
        if (handler.getCommandList().find(entry -> entry.text.equals(command)) != null) {
            Log.warn("Team Switch cannot register /@. This command is in use.", command);
            return;
        }
        handler.<Player>register(command, "Open the PvP team list.", (args, player) -> {
            Panel panel = panels.get(player);
            if (panel == null || now() - panel.openedAt >= 350) {
                showTeams(player, panel == null ? "" : panel.query);
            }
        });
    }

    private void showTeams(Player player, String query) {
        if (!available(player)) return;

        var teams = new ArrayList<Team>();
        for (var team : Vars.state.teams.getActive()) {
            if (playable(team.team)) teams.add(team.team);
        }
        if (teams.size() < 2) {
            tell(player, "noTeams");
            return;
        }

        int[] counts = roster();
        long cooldown = remainingCooldown(player, now());
        teams.sort(Comparator.comparingInt(team -> team.id));
        String filter = query.toLowerCase(Locale.ROOT);
        Team source = player.team();
        ResourceBundle messages = Messages.forLocale(player.locale);
        var choices = new HashMap<String, Team>();
        var cards = new ArrayList<TeamPanel.Card>();
        for (Team team : teams) {
            String name = TeamPanel.teamName(messages, team);
            if (!name.toLowerCase(Locale.ROOT).contains(filter)
                    && !TeamPanel.teamName(team).toLowerCase(Locale.ROOT).contains(filter)) continue;
            String action = null;
            if (cooldown == 0 && balanced(source, team, counts)) {
                action = "team-" + team.id;
                choices.put(action, team);
            }
            cards.add(new TeamPanel.Card(team, name, team.core().block.name, counts[team.id], action));
        }

        show(player, messages, TeamPanel.catalog(messages, query, cards.size()), query, choices, null);
        if (cards.isEmpty()) return;

        // Install slots in one scroll pane, then send each card within the native packet limit.
        MenuBuilder.of(TeamPanel.slots(cards)).id(menuId).update(player, TeamPanel.GRID);
        for (TeamPanel.Card card : cards) {
            MenuBuilder.of(TeamPanel.card(card, source)).id(menuId).update(player, TeamPanel.slot(card.team()));
        }
    }

    private void show(Player player, ResourceBundle messages, NodeBuilder<?> body,
                      String query, Map<String, Team> choices, Team destination) {
        long token = tokens.nextLong();
        panels.put(player, new Panel(token, now(), player.team(), query, Map.copyOf(choices), destination));
        MenuBuilder.of(body).id(menuId).token(token)
                .title(destination == null ? text(messages, "title") : null).show(player);
    }

    private void onMenu(Player player, MenuResult result) {
        if (!connected(player) || result == null) return;
        Panel panel = panels.get(player);
        if (panel == null || panel.token != result.token) return;
        panels.remove(player);
        String action = result.result;
        if (action == null) {
            if (panel.destination != null) showTeams(player, panel.query);
            return;
        }
        if (player.team() != panel.source
                || (panel.destination != null && now() - panel.openedAt >= CONFIRM_LIFETIME_MS)) {
            tell(player, "stale", "/" + command);
            return;
        }
        if (!available(player)) return;
        if (panel.destination != null) {
            if (action.equals("confirm")) switchTeam(player, panel.destination);
            return;
        }

        switch (action) {
            case "search" -> {
                String query = searchQuery(result.values.get(TeamPanel.SEARCH_FIELD));
                if (query != null) showTeams(player, query);
            }
            case "search-clear" -> showTeams(player, "");
            default -> {
                Team target = panel.choices.get(action);
                if (target != null) chooseTeam(player, panel.query, target);
            }
        }
    }

    private static String searchQuery(Object value) {
        if (value == null) return "";
        if (!(value instanceof String query) || query.length() > TeamPanel.SEARCH_LIMIT) return null;
        return query.strip();
    }

    private void chooseTeam(Player player, String query, Team target) {
        int[] counts = roster();
        if (!canSwitch(player, target, counts, now())) return;

        var members = new ArrayList<TeamPanel.Member>();
        for (Player member : Groups.player) {
            if (member.team() == target && connected(member)) members.add(appearance(member, player.team()));
        }
        ResourceBundle messages = Messages.forLocale(player.locale);
        show(player, messages, TeamPanel.confirm(messages, members.size()),
                query, Map.of(), target);
        if (members.isEmpty()) return;

        MenuBuilder.of(TeamPanel.rosterSlots(members.size())).id(menuId).update(player, TeamPanel.ROSTER);
        for (int start = 0; start < members.size(); start += TeamPanel.MEMBERS_PER_BATCH) {
            int end = Math.min(start + TeamPanel.MEMBERS_PER_BATCH, members.size());
            MenuBuilder.of(TeamPanel.rosterRows(target, members.subList(start, end))).id(menuId)
                    .update(player, TeamPanel.rosterSlot(start));
        }
    }

    private static TeamPanel.Member appearance(Player member, Team viewerTeam) {
        String name = member.name();
        String color = member.color().toString();
        var unit = member.unit();
        UnitType type;
        // Match the stock list when the unit cannot be resolved by the viewing client.
        if (member.dead() || unit.isSyncHidden(viewerTeam)) {
            var core = member.bestCore();
            type = core == null ? UnitTypes.alpha : ((CoreBlock) core.block).unitType;
        } else if (unit instanceof BlockUnitc blockUnit && blockUnit.tile() != null) {
            String block = blockUnit.tile().block.name;
            return new TeamPanel.Member(name, color, "block-" + block + "-ui", block);
        } else {
            type = unit.type();
        }
        return new TeamPanel.Member(name, color, "unit-" + type.name + "-ui", DEFAULT_UNIT_ICON);
    }

    private void switchTeam(Player player, Team target) {
        long time = now();
        if (!canSwitch(player, target, roster(), time)) return;

        // Player.team() also changes the team of any unit the player still controls.
        player.clearUnit();
        player.team(target);
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= time);
        if (settings.cooldownSeconds() > 0) {
            cooldowns.put(player.uuid(), time + TimeUnit.SECONDS.toMillis(settings.cooldownSeconds()));
        }
        player.checkSpawn();
        ResourceBundle messages = Messages.forLocale(player.locale);
        String name = TeamPanel.teamName(messages, target);
        player.sendMessage(text(messages, "switched", TeamPanel.coloredName(target, name)));
    }

    private boolean canSwitch(Player player, Team target, int[] counts, long time) {
        if (!playable(target)) {
            tell(player, "unavailableTeam", "/" + command);
            return false;
        }
        long cooldown = remainingCooldown(player, time);
        if (cooldown > 0) {
            tell(player, "cooldown", cooldown);
            return false;
        }
        if (!balanced(player.team(), target, counts)) {
            tell(player, "balanceChanged", "/" + command);
            return false;
        }
        return true;
    }

    private boolean available(Player player) {
        if (!connected(player)) return false;
        if (!settings.enabled()) {
            tell(player, "disabled");
            return false;
        }
        if (!Vars.state.isGame() || Vars.state.gameOver || !Vars.state.rules.pvp) {
            tell(player, "pvpOnly");
            return false;
        }
        if (!playable(player.team())) {
            tell(player, "sourceUnavailable");
            return false;
        }
        return true;
    }

    private static boolean playable(Team team) {
        var rules = Vars.state.rules;
        return team != Team.derelict && !(rules.waves && team == rules.waveTeam)
                && team.rules().protectCores && team.isAlive();
    }

    private static boolean balanced(Team source, Team target, int[] counts) {
        return counts[target.id] < counts[source.id];
    }

    private static int[] roster() {
        int[] counts = new int[Team.all.length];
        for (Player player : Groups.player) {
            if (connected(player)) counts[player.team().id]++;
        }
        return counts;
    }

    private static boolean connected(Player player) {
        return player != null && player.isAdded() && player.con != null && player.con.hasConnected
                && !player.con.hasDisconnected && !player.con.kicked;
    }

    private long remainingCooldown(Player player, long time) {
        long deadline = cooldowns.getOrDefault(player.uuid(), 0L);
        return Math.max(0L, deadline - time + 999L) / 1000L;
    }

    private void closePanels() {
        var viewers = List.copyOf(panels.keySet());
        panels.clear();
        for (Player player : viewers) {
            if (connected(player)) Call.hideMenuBuilder(player.con, menuId);
        }
    }

    private static void tell(Player player, String key, Object... values) {
        player.sendMessage(text(Messages.forLocale(player.locale), key, values));
    }

    private static long now() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - CLOCK_ORIGIN);
    }
}
