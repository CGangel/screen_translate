package com.cgangel.screen_translate;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverlayWindow {
    private static final int MAX_TRANSLATION_WINDOWS = 60;

    private final Context context;
    private final WindowManager windowManager;
    private final List<View> translationViews = new ArrayList<>();
    private TextView statusView;
    private boolean hasTranslations;

    public OverlayWindow(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void hideForCapture() {
        setVisible(false);
    }

    public void restore() {
        setVisible(true);
    }

    public void showStatus(String message) {
        clearTranslations();
        removeStatusView();
        hasTranslations = false;
        statusView = createStatusBubble(message);
        addView(statusView, statusParams());
    }

    public void showTransientStatus(String message) {
        if (!hasTranslations) {
            showStatus(message);
            return;
        }
        removeStatusView();
        statusView = createStatusBubble(message);
        addView(statusView, statusParams());
    }

    public boolean hasTranslations() {
        return hasTranslations;
    }

    public void clear() {
        clearTranslations();
        removeStatusView();
        hasTranslations = false;
    }

    public void showTranslations(
            List<OcrLine> lines,
            List<TranslationResult> translations,
            int sourceWidth,
            int sourceHeight
    ) {
        clearTranslations();
        removeStatusView();
        Map<String, String> translationById = new HashMap<>();
        if (translations != null) {
            for (TranslationResult result : translations) {
                if (result != null && !result.id.isEmpty() && !result.translation.isEmpty()) {
                    translationById.put(result.id, result.translation);
                }
            }
        }

        int[] displaySize = getDisplaySize(sourceWidth, sourceHeight);
        int displayWidth = displaySize[0];
        int displayHeight = displaySize[1];
        int added = 0;
        if (lines != null) {
            for (OcrLine line : lines) {
                if (added >= MAX_TRANSLATION_WINDOWS) {
                    break;
                }
                String translated = translationById.get(line.id);
                if (translated == null || translated.trim().isEmpty()) {
                    continue;
                }
                OcrLine mappedLine = CoordinateMapper.mapLine(
                        line,
                        sourceWidth,
                        sourceHeight,
                        displayWidth,
                        displayHeight
                );
                TextView textView = createTranslationBubble(translated, mappedLine);
                addView(textView, translationParams(mappedLine, displayWidth, displayHeight));
                translationViews.add(textView);
                added++;
            }
        }
        if (added == 0) {
            showStatus(context.getString(R.string.service_no_translated_lines));
            return;
        }
        hasTranslations = true;
    }

    public void dismiss() {
        clearTranslations();
        removeStatusView();
        hasTranslations = false;
    }

    private WindowManager.LayoutParams translationParams(OcrLine line, int displayWidth, int displayHeight) {
        int left = clamp(line.left, 0, Math.max(0, displayWidth - dp(24)));
        int top = clamp(line.top, 0, Math.max(0, displayHeight - dp(20)));
        int width = clamp(line.width() + dp(8), dp(40), Math.max(dp(40), displayWidth - left));
        WindowManager.LayoutParams params = baseParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = left;
        params.y = top;
        return params;
    }

    private WindowManager.LayoutParams statusParams() {
        WindowManager.LayoutParams params = baseParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(42);
        return params;
    }

    private WindowManager.LayoutParams baseParams(int width, int height) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return params;
    }

    private TextView createStatusBubble(String message) {
        TextView textView = new TextView(context);
        textView.setText(message);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(13);
        textView.setPadding(dp(8), dp(5), dp(8), dp(5));
        textView.setBackground(OverlayStyle.roundedRect(OverlayStyle.STATUS_BUBBLE_COLOR, dp(10)));
        return textView;
    }

    private TextView createTranslationBubble(String message, OcrLine line) {
        TextView textView = new TextView(context);
        textView.setText(message);
        textView.setTextColor(Color.rgb(17, 24, 39));
        textView.setTextSize(textSizeForLine(line));
        textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        textView.setIncludeFontPadding(false);
        textView.setPadding(dp(3), 0, dp(3), 0);
        textView.setMinHeight(Math.max(dp(16), line.height()));
        textView.setBackground(OverlayStyle.roundedRect(OverlayStyle.TRANSLATION_BUBBLE_COLOR, dp(8)));
        return textView;
    }

    private void clearTranslations() {
        for (View view : translationViews) {
            removeView(view);
        }
        translationViews.clear();
        hasTranslations = false;
    }

    private void removeStatusView() {
        if (statusView != null) {
            removeView(statusView);
            statusView = null;
        }
    }

    private void addView(View view, WindowManager.LayoutParams params) {
        try {
            windowManager.addView(view, params);
        } catch (Exception ignored) {
        }
    }

    private void removeView(View view) {
        try {
            windowManager.removeView(view);
        } catch (Exception ignored) {
        }
    }

    private void setVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.INVISIBLE;
        for (View view : translationViews) {
            view.setVisibility(visibility);
        }
        if (statusView != null) {
            statusView.setVisibility(visibility);
        }
    }

    private int[] getDisplaySize(int fallbackWidth, int fallbackHeight) {
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null && windowManager.getDefaultDisplay() != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }
        int width = metrics.widthPixels > 0 ? metrics.widthPixels : fallbackWidth;
        int height = metrics.heightPixels > 0 ? metrics.heightPixels : fallbackHeight;
        return new int[]{Math.max(1, width), Math.max(1, height)};
    }

    private float textSizeForLine(OcrLine line) {
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        int lineHeight = Math.max(dp(14), line.height());
        float px = Math.max(dp(10), Math.min(dp(22), lineHeight * 0.82f));
        return px / scaledDensity;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
