package com.cgangel.screen_translate;

public final class TextLayoutMode {
    public static final String SINGLE_LINE = "single_line";
    public static final String MULTI_LINE = "multi_line";

    private TextLayoutMode() {
    }

    public static String normalize(String value) {
        if (SINGLE_LINE.equals(value)) {
            return SINGLE_LINE;
        }
        return MULTI_LINE;
    }

    public static boolean isMultiLine(String value) {
        return MULTI_LINE.equals(normalize(value));
    }
}
