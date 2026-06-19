package com.cgangel.screen_translate;

public final class TranslationMode {
    public static final String REALTIME = "realtime";
    public static final String CLICK = "click";

    private TranslationMode() {
    }

    public static String normalize(String value) {
        if (CLICK.equals(value)) {
            return CLICK;
        }
        return REALTIME;
    }

    public static boolean isClick(String value) {
        return CLICK.equals(normalize(value));
    }
}
