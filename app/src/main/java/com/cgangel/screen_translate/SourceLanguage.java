package com.cgangel.screen_translate;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SourceLanguage {
    public static final String AUTO_LABEL = "\u81ea\u52a8\u8bc6\u522b";

    private static final Map<String, String> KNOWN_TAGS = new HashMap<>();

    static {
        add(AUTO_LABEL, "");
        add("auto", "");
        add("自动", "");
        add("English", "en");
        add("英文", "en");
        add("英语", "en");
        add("日本語", "ja");
        add("日文", "ja");
        add("日语", "ja");
        add("简体中文", "zh");
        add("繁體中文", "zh");
        add("繁体中文", "zh");
        add("中文", "zh");
        add("한국어", "ko");
        add("韩语", "ko");
        add("韓語", "ko");
    }

    private SourceLanguage() {
    }

    public static String normalizeLabel(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return AUTO_LABEL;
        }
        if (KNOWN_TAGS.containsKey(normalized) && KNOWN_TAGS.get(normalized).isEmpty()) {
            return AUTO_LABEL;
        }
        return value == null ? AUTO_LABEL : value.trim();
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
        if (normalized.startsWith("en")) {
            return "en";
        }
        if (normalized.startsWith("ja") || normalized.startsWith("jp")) {
            return "ja";
        }
        if (normalized.startsWith("zh")) {
            return "zh";
        }
        if (normalized.startsWith("ko") || normalized.startsWith("kr")) {
            return "ko";
        }
        return "";
    }

    public static boolean isAuto(String value) {
        return resolveLanguageTag(value).isEmpty();
    }

    public static String promptLabel(String value) {
        return isAuto(value) ? "Auto-detect" : normalizeLabel(value);
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
