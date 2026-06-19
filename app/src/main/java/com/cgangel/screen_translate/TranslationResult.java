package com.cgangel.screen_translate;

public class TranslationResult {
    public final String id;
    public final String translation;

    public TranslationResult(String id, String translation) {
        this.id = id == null ? "" : id;
        this.translation = translation == null ? "" : translation.trim();
    }
}
