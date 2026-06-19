package com.cgangel.screen_translate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OcrLineMerger {
    private OcrLineMerger() {
    }

    public static List<OcrLine> mergeAndSort(List<OcrLine> rawLines) {
        List<OcrLine> merged = new ArrayList<>();
        if (rawLines == null) {
            return merged;
        }
        for (OcrLine candidate : rawLines) {
            if (candidate == null || !candidate.hasText()) {
                continue;
            }
            int duplicateIndex = findDuplicateIndex(merged, candidate);
            if (duplicateIndex >= 0) {
                OcrLine existing = merged.get(duplicateIndex);
                if (shouldReplaceDuplicate(existing, candidate)) {
                    merged.set(duplicateIndex, candidate);
                }
            } else {
                merged.add(candidate);
            }
        }
        merged.sort(Comparator
                .comparingInt((OcrLine line) -> line.top / Math.max(1, line.height() + 8))
                .thenComparingInt(line -> line.top)
                .thenComparingInt(line -> line.left));

        List<OcrLine> withIds = new ArrayList<>();
        for (int i = 0; i < merged.size(); i++) {
            withIds.add(merged.get(i).withId("line_" + (i + 1)));
        }
        return withIds;
    }

    private static int findDuplicateIndex(List<OcrLine> lines, OcrLine candidate) {
        String candidateText = TextHash.normalizeText(candidate.text);
        for (int i = 0; i < lines.size(); i++) {
            OcrLine existing = lines.get(i);
            String existingText = TextHash.normalizeText(existing.text);
            if (candidate.intersectionOverUnion(existing) >= 0.55) {
                return i;
            }
            boolean sameText = !candidateText.isEmpty() && candidateText.equals(existingText);
            boolean closeVertical = Math.abs(candidate.top - existing.top)
                    <= Math.max(candidate.height(), existing.height());
            if (sameText && closeVertical) {
                return i;
            }
        }
        return -1;
    }

    private static boolean shouldReplaceDuplicate(OcrLine existing, OcrLine candidate) {
        String existingText = TextHash.normalizeText(existing.text);
        String candidateText = TextHash.normalizeText(candidate.text);
        int existingPriority = scriptPriority(existingText);
        int candidatePriority = scriptPriority(candidateText);
        if (candidatePriority > existingPriority) {
            int minLength = Math.min(2, Math.max(1, existingText.length() / 3));
            return candidateText.length() >= minLength;
        }
        if (candidatePriority < existingPriority) {
            return false;
        }

        int existingScriptChars = strongScriptCharCount(existingText);
        int candidateScriptChars = strongScriptCharCount(candidateText);
        if (candidateScriptChars != existingScriptChars) {
            return candidateScriptChars > existingScriptChars;
        }
        return candidateText.length() > existingText.length();
    }

    private static int scriptPriority(String text) {
        boolean hasKana = false;
        boolean hasCjkOrHangul = false;
        boolean hasLatinOrDigit = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA) {
                hasKana = true;
            } else if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HANGUL) {
                hasCjkOrHangul = true;
            } else if (script == Character.UnicodeScript.LATIN
                    || Character.isDigit(codePoint)) {
                hasLatinOrDigit = true;
            }
            offset += Character.charCount(codePoint);
        }
        if (hasKana) {
            return 3;
        }
        if (hasCjkOrHangul) {
            return 2;
        }
        if (hasLatinOrDigit) {
            return 1;
        }
        return 0;
    }

    private static int strongScriptCharCount(String text) {
        int count = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HANGUL) {
                count++;
            }
            offset += Character.charCount(codePoint);
        }
        return count;
    }
}
