package com.cgangel.screen_translate;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class JsonTranslationParserTest {
    @Test
    public void extractsChatContentAndTranslations() throws Exception {
        String response = "{"
                + "\"choices\":[{\"message\":{\"content\":\"{\\\"translations\\\":[{\\\"id\\\":\\\"line_1\\\",\\\"translation\\\":\\\"你好\\\"}]}\"}}]"
                + "}";

        String content = JsonTranslationParser.extractChatContent(response);
        List<TranslationResult> results = JsonTranslationParser.parseTranslations(content);

        assertEquals(1, results.size());
        assertEquals("line_1", results.get(0).id);
        assertEquals("你好", results.get(0).translation);
    }

    @Test
    public void stripsMarkdownFence() throws Exception {
        List<TranslationResult> results = JsonTranslationParser.parseTranslations(
                "```json\n{\"translations\":[{\"id\":\"line_2\",\"translation\":\"World\"}]}\n```"
        );

        assertEquals(1, results.size());
        assertEquals("line_2", results.get(0).id);
        assertEquals("World", results.get(0).translation);
    }
}
