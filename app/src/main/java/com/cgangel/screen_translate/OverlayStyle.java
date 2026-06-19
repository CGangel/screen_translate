package com.cgangel.screen_translate;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

public final class OverlayStyle {
    public static final int FLOATING_BUTTON_COLOR = Color.rgb(56, 189, 248);
    public static final int TRANSLATION_BUBBLE_COLOR = Color.rgb(255, 173, 205);
    public static final int STATUS_BUBBLE_COLOR = Color.argb(230, 17, 24, 39);

    private OverlayStyle() {
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    public static GradientDrawable roundedRect(int color, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }
}
