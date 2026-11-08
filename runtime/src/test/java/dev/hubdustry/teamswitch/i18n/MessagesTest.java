package dev.hubdustry.teamswitch.i18n;

import mindustry.gen.Iconc;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%%|%[ds]");

    @Test
    void coversAllGameLocalesWithTheSameKeysAndArguments() throws IOException {
        var locales = new TreeSet<String>();
        try (var input = MessagesTest.class.getResourceAsStream("/locales")) {
            assertNotNull(input);
            new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip).filter(code -> !code.isEmpty()).forEach(locales::add);
        }
        locales.add("router");
        var tags = new TreeSet<String>();
        var english = readBundle("en");
        for (String locale : locales) {
            String tag = locale.replace('_', '-').toLowerCase(Locale.ROOT);
            tags.add(tag);
            assertEquals(tag, Messages.resolve(locale), locale);
            var bundled = readBundle(locale);
            assertEquals(english.stringPropertyNames(), bundled.stringPropertyNames(), locale);
            var resolved = Messages.forLocale(locale);
            for (String key : english.stringPropertyNames()) {
                String message = bundled.getProperty(key);
                String label = locale + " " + key;
                assertFalse(message.isBlank(), label);
                assertEquals(message, resolved.getString(key), label);
                assertEquals(arguments(english.getProperty(key)), arguments(message), label);
            }
        }
        assertEquals(tags, Messages.supportedLocales());
    }

    @Test
    void resolvesGameCodesAliasesAndUnknownCodes() {
        assertEquals("id-id", Messages.resolve("in_ID"));
        assertEquals("nl-be", Messages.resolve("nl-BE"));
        assertEquals("pt-pt", Messages.resolve("pt_PT_POSIX"));
        assertEquals("pt-br", Messages.resolve("pt"));
        assertEquals("uk-ua", Messages.resolve("uk"));
        assertEquals("zh-cn", Messages.resolve("zh-Hans"));
        assertEquals("zh-tw", Messages.resolve("zh-Hant-TW"));
        assertEquals("zh-tw", Messages.resolve("zh_HK"));
        assertEquals("vi", Messages.resolve("vi_VN"));
        assertEquals("en", Messages.resolve("zz-ZZ"));
        assertEquals("en", Messages.resolve(null));
    }

    @Test
    void routerKeepsCountsAndCommandNames() {
        var router = Messages.forLocale("router");
        assertEquals(String.valueOf(Iconc.blockRouter).repeat(7), Messages.text(router, "confirm"));
        assertTrue(Messages.text(router, "players", 10).startsWith("10 "));
        assertTrue(Messages.text(router, "stale", "/change-team").contains("/change-team"));
    }

    private static List<String> arguments(String template) {
        return PLACEHOLDER.matcher(template).results().map(MatchResult::group)
                .filter(token -> !token.equals("%%")).sorted().toList();
    }

    private static Properties readBundle(String locale) throws IOException {
        String suffix = locale.equals("en") ? "" : "_" + locale;
        String path = "/teamswitch/messages" + suffix + ".properties";
        var properties = new Properties();
        try (var input = MessagesTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
