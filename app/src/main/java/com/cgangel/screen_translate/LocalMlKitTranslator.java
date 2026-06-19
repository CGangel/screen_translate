package com.cgangel.screen_translate;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class LocalMlKitTranslator {
    private static final String UNDETERMINED_LANGUAGE = "und";
    private static final int LANGUAGE_ID_TIMEOUT_SECONDS = 5;
    private static final int MODEL_DOWNLOAD_TIMEOUT_SECONDS = 90;
    private static final int TRANSLATE_TIMEOUT_SECONDS = 20;

    private final LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
    private final Map<String, Translator> translators = new HashMap<>();
    private final Set<String> downloadedModels = new HashSet<>();

    public List<TranslationResult> translate(
            String sourceLanguage,
            String targetLanguage,
            List<OcrLine> lines
    ) throws Exception {
        String sourceTag = SourceLanguage.resolveLanguageTag(sourceLanguage);
        String configuredSource = sourceTag.isEmpty() ? null : TranslateLanguage.fromLanguageTag(sourceTag);
        if (!sourceTag.isEmpty() && configuredSource == null) {
            throw new IllegalArgumentException("\u672c\u5730\u673a\u7ffb\u6682\u4e0d\u652f\u6301\u8be5\u539f\u8bed\u8a00");
        }

        String targetTag = LocalTranslationLanguage.resolveLanguageTag(targetLanguage);
        String target = targetTag.isEmpty() ? null : TranslateLanguage.fromLanguageTag(targetTag);
        if (target == null) {
            throw new IllegalArgumentException("\u672c\u5730\u673a\u7ffb\u6682\u4e0d\u652f\u6301\u8be5\u76ee\u6807\u8bed\u8a00");
        }

        List<TranslationResult> results = new ArrayList<>();
        if (lines == null) {
            return results;
        }
        for (OcrLine line : lines) {
            if (line == null || line.text.trim().isEmpty()) {
                continue;
            }
            String source = configuredSource != null
                    ? configuredSource
                    : identifySourceLanguage(line.text);
            if (source == null || source.equals(target)) {
                results.add(new TranslationResult(line.id, line.text));
                continue;
            }
            Translator translator = translatorFor(source, target);
            ensureModelDownloaded(translator, source, target);
            String translated = Tasks.await(
                    translator.translate(line.text),
                    TRANSLATE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            results.add(new TranslationResult(line.id, translated));
        }
        return results;
    }

    public void close() {
        try {
            languageIdentifier.close();
        } catch (Exception ignored) {
        }
        for (Translator translator : translators.values()) {
            try {
                translator.close();
            } catch (Exception ignored) {
            }
        }
        translators.clear();
        downloadedModels.clear();
    }

    private String identifySourceLanguage(String text) {
        try {
            String detectedTag = Tasks.await(
                    languageIdentifier.identifyLanguage(text),
                    LANGUAGE_ID_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            if (detectedTag == null || UNDETERMINED_LANGUAGE.equals(detectedTag)) {
                return null;
            }
            String resolvedTag = SourceLanguage.resolveLanguageTag(detectedTag);
            if (resolvedTag.isEmpty()) {
                return null;
            }
            return TranslateLanguage.fromLanguageTag(resolvedTag);
        } catch (Exception e) {
            return null;
        }
    }

    private Translator translatorFor(String source, String target) {
        String key = source + ">" + target;
        Translator translator = translators.get(key);
        if (translator != null) {
            return translator;
        }
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build();
        Translator created = Translation.getClient(options);
        translators.put(key, created);
        return created;
    }

    private void ensureModelDownloaded(Translator translator, String source, String target) throws Exception {
        String key = source + ">" + target;
        if (downloadedModels.contains(key)) {
            return;
        }
        DownloadConditions conditions = new DownloadConditions.Builder().build();
        Tasks.await(
                translator.downloadModelIfNeeded(conditions),
                MODEL_DOWNLOAD_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
        downloadedModels.add(key);
    }
}
