package com.cgangel.screen_translate;

import android.graphics.Bitmap;
import android.graphics.Color;

public final class ImageFingerprint {
    private ImageFingerprint() {
    }

    public static String hash(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return "";
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return "";
        }
        long hash = 1469598103934665603L;
        int samplesX = 24;
        int samplesY = 24;
        for (int y = 0; y < samplesY; y++) {
            int py = Math.min(height - 1, Math.round((height - 1) * (y / (float) Math.max(1, samplesY - 1))));
            for (int x = 0; x < samplesX; x++) {
                int px = Math.min(width - 1, Math.round((width - 1) * (x / (float) Math.max(1, samplesX - 1))));
                int color = bitmap.getPixel(px, py);
                int gray = (Color.red(color) * 30 + Color.green(color) * 59 + Color.blue(color) * 11) / 100;
                int bucket = gray / 16;
                hash ^= bucket;
                hash *= 1099511628211L;
            }
        }
        return Long.toHexString(hash);
    }
}
