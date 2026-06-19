package com.cgangel.screen_translate;

import android.content.Context;
import android.util.DisplayMetrics;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingTranslateWindow {
    private final Context context;
    private final WindowManager windowManager;
    private final int textResId;
    private final int backgroundColor;
    private final int yDp;
    private TextView buttonView;
    private WindowManager.LayoutParams lastParams;
    private boolean attached;

    public FloatingTranslateWindow(Context context) {
        this(context, R.string.overlay_button_translate, OverlayStyle.FLOATING_BUTTON_COLOR, 220);
    }

    public FloatingTranslateWindow(Context context, int textResId, int backgroundColor, int yDp) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        this.textResId = textResId;
        this.backgroundColor = backgroundColor;
        this.yDp = yDp;
    }

    public void show(View.OnClickListener listener) {
        if (attached && buttonView != null) {
            buttonView.setOnClickListener(listener);
            buttonView.setVisibility(View.VISIBLE);
            return;
        }
        buttonView = new TextView(context);
        buttonView.setText(textResId);
        buttonView.setTextColor(android.graphics.Color.WHITE);
        buttonView.setTextSize(22);
        buttonView.setGravity(Gravity.CENTER);
        buttonView.setBackground(OverlayStyle.oval(backgroundColor));
        buttonView.setOnClickListener(listener);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(56),
                dp(56),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(20);
        params.y = dp(yDp);
        windowManager.addView(buttonView, params);
        lastParams = params;
        attached = true;
    }

    public void hideForCapture() {
        if (buttonView != null) {
            buttonView.setVisibility(View.INVISIBLE);
        }
    }

    public void restore() {
        if (buttonView != null) {
            buttonView.setVisibility(View.VISIBLE);
        }
    }

    public void dismiss() {
        if (attached && buttonView != null) {
            try {
                windowManager.removeView(buttonView);
            } catch (Exception ignored) {
            }
        }
        attached = false;
        buttonView = null;
        lastParams = null;
    }

    public OcrLine captureExclusionLine(int sourceWidth, int sourceHeight) {
        if (!attached || buttonView == null || lastParams == null || sourceWidth <= 0 || sourceHeight <= 0) {
            return null;
        }
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null && windowManager.getDefaultDisplay() != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }
        int displayWidth = metrics.widthPixels > 0 ? metrics.widthPixels : sourceWidth;
        int displayHeight = metrics.heightPixels > 0 ? metrics.heightPixels : sourceHeight;
        int width = Math.max(1, lastParams.width);
        int height = Math.max(1, lastParams.height);
        int left = displayWidth - lastParams.x - width;
        int top = lastParams.y;
        int margin = dp(8);
        OcrLine displayLine = new OcrLine(
                "__floating_button__",
                "button",
                left - margin,
                top - margin,
                left + width + margin,
                top + height + margin
        );
        return CoordinateMapper.mapLine(
                displayLine,
                displayWidth,
                displayHeight,
                sourceWidth,
                sourceHeight
        );
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
