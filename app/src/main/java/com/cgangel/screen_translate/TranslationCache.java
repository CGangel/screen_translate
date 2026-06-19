package com.cgangel.screen_translate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TranslationCache {
    private final int maxEntries;
    private final LinkedHashMap<String, List<TranslationResult>> cache;

    public TranslationCache(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.cache = new LinkedHashMap<String, List<TranslationResult>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<TranslationResult>> eldest) {
                return size() > TranslationCache.this.maxEntries;
            }
        };
    }

    public synchronized List<TranslationResult> get(String key) {
        return cache.get(key);
    }

    public synchronized void put(String key, List<TranslationResult> results) {
        if (key != null && results != null) {
            cache.put(key, results);
        }
    }

    public static String key(ApiSettings settings, String textHash) {
        return TextHash.sha256(settings.translationEngine
                + "\n"
                + settings.normalizedBaseUrl
                + "\n"
                + settings.model
                + "\n"
                + settings.sourceLanguage
                + "\n"
                + settings.targetLanguage
                + "\n"
                + textHash);
    }
}
