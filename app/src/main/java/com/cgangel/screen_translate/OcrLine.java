package com.cgangel.screen_translate;

public class OcrLine {
    public final String id;
    public final String text;
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public OcrLine(String id, String text, int left, int top, int right, int bottom) {
        this.id = id == null ? "" : id;
        this.text = text == null ? "" : text.trim();
        this.left = Math.min(left, right);
        this.top = Math.min(top, bottom);
        this.right = Math.max(left, right);
        this.bottom = Math.max(top, bottom);
    }

    public OcrLine withId(String newId) {
        return new OcrLine(newId, text, left, top, right, bottom);
    }

    public int width() {
        return Math.max(0, right - left);
    }

    public int height() {
        return Math.max(0, bottom - top);
    }

    public int area() {
        return width() * height();
    }

    public boolean hasText() {
        return !TextHash.normalizeText(text).isEmpty();
    }

    public double intersectionOverUnion(OcrLine other) {
        int intersectionLeft = Math.max(left, other.left);
        int intersectionTop = Math.max(top, other.top);
        int intersectionRight = Math.min(right, other.right);
        int intersectionBottom = Math.min(bottom, other.bottom);
        int intersectionWidth = Math.max(0, intersectionRight - intersectionLeft);
        int intersectionHeight = Math.max(0, intersectionBottom - intersectionTop);
        int intersection = intersectionWidth * intersectionHeight;
        int union = area() + other.area() - intersection;
        if (union <= 0) {
            return 0.0;
        }
        return (double) intersection / union;
    }
}
