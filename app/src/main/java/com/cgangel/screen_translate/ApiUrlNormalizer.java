package com.cgangel.screen_translate;

import java.util.Locale;

public final class ApiUrlNormalizer {
    private ApiUrlNormalizer() {
    }

    public static String normalize(String rawUrl) {
        if (rawUrl == null) {
            return "";
        }
        String value = rawUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.US);
        if (lower.endsWith("/v1")) {
            return value;
        }
        return value + "/v1";
    }

    public static String chatCompletionsUrl(String rawUrl) {
        String normalized = normalize(rawUrl);
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized + "/chat/completions";
    }
}
