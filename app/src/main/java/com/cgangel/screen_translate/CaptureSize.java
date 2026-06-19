package com.cgangel.screen_translate;

public class CaptureSize {
    public final int width;
    public final int height;

    public CaptureSize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public static CaptureSize fitWithin(int width, int height, int maxLongEdge) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int safeMaxLongEdge = Math.max(1, maxLongEdge);
        int longEdge = Math.max(safeWidth, safeHeight);
        if (longEdge <= safeMaxLongEdge) {
            return new CaptureSize(safeWidth, safeHeight);
        }
        float scale = (float) safeMaxLongEdge / (float) longEdge;
        return new CaptureSize(
                Math.max(1, Math.round(safeWidth * scale)),
                Math.max(1, Math.round(safeHeight * scale))
        );
    }
}
