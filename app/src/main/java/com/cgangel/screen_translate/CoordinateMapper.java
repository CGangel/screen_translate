package com.cgangel.screen_translate;

public final class CoordinateMapper {
    private CoordinateMapper() {
    }

    public static OcrLine mapLine(
            OcrLine line,
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight
    ) {
        if (line == null || sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return line;
        }
        float scaleX = (float) targetWidth / (float) sourceWidth;
        float scaleY = (float) targetHeight / (float) sourceHeight;
        return new OcrLine(
                line.id,
                line.text,
                Math.round(line.left * scaleX),
                Math.round(line.top * scaleY),
                Math.round(line.right * scaleX),
                Math.round(line.bottom * scaleY)
        );
    }

    public static OcrLine scaleLine(OcrLine line, float scaleX, float scaleY) {
        if (line == null) {
            return null;
        }
        return new OcrLine(
                line.id,
                line.text,
                Math.round(line.left * scaleX),
                Math.round(line.top * scaleY),
                Math.round(line.right * scaleX),
                Math.round(line.bottom * scaleY)
        );
    }
}
