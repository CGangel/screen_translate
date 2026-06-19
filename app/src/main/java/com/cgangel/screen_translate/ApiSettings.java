package com.cgangel.screen_translate;

public class ApiSettings {
    public final String apiBaseUrl;
    public final String normalizedBaseUrl;
    public final String model;
    public final String apiKey;
    public final String sourceLanguage;
    public final String targetLanguage;
    public final String translationMode;
    public final String translationEngine;
    public final String textLayoutMode;

    public ApiSettings(String apiBaseUrl, String model, String apiKey, String targetLanguage) {
        this(apiBaseUrl, model, apiKey, targetLanguage, TranslationMode.REALTIME);
    }

    public ApiSettings(
            String apiBaseUrl,
            String model,
            String apiKey,
            String targetLanguage,
            String translationMode
    ) {
        this(
                apiBaseUrl,
                model,
                apiKey,
                SourceLanguage.AUTO_LABEL,
                targetLanguage,
                translationMode,
                TranslationEngine.API
        );
    }

    public ApiSettings(
            String apiBaseUrl,
            String model,
            String apiKey,
            String sourceLanguage,
            String targetLanguage,
            String translationMode,
            String translationEngine
    ) {
        this(
                apiBaseUrl,
                model,
                apiKey,
                sourceLanguage,
                targetLanguage,
                translationMode,
                translationEngine,
                TextLayoutMode.MULTI_LINE
        );
    }

    public ApiSettings(
            String apiBaseUrl,
            String model,
            String apiKey,
            String sourceLanguage,
            String targetLanguage,
            String translationMode,
            String translationEngine,
            String textLayoutMode
    ) {
        this.apiBaseUrl = safe(apiBaseUrl);
        this.normalizedBaseUrl = ApiUrlNormalizer.normalize(this.apiBaseUrl);
        this.model = safe(model);
        this.apiKey = safe(apiKey);
        this.sourceLanguage = SourceLanguage.normalizeLabel(sourceLanguage);
        this.targetLanguage = safe(targetLanguage).isEmpty()
                ? "\u7b80\u4f53\u4e2d\u6587"
                : safe(targetLanguage);
        this.translationMode = TranslationMode.normalize(translationMode);
        this.translationEngine = TranslationEngine.normalize(translationEngine);
        this.textLayoutMode = TextLayoutMode.normalize(textLayoutMode);
    }

    public boolean canTranslate() {
        if (TranslationEngine.isLocal(translationEngine)) {
            return LocalTranslationLanguage.hasLanguageTag(targetLanguage);
        }
        return !normalizedBaseUrl.isEmpty() && !model.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
