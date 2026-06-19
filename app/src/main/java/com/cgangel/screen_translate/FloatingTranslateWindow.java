package com.cgangel.screen_translate;

import android.content.Context;
import android.util.DisplayMetrics;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingTranslateWindow {
    private static final int BUTTON_SIZE_DP = 48;

    private final Context context;
    private final WindowManager windowManager;
    private final int textResId;
    private final int backgroundColor;
    private final int yDp;
    private TextView buttonView;
    private WindowManager.LayoutParams lastParams;
    private boolean attached;
    private float downRawX;
    private float downRawY;
    private int downX;
    private int downY;
    private boolean dragging;

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
        buttonView.setTextSize(20);
        buttonView.setGravity(Gravity.CENTER);
        buttonView.setBackground(OverlayStyle.oval(backgroundColor));
        buttonView.setOnClickListener(listener);
        buttonView.setOnTouchListener(this::handleTouch);

        int buttonSize = dp(BUTTON_SIZE_DP);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                buttonSize,
                buttonSize,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics metrics = displayMetrics();
        int displayWidth = metrics.widthPixels > 0 ? metrics.widthPixels : buttonSize + dp(16);
        int displayHeight = metrics.heightPixels > 0 ? metrics.heightPixels : buttonSize + dp(yDp);
        params.x = clamp(displayWidth - buttonSize - dp(16), 0, Math.max(0, displayWidth - buttonSize));
        params.y = clamp(dp(yDp), 0, Math.max(0, displayHeight - buttonSize));
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
            if (attached && lastParams != null) {
                updatePosition(lastParams.x, lastParams.y);
            }
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
        DisplayMetrics metrics = displayMetrics();
        int displayWidth = metrics.widthPixels > 0 ? metrics.widthPixels : sourceWidth;
        int displayHeight = metrics.heightPixels > 0 ? metrics.heightPixels : sourceHeight;
        int width = Math.max(1, lastParams.width);
        int height = Math.max(1, lastParams.height);
        int left = lastParams.x;
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

    private boolean handleTouch(View view, MotionEvent event) {
        if (lastParams == null || windowManager == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = lastParams.x;
                downY = lastParams.y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getRawX() - downRawX;
                float deltaY = event.getRawY() - downRawY;
                if (!dragging && distance(deltaX, deltaY) > ViewConfiguration.get(context).getScaledTouchSlop()) {
                    dragging = true;
                }
                if (dragging) {
                    updatePosition(Math.round(downX + deltaX), Math.round(downY + deltaY));
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    view.performClick();
                }
                dragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return false;
        }
    }

    private void updatePosition(int x, int y) {
        DisplayMetrics metrics = displayMetrics();
        int displayWidth = metrics.widthPixels > 0 ? metrics.widthPixels : x + Math.max(1, lastParams.width);
        int displayHeight = metrics.heightPixels > 0 ? metrics.heightPixels : y + Math.max(1, lastParams.height);
        int maxX = Math.max(0, displayWidth - Math.max(1, lastParams.width));
        int maxY = Math.max(0, displayHeight - Math.max(1, lastParams.height));
        lastParams.x = clamp(x, 0, maxX);
        lastParams.y = clamp(y, 0, maxY);
        try {
            windowManager.updateViewLayout(buttonView, lastParams);
        } catch (Exception ignored) {
        }
    }

    private DisplayMetrics displayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null && windowManager.getDefaultDisplay() != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }
        return metrics;
    }

    private float distance(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
