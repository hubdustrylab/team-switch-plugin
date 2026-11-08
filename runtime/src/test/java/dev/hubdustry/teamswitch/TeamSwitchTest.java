package dev.hubdustry.teamswitch;

import arc.Core;
import arc.Events;
import arc.Settings;
import arc.files.Fi;
import arc.mock.MockApplication;
import arc.mock.MockFiles;
import arc.util.CommandHandler;
import arc.util.io.Writes;
import dev.hubdustry.teamswitch.ui.TeamPanel;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.core.GameState;
import mindustry.core.Platform;
import mindustry.core.UI;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.MenuBuilderCallPacket;
import mindustry.gen.MenuBuilderUpdateCallPacket;
import mindustry.gen.Player;
import mindustry.gen.SendMessageCallPacket2;
import mindustry.net.Net;
import mindustry.net.NetConnection;
import mindustry.net.Packet;
import mindustry.ui.builder.MenuResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TeamSwitchTest {
    @TempDir
    static Path data;

    private static final CommandHandler commands = new CommandHandler("/");

    @BeforeAll
    static void loadEngine() {
        Core.app = new MockApplication();
        Core.files = new MockFiles();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi(data.toFile()));
        Vars.platform = new Platform() {};
        Vars.headless = true;
        Vars.loadLocales = false;
        Vars.net = new Net(null) {
            @Override
            public boolean server() {
                return true;
            }

            @Override
            public void send(Object packet, boolean reliable) {}
        };
        Vars.loadSettings();
        Vars.init();
        UI.loadColors();
        Vars.content.createBaseContent();
        Vars.content.init();

        var feature = new TeamSwitch("teamswitch");
        feature.init(data.resolve("team-switch"));
        feature.registerClientCommands(commands);
    }

    @BeforeEach
    void startPvpMap() {
        Groups.clear();
        Vars.state = new GameState();
        Vars.state.rules.pvp = true;
        loadCores(Team.sharded, Team.crux, Team.malis);
        Vars.state.set(GameState.State.playing);
    }

    @Test
    void switchesToAThirdTeamAndSpawnsWithoutTakingTheOldUnit() {
        Player player = join("player", Team.sharded);
        join("ally", Team.sharded);
        join("enemy", Team.crux);
        var oldUnit = UnitTypes.dagger.spawn(Team.sharded, 70f, 70f);
        player.unit(oldUnit);
        player.locale = "vi_VN";

        MenuBuilderCallPacket list = open(player);
        assertEquals("Đổi đội", list.title);
        assertTrue(connection(player).cards().contains("team-2"));
        assertTrue(connection(player).cards().contains("team-3"));
        click(player, list, "team-3");
        MenuBuilderCallPacket confirmation = connection(player).takeMenu();
        assertNull(confirmation.title);
        assertTrue(confirmation.builder.toString().contains("0 người chơi"));
        assertTrue(connection(player).updates.isEmpty());
        click(player, confirmation, "confirm");

        assertSame(Team.malis, player.team());
        assertSame(Team.sharded, oldUnit.team());
        assertNotSame(oldUnit, player.unit());
        assertSame(Team.malis, player.unit().team());
        assertEquals(Team.malis.core().x, player.unit().x);
        assertEquals(Team.malis.core().y, player.unit().y);

        join("malis-ally", Team.malis);
        join("second-malis-ally", Team.malis);
        player.remove();
        Player returning = join("player", Team.malis);
        open(returning);
        assertFalse(connection(returning).cards().contains("team-2"));
    }

    @Test
    void sendsEachPlayerTheirOwnPanelAndMessages() {
        Player spanish = join("spanish", Team.sharded);
        spanish.locale = "es_ES";
        Player chinese = join("chinese", Team.sharded);
        chinese.locale = "zh_TW";
        join("target-player", Team.malis);

        var spanishCatalog = open(spanish);
        assertEquals("Cambiar de equipo", spanishCatalog.title);
        assertTrue(spanishCatalog.builder.toString().contains("Buscar equipos"));

        var chineseCatalog = open(chinese);
        assertEquals("切換隊伍", chineseCatalog.title);
        assertTrue(connection(chinese).cards().contains("紫"));
        var search = new MenuResult("search");
        search.token = chineseCatalog.token;
        search.values.put(TeamPanel.SEARCH_FIELD, "紫");
        Call.menuBuilderChoose(chinese, chineseCatalog.id, search);
        connection(chinese).takeMenu();
        assertTrue(connection(chinese).cards().contains("team-3"));
        assertFalse(connection(chinese).cards().contains("team-2"));

        click(spanish, spanishCatalog, "team-3");
        var confirmation = connection(spanish).takeMenu();
        assertTrue(confirmation.builder.toString().contains("1 jugador"));
        assertTrue(confirmation.builder.toString().contains("Confirmar"));
        click(spanish, confirmation, "confirm");

        assertSame(Team.malis, spanish.team());
        assertTrue(connection(spanish).notices.get(0).startsWith("Te has unido al equipo "));
        assertSame(Team.sharded, chinese.team());
        assertTrue(connection(chinese).notices.isEmpty());
    }

    @Test
    void confirmsWithConnectedDestinationPlayersAndBatchesTheirNativeUnitRows() {
        Player player = join("viewer", Team.sharded);
        for (int i = 0; i < 33; i++) join("source-" + i, Team.sharded);
        for (int i = 0; i < 32; i++) join("member-" + i, Team.malis);
        Player pilot = join("Malis pilot", Team.malis);
        pilot.color.set(0.8f, 0.4f, 0.2f, 1f);
        pilot.unit(UnitTypes.dagger.spawn(Team.malis, 70f, 70f));
        join("joining-Malis", Team.malis).con.hasConnected = false;
        join("other-team", Team.crux);

        MenuBuilderCallPacket list = open(player);
        assertFalse(list.builder.toString().contains("Your team"));
        assertFalse(connection(player).cards().contains("rightSmall"));
        click(player, list, "team-3");

        MenuBuilderCallPacket confirmation = connection(player).takeMenu();
        assertFits(confirmation);
        assertNull(confirmation.title);
        assertTrue(confirmation.builder.toString().contains("33 players"));
        assertFalse(confirmation.builder.toString().contains("Sharded"));
        assertFalse(confirmation.builder.toString().contains("core-shard"));
        var updates = connection(player).updates;
        assertEquals(3, updates.size());
        assertEquals(TeamPanel.ROSTER, updates.get(0).tableId);
        assertEquals(TeamPanel.rosterSlot(32), updates.get(2).tableId);
        for (var update : updates) assertFits(update);

        String rows = connection(player).cards();
        assertTrue(rows.contains("Malis pilot"));
        assertTrue(rows.contains(pilot.color.toString()));
        assertTrue(rows.contains(Team.malis.color.toString()));
        assertTrue(rows.contains("unit-dagger-ui"));
        assertTrue(rows.contains("unit-alpha-ui"));
        assertTrue(rows.contains("underline"));
        assertFalse(rows.contains("joining-Malis"));
        assertFalse(rows.contains("other-team"));
    }

    @Test
    void checksTheLiveRosterAtConfirmationAndRejectsAnotherPlayersToken() {
        Player player = join("player", Team.sharded);
        Player ally = join("ally", Team.sharded);
        join("enemy", Team.crux);

        click(player, open(player), "team-2");
        MenuBuilderCallPacket confirmation = connection(player).takeMenu();
        String confirmationLayout = confirmation.builder.toString();
        assertTrue(confirmationLayout.contains("1 player"));
        assertFalse(confirmationLayout.contains("1 players"));
        open(ally);
        click(ally, confirmation, "confirm");
        assertSame(Team.sharded, ally.team());

        join("late-enemy", Team.crux);
        click(player, confirmation, "confirm");
        assertSame(Team.sharded, player.team());
        open(player);
        assertFalse(connection(player).cards().contains("team-2"));
    }

    @Test
    void worldChangeInvalidatesAnOpenConfirmation() {
        Player player = join("player", Team.sharded);
        click(player, open(player), "team-2");
        MenuBuilderCallPacket confirmation = connection(player).takeMenu();

        Events.fire(new WorldLoadEvent());
        click(player, confirmation, "confirm");

        assertSame(Team.sharded, player.team());
    }

    @Test
    void refusesTheMoveIfTheDestinationLosesItsLastCore() {
        Player player = join("player", Team.sharded);
        click(player, open(player), "team-2");
        MenuBuilderCallPacket confirmation = connection(player).takeMenu();

        Vars.state.teams.unregisterCore(Team.crux.core());
        click(player, confirmation, "confirm");

        assertSame(Team.sharded, player.team());
    }

    @Test
    void streamsAllLiveTeamsIntoOneScrollPaneWithinThePacketLimit() {
        Team last = Team.get(18);
        loadCores(IntStream.rangeClosed(1, last.id).mapToObj(Team::get).toArray(Team[]::new));
        Player player = join("player", Team.sharded);

        MenuBuilderCallPacket catalog = open(player);
        assertTrue(catalog.builder.toString().contains(TeamPanel.SEARCH_FIELD));
        assertFalse(catalog.builder.toString().contains("Next"));
        assertEquals(last.id + 1, connection(player).updates.size());
        assertTrue(connection(player).cards().contains("team-18"));
        assertFits(catalog);
        for (var update : connection(player).updates) assertFits(update);

        click(player, catalog, "team-18");
        click(player, connection(player).takeMenu(), "confirm");
        assertSame(last, player.team());
    }

    @Test
    void searchFiltersChoicesAndNativeBackReturnsToTheFilteredList() {
        Player player = join("player", Team.sharded);
        MenuBuilderCallPacket original = open(player);
        var result = new MenuResult("search");
        result.token = original.token;
        result.values.put(TeamPanel.SEARCH_FIELD, "  mAl  ");
        Call.menuBuilderChoose(player, original.id, result);
        MenuBuilderCallPacket filtered = connection(player).takeMenu();
        assertTrue(filtered.builder.toString().contains("search-clear"));
        assertTrue(connection(player).cards().contains("team-3"));
        assertFalse(connection(player).cards().contains("team-2"));

        click(player, filtered, "team-3");
        MenuBuilderCallPacket confirmation = connection(player).takeMenu();
        assertFalse(confirmation.builder.toString().contains("clicked: \"back\""));
        click(player, confirmation, null);

        MenuBuilderCallPacket returned = connection(player).takeMenu();
        assertTrue(returned.builder.toString().contains("mAl"));
        assertFalse(connection(player).cards().contains("team-2"));
        assertSame(Team.sharded, player.team());
        click(player, returned, "search-clear");
        MenuBuilderCallPacket cleared = connection(player).takeMenu();
        assertTrue(connection(player).cards().contains("team-2"));
        click(player, cleared, null);
        assertNull(connection(player).pollMenu());
    }

    @Test
    void theCommandLeavesOtherModesAndExistingCommandOwnersAlone() {
        Player player = join("player", Team.sharded);
        Vars.state.rules.pvp = false;
        commands.handleMessage("/teamswitch", player);
        assertNull(connection(player).pollMenu());

        var otherCommands = new CommandHandler("/");
        var original = otherCommands.register("teamswitch", "An existing command.", args -> {});
        new TeamSwitch("teamswitch").registerClientCommands(otherCommands);
        assertSame(original, otherCommands.getCommandList().first());
        new TeamSwitch("change-team").registerClientCommands(otherCommands);
        assertNotNull(otherCommands.getCommandList().find(command -> command.text.equals("change-team")));
    }

    private static void loadCores(Team... teams) {
        int columns = Math.min(8, teams.length);
        int rows = (teams.length + columns - 1) / columns;
        Vars.world.loadGenerator(Math.max(48, columns * 9 + 8), Math.max(32, rows * 9 + 8), tiles -> {
            tiles.fill();
            for (int i = 0; i < teams.length; i++) {
                tiles.getn(8 + i % columns * 9, 8 + i / columns * 9).setBlock(Blocks.coreShard, teams[i]);
            }
        });
    }

    private static Player join(String uuid, Team team) {
        Player player = Player.create();
        player.name = uuid;
        player.locale = "en";
        var connection = new TestConnection();
        connection.uuid = uuid;
        connection.hasConnected = true;
        connection.player = player;
        player.con = connection;
        player.team(team);
        player.add();
        return player;
    }

    private static TestConnection connection(Player player) {
        return (TestConnection) player.con;
    }

    private static MenuBuilderCallPacket open(Player player) {
        commands.handleMessage("/teamswitch", player);
        return connection(player).takeMenu();
    }

    private static void click(Player player, MenuBuilderCallPacket menu, String action) {
        var result = new MenuResult(action);
        result.token = menu.token;
        Call.menuBuilderChoose(player, menu.id, result);
    }

    private static void assertFits(Packet packet) {
        var payload = new ByteArrayOutputStream();
        packet.write(new Writes(new DataOutputStream(payload)));
        assertTrue(payload.size() < 24_000);
    }

    private static final class TestConnection extends NetConnection {
        private final List<MenuBuilderCallPacket> menus = new ArrayList<>();
        private final List<MenuBuilderUpdateCallPacket> updates = new ArrayList<>();
        private final List<String> notices = new ArrayList<>();

        private TestConnection() {
            super("test");
        }

        @Override
        public void send(Object packet, boolean reliable) {
            if (packet instanceof MenuBuilderCallPacket menu) {
                menus.add(menu);
                updates.clear();
            } else if (packet instanceof MenuBuilderUpdateCallPacket update) {
                updates.add(update);
            } else if (packet instanceof SendMessageCallPacket2 notice) {
                notices.add(notice.message);
            }
        }

        @Override
        public void close() {}

        private MenuBuilderCallPacket takeMenu() {
            MenuBuilderCallPacket menu = pollMenu();
            assertNotNull(menu, "Expected a native menu packet");
            return menu;
        }

        private MenuBuilderCallPacket pollMenu() {
            return menus.isEmpty() ? null : menus.remove(0);
        }

        private String cards() {
            var cards = new StringBuilder();
            for (var update : updates) {
                if (!update.tableId.equals(TeamPanel.GRID)) cards.append(update.builder);
            }
            return cards.toString();
        }
    }
}
