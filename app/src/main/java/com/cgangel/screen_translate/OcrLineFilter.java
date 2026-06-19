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
        if (exclusion == null) {
            return lines;
        }
        List<OcrLine> exclusions = new ArrayList<>();
        exclusions.add(exclusion);
        return excludeOverlayLines(lines, exclusions, buttonText);
    }

    public static List<OcrLine> excludeOverlayLines(
            List<OcrLine> lines,
            List<OcrLine> exclusions,
            String buttonText
    ) {
        if (lines == null || lines.isEmpty() || exclusions == null || exclusions.isEmpty()) {
            return lines;
        }
        List<OcrLine> filtered = new ArrayList<>();
        for (OcrLine line : lines) {
            if (line == null) {
                continue;
            }
            if (isInsideAnyOverlayExclusion(line, exclusions, buttonText)) {
                continue;
            }
            filtered.add(line);
        }
        return filtered;
    }

    private static boolean isInsideAnyOverlayExclusion(
            OcrLine line,
            List<OcrLine> exclusions,
            String buttonText
    ) {
        if (exclusions == null || exclusions.isEmpty()) {
            return false;
        }
        for (OcrLine exclusion : exclusions) {
            if (exclusion != null
                    && exclusion.area() > 0
                    && isInsideOverlayExclusion(line, exclusion, buttonText)) {
                return true;
            }
        }
        return false;
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
