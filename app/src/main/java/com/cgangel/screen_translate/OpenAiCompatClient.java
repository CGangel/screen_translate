package com.cgangel.screen_translate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class OpenAiCompatClient {
    private static final int TIMEOUT_MS = 45000;

    public List<TranslationResult> translate(
            ApiSettings settings,
            List<OcrLine> lines
    ) throws Exception {
        String firstContent = request(settings, lines, false);
        try {
            return JsonTranslationParser.parseTranslations(firstContent);
        } catch (Exception parseError) {
            String retryContent = request(settings, lines, true);
            try {
                return JsonTranslationParser.parseTranslations(retryContent);
            } catch (Exception retryError) {
                throw new IOException("Translation JSON parse failed: " + shorten(retryContent), retryError);
            }
        }
    }

    private String request(ApiSettings settings, List<OcrLine> lines, boolean strictRetry)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", settings.model);
        body.put("temperature", 0);
        body.put("messages", buildMessages(settings, lines, strictRetry));

        HttpURLConnection connection = (HttpURLConnection) new URL(
                ApiUrlNormalizer.chatCompletionsUrl(settings.normalizedBaseUrl)
        ).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        String authorization = authorizationHeader(settings.apiKey);
        if (authorization != null) {
            connection.setRequestProperty("Authorization", authorization);
        }

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload);
        }

        int responseCode = connection.getResponseCode();
        String responseBody = readResponse(
                responseCode >= 200 && responseCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream()
        );
        connection.disconnect();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("HTTP " + responseCode + ": " + shorten(responseBody));
        }
        return JsonTranslationParser.extractChatContent(responseBody);
    }

    private JSONArray buildMessages(ApiSettings settings, List<OcrLine> lines, boolean strictRetry)
            throws Exception {
        JSONArray messages = new JSONArray();
        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", strictRetry
                ? "Return only valid JSON. Do not use markdown. Translate each line exactly once."
                : "You translate OCR text lines from a phone screen. Return only JSON.");
        messages.put(system);

        JSONArray lineItems = new JSONArray();
        for (OcrLine line : lines) {
            if (line != null && line.hasText()) {
                JSONObject item = new JSONObject();
                item.put("id", line.id);
                item.put("text", line.text);
                lineItems.put(item);
            }
        }

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content",
                "Source language: " + SourceLanguage.promptLabel(settings.sourceLanguage) + "\n"
                        + "Target language: " + settings.targetLanguage + "\n"
                        + "Translate every line from the source language to the target language. "
                        + "If source language is Auto-detect, infer it from each line. "
                        + "Preserve ids. "
                        + "Return JSON in this exact shape: "
                        + "{\"translations\":[{\"id\":\"line_1\",\"translation\":\"...\"}]}.\n"
                        + "Lines JSON: " + lineItems);
        messages.put(user);
        return messages;
    }

    private String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    static String authorizationHeader(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }
        return "Bearer " + apiKey.trim();
    }

    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 240) {
            return compact;
        }
        return compact.substring(0, 240) + "...";
    }
}
