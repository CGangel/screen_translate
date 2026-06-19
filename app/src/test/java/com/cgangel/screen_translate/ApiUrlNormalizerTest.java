package com.cgangel.screen_translate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ApiUrlNormalizerTest {
    @Test
    public void normalizeAddsV1WhenMissing() {
        assertEquals("https://example.com/v1", ApiUrlNormalizer.normalize("https://example.com"));
        assertEquals(
                "https://example.com/v1/chat/completions",
                ApiUrlNormalizer.chatCompletionsUrl("https://example.com")
        );
    }

    @Test
    public void normalizeDoesNotDuplicateV1() {
        assertEquals("https://example.com/v1", ApiUrlNormalizer.normalize("https://example.com/v1"));
        assertEquals("https://example.com/v1", ApiUrlNormalizer.normalize("https://example.com/v1/"));
    }

    @Test
    public void normalizeHandlesBlankInput() {
        assertEquals("", ApiUrlNormalizer.normalize("  "));
        assertEquals("", ApiUrlNormalizer.chatCompletionsUrl(null));
    }
}
