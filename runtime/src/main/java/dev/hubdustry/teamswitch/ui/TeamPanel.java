package dev.hubdustry.teamswitch.ui;

import arc.graphics.Color;
import arc.util.Scaling;
import mindustry.game.Team;
import mindustry.ui.builder.UiBuilder.ImageBuilder;
import mindustry.ui.builder.UiBuilder.NodeBuilder;

import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Function;

import static dev.hubdustry.teamswitch.i18n.Messages.text;
import static mindustry.ui.builder.UiBuilder.*;

public final class TeamPanel {
    public static final String SEARCH_FIELD = "teamswitch-search";
    public static final String GRID = "teamswitch-grid";
    public static final String ROSTER = "teamswitch-roster";
    public static final int SEARCH_LIMIT = 80;
    public static final int MEMBERS_PER_BATCH = 32;
    private static final float MEMBER_ICON_SIZE = 50f;
    private static final float MEMBER_ROW_HEIGHT = MEMBER_ICON_SIZE + 15f;

    public record Card(Team team, String name, String core, int players, String action) {}
    public record Member(String name, String color, String icon, String fallbackIcon) {}

    private record CardSize(float width, float columnStep, float inset) {}

    private TeamPanel() {}

    public static NodeBuilder<?> catalog(ResourceBundle messages, String query, int cardCount) {
        var root = table().grow().margin(8f).align("top");
        var search = table().growX().padBottom(10f);
        search.add(buttonTable().style("cleart").margin(0f).clicked("search").size(40f)
                .add(image("zoom").size(28f)));
        search.add(field(query).id(SEARCH_FIELD).hint(text(messages, "searchTeams"))
                .maxLength(SEARCH_LIMIT).enter("search").growX().height(40f));
        if (!query.isEmpty()) {
            search.add(buttonTable().style("cleart").margin(0f).clicked("search-clear").size(40f)
                    .add(image("cancel").size(24f)));
        }
        root.add(search).row();

        var scroll = pane().grow().minWidth(0f).minHeight(0f).align("top");
        if (cardCount == 0) {
            scroll.add(table().add(image("zoom").size(24f).padRight(8f))
                    .add(label(text(messages, "noSearchResults")).wrap().width(200f)));
        } else {
            scroll.add(responsive(size -> grid(cardCount, size)));
        }
        root.add(scroll);
        return table().add(root);
    }

    public static NodeBuilder<?> slots(List<Card> cards) {
        var slots = table();
        for (Card card : cards) {
            slots.add(table().id(slot(card.team)).pad(6f));
        }
        return slots;
    }

    public static String slot(Team team) {
        return "teamswitch-card-" + team.id;
    }

    public static NodeBuilder<?> card(Card card, Team current) {
        return responsive(size -> cardFace(card, card.team == current, size.width));
    }

    private static NodeBuilder<?> grid(int count, CardSize size) {
        var variants = table();
        int maxColumns = Math.min(12, count);
        for (int columns = 1; columns <= maxColumns; columns++) {
            var range = table();
            if (columns < maxColumns) {
                range.condition("width < " + ((columns + 1) * size.columnStep + size.inset));
            }
            range.add(table().id(GRID).wrap(true).growX().align("topLeft")
                    .maxWidth(columns * (size.width + 12f))
                    .condition("width >= " + (columns == 1 ? 0f : columns * size.columnStep + size.inset)));
            variants.add(range);
        }
        return variants;
    }

    private static NodeBuilder<?> responsive(Function<CardSize, NodeBuilder<?>> build) {
        var tall = table().condition("height >= 560");
        tall.add(build.apply(new CardSize(200f, 222f, 0f)).condition("width >= 600"));
        var narrow = table().condition("width < 600");
        narrow.add(build.apply(new CardSize(152f, 164f, 60f)).condition("width >= 388"));
        narrow.add(build.apply(new CardSize(136f, 148f, 60f)).condition("width < 388"));
        tall.add(narrow);
        return table().add(tall)
                .add(build.apply(new CardSize(128f, 140f, 60f)).condition("height < 560"));
    }

    private static NodeBuilder<?> cardFace(Card card, boolean current, float width) {
        float preview = width - 18f;
        boolean eligible = card.action != null;
        var face = buttonTable().style(current ? "squareTogglet" : "grayt")
                .checked(current).disabled(!eligible).margin(9f).width(width).height(preview + 74f);
        if (eligible) face.clicked(card.action);

        face.add(label(coloredName(card.team, card.name)).wrap().labelAlign("center").height(28f).growX()).row();
        face.add(image("whiteui").color(card.team.color.toString()).height(2f).growX().padBottom(5f)).row();
        face.add(corePreview(card.team, card.core, preview)).row();

        var stats = table().growX().padTop(6f);
        stats.add(icon("playersSmall", 18f).padRight(7f));
        stats.add(label(Integer.toString(card.players)));
        face.add(stats);
        return face;
    }

