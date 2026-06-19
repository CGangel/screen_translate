package com.cgangel.screen_translate;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TextHashAndCacheTest {
    @Test
    public void hashNormalizesWhitespace() {
        String a = TextHash.hashLines(Collections.singletonList(
                new OcrLine("line_1", "Hello   world", 0, 0, 1, 1)
        ));
        String b = TextHash.hashLines(Collections.singletonList(
                new OcrLine("line_1", "Hello world", 0, 0, 1, 1)
        ));

        assertEquals(a, b);
    }

    @Test
    public void cacheKeyChangesWithModelAndTargetLanguage() {
        ApiSettings first = new ApiSettings(
                "https://example.com",
                "model-a",
                "",
                "\u7b80\u4f53\u4e2d\u6587"
        );
        ApiSettings second = new ApiSettings(
                "https://example.com",
                "model-b",
                "",
                "\u7b80\u4f53\u4e2d\u6587"
        );

        assertNotEquals(
                TranslationCache.key(first, "hash"),
                TranslationCache.key(second, "hash")
        );
    }

    @Test
    public void cacheKeyChangesWithTranslationEngine() {
        ApiSettings api = new ApiSettings(
                "https://example.com",
                "model-a",
                "",
                SourceLanguage.AUTO_LABEL,
                "\u7b80\u4f53\u4e2d\u6587",
                TranslationMode.REALTIME,
                TranslationEngine.API
        );
        ApiSettings local = new ApiSettings(
                "",
                "",
                "",
                SourceLanguage.AUTO_LABEL,
                "\u7b80\u4f53\u4e2d\u6587",
                TranslationMode.REALTIME,
                TranslationEngine.LOCAL
        );

        assertNotEquals(
                TranslationCache.key(api, "hash"),
                TranslationCache.key(local, "hash")
        );
    }

    @Test
    public void cacheKeyChangesWithSourceLanguage() {
        ApiSettings english = new ApiSettings(
                "https://example.com",
                "model-a",
                "",
                "English",
                "\u7b80\u4f53\u4e2d\u6587",
                TranslationMode.REALTIME,
                TranslationEngine.API
        );
        ApiSettings japanese = new ApiSettings(
                "https://example.com",
                "model-a",
                "",
                "\u65e5\u672c\u8a9e",
                "\u7b80\u4f53\u4e2d\u6587",
                TranslationMode.REALTIME,
                TranslationEngine.API
        );

        assertNotEquals(
                TranslationCache.key(english, "hash"),
                TranslationCache.key(japanese, "hash")
        );
    }

    @Test
    public void cacheReturnsStoredTranslation() {
        TranslationCache cache = new TranslationCache(2);
        cache.put("k", Arrays.asList(new TranslationResult("line_1", "Hi")));

        assertNotNull(cache.get("k"));
        assertEquals("Hi", cache.get("k").get(0).translation);
    }

    @Test
    public void authorizationHeaderIsOptional() {
        assertNull(OpenAiCompatClient.authorizationHeader(""));
        assertNull(OpenAiCompatClient.authorizationHeader("  "));
        assertEquals("Bearer sk-test", OpenAiCompatClient.authorizationHeader(" sk-test "));
    }

    @Test
    public void translationModeDefaultsToRealtimeAndAcceptsClickMode() {
        ApiSettings defaults = new ApiSettings(
                "https://example.com",
                "model-a",
                "",
                "\u7b80\u4f53\u4e2d\u6587"
        );
        ApiSettings click = new ApiSettings(
                "https://example.com",
                "model-a",
                "",
                "\u7b80\u4f53\u4e2d\u6587",
                TranslationMode.CLICK
        );

        assertEquals(TranslationMode.REALTIME, defaults.translationMode);
        assertEquals(TranslationMode.CLICK, click.translationMode);
        assertEquals(TranslationMode.REALTIME, TranslationMode.normalize("unknown"));
    }

    @Test
    public void localTranslationCanStartWithoutApiConfig() {
        ApiSettings local = new ApiSettings(
                "",
                "",
                "",
                SourceLanguage.AUTO_LABEL,
                "\u7b80\u4f53\u4e2d\u6587",
                TranslationMode.REALTIME,
                TranslationEngine.LOCAL
        );
        ApiSettings api = new ApiSettings(
                "",
                "",
                "",
                SourceLanguage.AUTO_LABEL,
                "\u7b80\u4f53\u4e2d\u6587",
                TranslationMode.REALTIME,
                TranslationEngine.API
        );

        assertEquals(TranslationEngine.LOCAL, local.translationEngine);
        assertEquals(TranslationEngine.API, TranslationEngine.normalize("unknown"));
        assertTrue(local.canTranslate());
        assertFalse(api.canTranslate());
    }

    @Test
    public void localLanguageMapperAcceptsPresetsAndTags() {
        assertEquals("zh", LocalTranslationLanguage.resolveLanguageTag("\u7b80\u4f53\u4e2d\u6587"));
        assertEquals("ja", LocalTranslationLanguage.resolveLanguageTag("\u65e5\u672c\u8a9e"));
        assertEquals("ko", LocalTranslationLanguage.resolveLanguageTag("\ud55c\uad6d\uc5b4"));
        assertEquals("en", LocalTranslationLanguage.resolveLanguageTag("en-US"));
        assertEquals("", LocalTranslationLanguage.resolveLanguageTag("\u672a\u77e5\u8bed\u8a00"));
    }

    @Test
    public void sourceLanguageMapperSupportsAutoEnglishAndJapanese() {
        assertTrue(SourceLanguage.isAuto("\u81ea\u52a8\u8bc6\u522b"));
        assertEquals("en", SourceLanguage.resolveLanguageTag("English"));
        assertEquals("ja", SourceLanguage.resolveLanguageTag("\u65e5\u672c\u8a9e"));
        assertEquals("ja", SourceLanguage.resolveLanguageTag("ja-JP"));
        assertEquals("", SourceLanguage.resolveLanguageTag("\u672a\u77e5\u8bed\u8a00"));
    }
}
