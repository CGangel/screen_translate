package com.cgangel.screen_translate;

public final class TranslationEngine {
    public static final String API = "api";
    public static final String LOCAL = "local";

    private TranslationEngine() {
    }

    public static String normalize(String value) {
        if (LOCAL.equals(value)) {
            return LOCAL;
        }
        return API;
    }

    public static boolean isLocal(String value) {
        return LOCAL.equals(normalize(value));
    }
}
