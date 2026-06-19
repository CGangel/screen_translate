package com.cgangel.screen_translate;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LocalTranslationLanguage {
    private static final Map<String, String> KNOWN_TAGS = new HashMap<>();

    static {
        add("简体中文", "zh");
        add("繁體中文", "zh");
        add("繁体中文", "zh");
        add("中文", "zh");
        add("汉语", "zh");
        add("漢語", "zh");
        add("english", "en");
        add("英文", "en");
        add("日本語", "ja");
        add("日语", "ja");
        add("日文", "ja");
        add("한국어", "ko");
        add("韩语", "ko");
        add("韓語", "ko");
        add("français", "fr");
        add("francais", "fr");
        add("法语", "fr");
        add("deutsch", "de");
        add("德语", "de");
        add("español", "es");
        add("espanol", "es");
        add("西班牙语", "es");
        add("português", "pt");
        add("portugues", "pt");
        add("葡萄牙语", "pt");
        add("русский", "ru");
        add("俄语", "ru");
    }

    private LocalTranslationLanguage() {
    }

    public static String resolveLanguageTag(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return "";
        }
        String known = KNOWN_TAGS.get(normalized);
        if (known != null) {
            return known;
        }
        if (normalized.startsWith("zh")) {
            return "zh";
        }
        if (normalized.startsWith("en")) {
            return "en";
        }
        if (normalized.startsWith("ja") || normalized.startsWith("jp")) {
            return "ja";
        }
        if (normalized.startsWith("ko") || normalized.startsWith("kr")) {
            return "ko";
        }
        if (normalized.matches("[a-z]{2,3}(-[a-z0-9]+)*")) {
            return normalized;
        }
        return "";
    }

    public static boolean hasLanguageTag(String value) {
        return !resolveLanguageTag(value).isEmpty();
    }

    private static void add(String label, String tag) {
        KNOWN_TAGS.put(normalize(label), tag);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
    }
}
