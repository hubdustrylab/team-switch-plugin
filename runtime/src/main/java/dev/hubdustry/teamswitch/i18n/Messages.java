package dev.hubdustry.teamswitch.i18n;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

public final class Messages {
    private static final String BUNDLE = "teamswitch.messages";
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static final Set<String> SUPPORTED = Set.of(
            "be", "bg", "ca", "cs", "da", "de", "en", "es", "et", "eu", "fi", "fil", "fr", "hu",
            "id-id", "it", "ja", "ko", "lt", "nl", "nl-be", "pl", "pt-br", "pt-pt", "ro", "router",
            "ru", "sr", "sv", "th", "tk", "tr", "uk-ua", "vi", "zh-cn", "zh-tw");
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("id", "id-id"), Map.entry("in", "id-id"), Map.entry("in-id", "id-id"),
            Map.entry("pt", "pt-br"), Map.entry("uk", "uk-ua"), Map.entry("zh", "zh-cn"),
            Map.entry("zh-hans", "zh-cn"), Map.entry("zh-hant", "zh-tw"),
            Map.entry("zh-hk", "zh-tw"), Map.entry("zh-mo", "zh-tw"));

    private Messages() {}

    public static ResourceBundle forLocale(String locale) {
        String tag = resolve(locale);
        Locale selected = tag.equals("en") ? Locale.ROOT : Locale.forLanguageTag(tag);
        return ResourceBundle.getBundle(BUNDLE, selected, Messages.class.getClassLoader(), CONTROL);
    }

    static String resolve(String locale) {
        if (locale == null || locale.length() > 64) return "en";
        String tag = locale.strip().replace('_', '-').toLowerCase(Locale.ROOT);
        while (!tag.isEmpty()) {
            if (SUPPORTED.contains(tag)) return tag;
            String alias = ALIASES.get(tag);
            if (alias != null) return alias;
            int separator = tag.lastIndexOf('-');
            if (separator < 0) break;
            tag = tag.substring(0, separator);
        }
        return "en";
    }

    static Set<String> supportedLocales() {
        return SUPPORTED;
    }

    public static String text(ResourceBundle messages, String key, Object... values) {
        return String.format(Locale.ROOT, messages.getString(key), values);
    }
}
