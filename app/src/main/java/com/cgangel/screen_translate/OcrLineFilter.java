package com.cgangel.screen_translate;

import java.util.ArrayList;
import java.util.List;

public final class OcrLineFilter {
    private OcrLineFilter() {
    }

    public static List<OcrLine> excludeOverlayLines(
            List<OcrLine> lines,
            OcrLine exclusion,
            String buttonText
    ) {
        if (lines == null || lines.isEmpty() || exclusion == null || exclusion.area() <= 0) {
            return lines;
        }
        List<OcrLine> filtered = new ArrayList<>();
        for (OcrLine line : lines) {
            if (line == null) {
                continue;
            }
            if (isInsideOverlayExclusion(line, exclusion, buttonText)) {
                continue;
            }
            filtered.add(line);
        }
        return filtered;
    }

    private static boolean isInsideOverlayExclusion(
            OcrLine line,
            OcrLine exclusion,
            String buttonText
    ) {
        int intersectionLeft = Math.max(line.left, exclusion.left);
        int intersectionTop = Math.max(line.top, exclusion.top);
        int intersectionRight = Math.min(line.right, exclusion.right);
        int intersectionBottom = Math.min(line.bottom, exclusion.bottom);
        int intersection = Math.max(0, intersectionRight - intersectionLeft)
                * Math.max(0, intersectionBottom - intersectionTop);
        double lineOverlap = line.area() <= 0 ? 0.0 : (double) intersection / (double) line.area();
        if (lineOverlap >= 0.45) {
            return true;
        }
        return TextHash.normalizeText(line.text).equals(TextHash.normalizeText(buttonText))
                && line.intersectionOverUnion(exclusion) > 0.02;
    }
}
