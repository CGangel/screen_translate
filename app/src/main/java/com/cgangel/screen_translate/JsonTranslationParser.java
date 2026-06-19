package com.cgangel.screen_translate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class JsonTranslationParser {
    private JsonTranslationParser() {
    }

    public static String extractChatContent(String chatResponseJson) throws Exception {
        JSONObject root = new JSONObject(chatResponseJson);
        JSONArray choices = root.getJSONArray("choices");
        if (choices.length() == 0) {
            throw new IllegalArgumentException("No choices in response");
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        return message.getString("content");
    }

    public static List<TranslationResult> parseTranslations(String content) throws Exception {
        String json = stripMarkdownFence(content);
        JSONObject root = new JSONObject(json);
        JSONArray translations = root.getJSONArray("translations");
        List<TranslationResult> results = new ArrayList<>();
        for (int i = 0; i < translations.length(); i++) {
            JSONObject item = translations.getJSONObject(i);
            results.add(new TranslationResult(
                    item.optString("id", ""),
                    item.optString("translation", "")
            ));
        }
        return results;
    }

    static String stripMarkdownFence(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline >= 0 && lastFence > firstNewline) {
            return trimmed.substring(firstNewline + 1, lastFence).trim();
        }
        return trimmed;
    }
}
