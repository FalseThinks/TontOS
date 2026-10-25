package org.tontos.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/**
 * Centralized tactile UI factory and monospace button utilities.
 * Enforces uniform bracketed syntax [ ... ], AMOLED touch feedback, and StateListDrawables.
 */
public final class TactileUi {

    public static final int MIN_TOUCH_TARGET_HEIGHT_DP = 48;
    public static final int MIN_TOUCH_TARGET_WIDTH_DP = 44;

    private TactileUi() {}

    public static int dpToPx(Context context, int dp) {
        if (context == null) return dp;
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int dpToPx(float density, int dp) {
        return (int) (dp * density + 0.5f);
    }

    public static String ensureBrackets(String val) {
        if (val == null) return "";
        val = val.trim();
        while (val.startsWith("[") && val.endsWith("]")) {
            val = val.substring(1, val.length() - 1).trim();
        }
        return "[ " + val + " ]";
    }

    public static String ensureBrackets(String text, String fallback) {
        String val = (text != null && !text.trim().isEmpty()) ? text.trim() : fallback;
        return ensureBrackets(val);
    }

    public static Drawable createTouchFeedbackDrawable(Context context, ThemeTokens tokens) {
        int radius = dpToPx(context, 4);
        StateListDrawable states = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(tokens.controlBgPressed);
        pressed.setCornerRadius(radius);

        ColorDrawable normal = new ColorDrawable(Color.TRANSPARENT);
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    public static StateListDrawable createButtonBg(int pressedColor, int strokeColor, float density) {
        int strokeWidth = Math.max(1, (int) (1 * density + 0.5f));
        int cornerRadius = (int) (4 * density + 0.5f);
        StateListDrawable sld = new StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(pressedColor);
        pressed.setStroke(strokeWidth, strokeColor);
        pressed.setCornerRadius(cornerRadius);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);
        normal.setStroke(strokeWidth, strokeColor);
        normal.setCornerRadius(cornerRadius);

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    public static StateListDrawable createButtonDrawable(int normalBg, int pressedBg, int strokeColor, int strokeWidth, int cornerRadius) {
        StateListDrawable sld = new StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(pressedBg);
        pressed.setStroke(strokeWidth, strokeColor);
        pressed.setCornerRadius(cornerRadius);

        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(normalBg);
        normal.setStroke(strokeWidth, strokeColor);
        normal.setCornerRadius(cornerRadius);

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    public static StateListDrawable createSelectionRowBg(ThemeTokens tokens, boolean isActive, float density) {
        StateListDrawable sld = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(tokens.controlBgPressed);
        pressed.setCornerRadius((int) (4 * density));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(isActive ? tokens.activeSelectionBg : Color.TRANSPARENT);
        normal.setCornerRadius((int) (4 * density));
        if (isActive) {
            normal.setStroke((int) (1 * density), tokens.accent);
        }

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }
}
