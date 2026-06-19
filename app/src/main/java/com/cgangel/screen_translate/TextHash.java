package com.cgangel.screen_translate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public final class TextHash {
    private TextHash() {
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    public static String hashLines(List<OcrLine> lines) {
        StringBuilder builder = new StringBuilder();
        if (lines != null) {
            for (OcrLine line : lines) {
                if (line != null && line.hasText()) {
                    builder.append(normalizeText(line.text)).append('\n');
                }
            }
        }
        return sha256(builder.toString());
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate hash", e);
        }
    }
}