    public static NodeBuilder<?> confirm(ResourceBundle messages, int members) {
        // Match RTV's height-first sizing while letting the roster determine the content height.
        var tall = table().growX().condition("height >= 560");
        tall.add(confirmationWidths(messages, members, 420f)
                .condition("height >= 720"));
        tall.add(confirmationWidths(messages, members, 300f)
                .condition("height < 720"));

        var shortScreen = table().growX().condition("height < 560");
        shortScreen.add(confirmationWidths(messages, members, 220f)
                .condition("height >= 420"));
        shortScreen.add(confirmationWidths(messages, members, 130f)
                .condition("height < 420"));
        return table().grow().margin(8f).add(tall).add(shortScreen);
    }

    private static NodeBuilder<?> confirmationWidths(ResourceBundle messages, int members, float rosterHeight) {
        return table().growX()
                .add(confirmation(messages, members, 460f, rosterHeight)
                        .condition("width >= 600"))
                .add(confirmation(messages, members, 360f, rosterHeight)
                        .condition("width < 600"));
    }

    private static NodeBuilder<?> confirmation(ResourceBundle messages, int members, float width, float rosterHeight) {
        var panel = table().growX().minWidth(0f).maxWidth(width).pad(8f);
        String countKey = members == 1 ? "players.single" : "players";
        panel.add(label(text(messages, countKey, members)).padBottom(8f)).row();
        if (members > 0) {
            panel.add(pane().id(ROSTER).growX().minWidth(0f).align("topLeft")
                    .height(Math.min(rosterHeight, members * MEMBER_ROW_HEIGHT)).padBottom(12f)).row();
        }

        var confirm = buttonTable().style("togglet").margin(0f).clicked("confirm").growX().height(56f);
        var face = table().background("button").margin(10f).grow();
        face.add(icon("ok", 28f));
        face.add(label(text(messages, "confirm")).labelAlign("center").growX());
        face.add(space().width(28f));
        return panel.add(confirm.add(face));
    }

    public static NodeBuilder<?> rosterSlots(int members) {
        var slots = table().growX().align("topLeft");
        for (int start = 0; start < members; start += MEMBERS_PER_BATCH) {
            slots.add(table().id(rosterSlot(start)).growX()).row();
        }
        return slots;
    }

    public static String rosterSlot(int start) {
        return ROSTER + "-" + start;
    }

    public static NodeBuilder<?> rosterRows(Team team, List<Member> members) {
        var rows = table().growX();
        for (Member member : members) {
            var unit = stack().size(MEMBER_ICON_SIZE);
            unit.add(image("whiteui").color("252529"));
            unit.add(table().add(image(member.icon).placeholder(member.fallbackIcon)
                    .scaling(Scaling.bounded).size(MEMBER_ICON_SIZE - 16f)));
            unit.add(image("white-pane").color(team.color.toString()));

            var row = table().growX().align("left").margin(5f).background("underline");
            row.add(unit);
            row.add(label("[#" + member.color + "]" + member.name + "[]")
                    .style("outlineLabel").wrap().growX().minWidth(0f).labelAlign("left").pad(10f)).row();
            row.add(space().height(5f).colspan(2));
            rows.add(row).row();
        }
        return rows;
    }

    private static NodeBuilder<?> corePreview(Team team, String core, float size) {
        float sprite = size * 0.71f;
        var art = stack().size(sprite);
        art.add(image(core).placeholder("players").scaling(Scaling.fit));
        art.add(image(core + "-team").placeholder("clear").color(team.color.toString()).scaling(Scaling.fit));
        return stack().size(size)
                .add(image("whiteui").color("252529"))
                .add(table().add(art))
                .add(image("white-pane").color("56565D"));
    }

    private static ImageBuilder icon(String name, float size) {
        return image(name).size(size).scaling(Scaling.fit);
    }

    public static String teamName(Team team) {
        String name = team.name == null || team.name.isBlank() ? "Team " + team.id : team.name.strip();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static String teamName(ResourceBundle messages, Team team) {
        String key = "team." + team.name + ".name";
        return messages.containsKey(key) ? messages.getString(key) : teamName(team);
    }

    public static String coloredName(Team team, String name) {
        Color color = team.color.cpy();
        float brightness = color.r * 0.21f + color.g * 0.72f + color.b * 0.07f;
        if (brightness < 0.44f) color.lerp(Color.white, 0.42f);
        return "[#" + color + "]" + name.replace("[", "[[") + "[]";
    }
}
