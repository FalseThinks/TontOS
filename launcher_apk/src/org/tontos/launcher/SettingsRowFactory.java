package org.tontos.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * UI row factory and styling utility for TontOS Settings.
 */
public final class SettingsRowFactory {

    public static final int MIN_TOUCH_TARGET_HEIGHT_DP = 48;
    public static final int MIN_TOUCH_TARGET_WIDTH_DP = 44;
    public static final int BUTTON_MARGIN_DP = 4;

    private SettingsRowFactory() {}

    public static float getFontScale(Context context) {
        // config.fontScale is already applied to DisplayMetrics.scaledDensity in attachBaseContext.
        // Returning 1.0f prevents compound quadratic scaling (fontScale^2).
        return 1.0f;
    }

    public static String ensureBrackets(String val) {
        return TactileUi.ensureBrackets(val);
    }

    public static void addBackHeader(
        Context context,
        LinearLayout container,
        ThemeTokens tokens,
        String langCode,
        View.OnClickListener onBack
    ) {
        float scale = getFontScale(context);
        TextView backBtn = new TextView(context);
        backBtn.setText(ensureBrackets(I18n.get(I18n.KEY_BACK_TO_SETTINGS, langCode)));
        backBtn.setTextColor(tokens.navAccent);
        backBtn.setTextSize(14 * scale);
        backBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        int minTargetPx = dpToPx(context, MIN_TOUCH_TARGET_HEIGHT_DP);
        backBtn.setMinHeight(minTargetPx);
        backBtn.setMinimumHeight(minTargetPx);
        backBtn.setGravity(Gravity.CENTER_VERTICAL);
        backBtn.setBackground(createTouchFeedbackDrawable(context, tokens));
        backBtn.setPadding(0, dpToPx(context, 8), 0, dpToPx(context, 8));
        backBtn.setOnClickListener(onBack);
        container.addView(backBtn);
    }

    public static void addCategoryCard(
        Context context,
        LinearLayout container,
        String title,
        String subtitle,
        View.OnClickListener listener,
        ThemeTokens tokens
    ) {
        float scale = getFontScale(context);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, dpToPx(context, 16), 0, dpToPx(context, 16));
        card.setClickable(true);
        card.setFocusable(true);
        card.setBackground(createTouchFeedbackDrawable(context, tokens));
        card.setOnClickListener(listener);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView t = new TextView(context);
        t.setText(title);
        t.setTextColor(tokens.textPrimary);
        t.setTextSize(15 * scale);
        t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        t.setLayoutParams(lp);
        row.addView(t);

        TextView arrow = new TextView(context);
        arrow.setText("-->");
        arrow.setTextColor(tokens.navAccent);
        arrow.setTextSize(15 * scale);
        arrow.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        row.addView(arrow);

        card.addView(row);

        TextView s = new TextView(context);
        s.setText(subtitle);
        s.setTextColor(tokens.textMuted);
        s.setTextSize(12 * scale);
        s.setTypeface(Typeface.MONOSPACE);
        s.setPadding(0, dpToPx(context, 4), 0, 0);
        card.addView(s);

        container.addView(card);
    }

    public static void addSliderRow(
        Context context,
        LinearLayout container,
        final String label,
        int currentVal,
        final int min,
        final int max,
        final SettingsActivity.OnSliderChangedListener listener,
        ThemeTokens tokens
    ) {
        float scale = getFontScale(context);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dpToPx(context, 12), 0, dpToPx(context, 12));

        LinearLayout labelContainer = new LinearLayout(context);
        labelContainer.setOrientation(LinearLayout.HORIZONTAL);
        labelContainer.setGravity(Gravity.CENTER_VERTICAL);

        final TextView labelView = new TextView(context);
        labelView.setText(label + ": " + currentVal + "%");
        labelView.setTextColor(tokens.textPrimary);
        labelView.setTextSize(14 * scale);
        labelView.setTypeface(Typeface.MONOSPACE);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        labelView.setLayoutParams(labelLp);
        labelContainer.addView(labelView);

        View helpBadge = SettingsDescriptions.createHelpBadge(context, tokens, label, label);
        if (helpBadge != null) {
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.setMargins(dpToPx(context, 4), 0, 0, 0);
            helpBadge.setLayoutParams(badgeLp);
            labelContainer.addView(helpBadge);
        }
        row.addView(labelContainer);

        SeekBar seekBar = new SeekBar(context);
        styleMinimalSeekBar(context, seekBar, tokens);
        seekBar.setMax(max - min);
        seekBar.setProgress(Math.max(0, currentVal - min));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int actualVal = min + progress;
                labelView.setText(label + ": " + actualVal + "%");
                if (listener != null) {
                    listener.onProgress(actualVal, fromUser);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                int actualVal = min + sb.getProgress();
                if (listener != null) {
                    listener.onStop(actualVal);
                }
            }
        });
        row.addView(seekBar);

        container.addView(row);
    }

    private static String formatBracketText(String raw) {
        if (raw == null) return "[  ]";
        String clean = raw.trim();
        while (clean.startsWith("[") && clean.endsWith("]")) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        return "[ " + clean + " ]";
    }

    public static void addToggleRow(
        Context context,
        LinearLayout container,
        String label,
        String actionText,
        boolean isPositive,
        View.OnClickListener listener,
        ThemeTokens tokens
    ) {
        float density = context.getResources().getDisplayMetrics().density;
        float scale = getFontScale(context);
        int minTargetPx = (int) (MIN_TOUCH_TARGET_HEIGHT_DP * density + 0.5f);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpToPx(context, 10), 0, dpToPx(context, 10));

        LinearLayout labelContainer = new LinearLayout(context);
        labelContainer.setOrientation(LinearLayout.HORIZONTAL);
        labelContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        labelContainer.setLayoutParams(lp);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(tokens.textPrimary);
        labelView.setTextSize(14 * scale);
        labelView.setTypeface(Typeface.MONOSPACE);
        labelView.setMaxLines(2);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        labelView.setLayoutParams(labelLp);
        labelContainer.addView(labelView);

        View helpBadge = SettingsDescriptions.createHelpBadge(context, tokens, label, label);
        if (helpBadge != null) {
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.setMargins(dpToPx(context, 4), 0, dpToPx(context, 4), 0);
            helpBadge.setLayoutParams(badgeLp);
            labelContainer.addView(helpBadge);
        }
        row.addView(labelContainer);

        TextView actionBtn = new TextView(context);
        actionBtn.setText(formatBracketText(actionText));
        int btnColor = isPositive ? tokens.navAccent : (tokens.isLight ? Color.parseColor("#CC0000") : Color.parseColor("#FF5555"));
        actionBtn.setTextColor(btnColor);
        actionBtn.setTextSize(13 * scale);
        actionBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        actionBtn.setGravity(Gravity.CENTER);
        actionBtn.setMinHeight(minTargetPx);
        actionBtn.setMinimumHeight(minTargetPx);
        actionBtn.setPadding((int) (8 * density), (int) (6 * density), (int) (8 * density), (int) (6 * density));
        actionBtn.setBackground(createTouchFeedbackDrawable(context, tokens));

        actionBtn.setClickable(true);
        actionBtn.setFocusable(true);
        actionBtn.setOnClickListener(listener);
        row.addView(actionBtn);

        container.addView(row);
    }

    public static void addButtonRow(
        Context context,
        LinearLayout container,
        String label,
        String buttonText,
        int buttonTextColor,
        View.OnClickListener listener,
        ThemeTokens tokens
    ) {
        float density = context.getResources().getDisplayMetrics().density;
        float scale = getFontScale(context);
        int minTargetPx = (int) (MIN_TOUCH_TARGET_HEIGHT_DP * density + 0.5f);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpToPx(context, 10), 0, dpToPx(context, 10));

        LinearLayout labelContainer = new LinearLayout(context);
        labelContainer.setOrientation(LinearLayout.HORIZONTAL);
        labelContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        labelContainer.setLayoutParams(lp);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(tokens.textPrimary);
        labelView.setTextSize(14 * scale);
        labelView.setTypeface(Typeface.MONOSPACE);
        labelView.setMaxLines(2);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        labelView.setLayoutParams(labelLp);
        labelContainer.addView(labelView);

        View helpBadge = SettingsDescriptions.createHelpBadge(context, tokens, label, label);
        if (helpBadge != null) {
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.setMargins(dpToPx(context, 4), 0, dpToPx(context, 4), 0);
            helpBadge.setLayoutParams(badgeLp);
            labelContainer.addView(helpBadge);
        }
        row.addView(labelContainer);

        TextView actionBtn = new TextView(context);
        actionBtn.setText(formatBracketText(buttonText));
        actionBtn.setTextColor(buttonTextColor != 0 ? buttonTextColor : tokens.navAccent);
        actionBtn.setTextSize(13 * scale);
        actionBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        actionBtn.setGravity(Gravity.CENTER);
        actionBtn.setMinHeight(minTargetPx);
        actionBtn.setMinimumHeight(minTargetPx);
        actionBtn.setPadding((int) (8 * density), (int) (6 * density), (int) (8 * density), (int) (6 * density));
        actionBtn.setBackground(createTouchFeedbackDrawable(context, tokens));

        actionBtn.setClickable(true);
        actionBtn.setFocusable(true);
        actionBtn.setOnClickListener(listener);
        row.addView(actionBtn);

        container.addView(row);
    }

    public static void addSelectorRow(
        Context context,
        LinearLayout container,
        String label,
        String valueText,
        boolean enabled,
        View.OnClickListener listener,
        ThemeTokens tokens
    ) {
        float density = context.getResources().getDisplayMetrics().density;
        int minHeightPx = (int) (MIN_TOUCH_TARGET_HEIGHT_DP * density + 0.5f);
        int padH = (int) (6 * density + 0.5f);
        int padV = (int) (6 * density + 0.5f);

        float scale = getFontScale(context);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

        LinearLayout labelContainer = new LinearLayout(context);
        labelContainer.setOrientation(LinearLayout.HORIZONTAL);
        labelContainer.setGravity(Gravity.CENTER_VERTICAL);
        labelContainer.setPadding(0, 0, 0, (int) (4 * density));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(tokens.textMuted);
        labelView.setTextSize(13 * scale);
        labelView.setTypeface(Typeface.MONOSPACE);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        labelView.setLayoutParams(labelLp);
        labelContainer.addView(labelView);

        View helpBadge = SettingsDescriptions.createHelpBadge(context, tokens, label, label);
        if (helpBadge != null) {
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.setMargins(dpToPx(context, 4), 0, 0, 0);
            helpBadge.setLayoutParams(badgeLp);
            labelContainer.addView(helpBadge);
        }
        row.addView(labelContainer);

        String cleanVal = (valueText != null ? valueText.trim() : "");
        while (cleanVal.startsWith("[") && cleanVal.endsWith("]")) {
            cleanVal = cleanVal.substring(1, cleanVal.length() - 1).trim();
        }

        TextView triggerBtn = new TextView(context);
        triggerBtn.setText("[ " + cleanVal + (enabled ? "  ▼ ]" : " ]"));
        triggerBtn.setTextColor(enabled ? tokens.textPrimary : tokens.textMuted);
        triggerBtn.setTextSize(13 * scale);
        triggerBtn.setTypeface(Typeface.MONOSPACE, enabled ? Typeface.BOLD : Typeface.NORMAL);
        triggerBtn.setSingleLine(true);
        triggerBtn.setEllipsize(TextUtils.TruncateAt.END);
        triggerBtn.setGravity(Gravity.CENTER_VERTICAL);
        triggerBtn.setMinHeight(minHeightPx);
        triggerBtn.setMinimumHeight(minHeightPx);
        triggerBtn.setPadding(padH, padV, padH, padV);
        triggerBtn.setBackground(createTouchFeedbackDrawable(context, tokens));

        triggerBtn.setClickable(true);
        triggerBtn.setFocusable(true);
        triggerBtn.setOnClickListener(listener);

        row.addView(triggerBtn);
        container.addView(row);
    }

    public static void addInfoRow(
        Context context,
        LinearLayout container,
        String key,
        String value,
        ThemeTokens tokens
    ) {
        float scale = getFontScale(context);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dpToPx(context, 6), 0, dpToPx(context, 8));

        LinearLayout labelContainer = new LinearLayout(context);
        labelContainer.setOrientation(LinearLayout.HORIZONTAL);
        labelContainer.setGravity(Gravity.CENTER_VERTICAL);

        TextView k = new TextView(context);
        k.setText(key);
        k.setTextColor(tokens.textMuted);
        k.setTextSize(11 * scale);
        k.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams kLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        k.setLayoutParams(kLp);
        k.setSingleLine(true);
        k.setEllipsize(TextUtils.TruncateAt.END);
        labelContainer.addView(k);

        View helpBadge = SettingsDescriptions.createHelpBadge(context, tokens, key, key);
        if (helpBadge != null) {
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.setMargins(dpToPx(context, 4), 0, 0, 0);
            helpBadge.setLayoutParams(badgeLp);
            labelContainer.addView(helpBadge);
        }
        row.addView(labelContainer);

        TextView v = new TextView(context);
        v.setText(value != null ? value : "Unknown");
        v.setTextColor(tokens.textPrimary);
        v.setTextSize(13 * scale);
        v.setTypeface(Typeface.MONOSPACE);
        v.setPadding(0, dpToPx(context, 2), 0, dpToPx(context, 4));
        row.addView(v);

        container.addView(row);
    }

    public static Drawable createTouchFeedbackDrawable(Context context, ThemeTokens tokens) {
        return TactileUi.createTouchFeedbackDrawable(context, tokens);
    }

    public static TextView createReorderButton(
        Context context,
        String text,
        int textColor,
        View.OnClickListener listener,
        ThemeTokens tokens
    ) {
        float density = context.getResources().getDisplayMetrics().density;
        float scale = getFontScale(context);
        int minHeightPx = (int) (MIN_TOUCH_TARGET_HEIGHT_DP * density);
        int minWidthPx = (int) (MIN_TOUCH_TARGET_WIDTH_DP * density);
        int marginPx = (int) (BUTTON_MARGIN_DP * density);

        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextColor(textColor != 0 ? textColor : tokens.navAccent);
        btn.setTextSize(12 * scale);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setMinHeight(minHeightPx);
        btn.setMinimumHeight(minHeightPx);
        btn.setMinWidth(minWidthPx);
        btn.setMinimumWidth(minWidthPx);
        btn.setPadding((int) (6 * density), (int) (4 * density), (int) (6 * density), (int) (4 * density));
        btn.setBackground(TactileUi.createButtonDrawable(
            tokens.controlBgNormal,
            tokens.controlBgPressed,
            tokens.border,
            Math.max(1, (int) (1 * density + 0.5f)),
            (int) (4 * density)
        ));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnClickListener(listener);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(marginPx, 0, marginPx, 0);
        btn.setLayoutParams(lp);

        return btn;
    }

    public static void styleMinimalSeekBar(Context context, SeekBar seekBar, ThemeTokens tokens) {
        float density = context.getResources().getDisplayMetrics().density;
        int trackHeight = (int) (4 * density + 0.5f);
        int thumbWidth = (int) (12 * density + 0.5f);
        int thumbHeight = (int) (22 * density + 0.5f);

        // 1. Background Track
        GradientDrawable bgTrack = new GradientDrawable();
        bgTrack.setColor(tokens.isLight ? Color.parseColor("#E0E0E0") : Color.parseColor("#18181B"));
        bgTrack.setStroke((int) (1 * density + 0.5f), tokens.border);
        bgTrack.setCornerRadius((int) (2 * density));
        bgTrack.setSize(-1, trackHeight);

        // 2. Progress Track
        GradientDrawable progressShape = new GradientDrawable();
        progressShape.setColor(tokens.accent);
        progressShape.setCornerRadius((int) (2 * density));
        progressShape.setSize(-1, trackHeight);
        ClipDrawable progressClip = new ClipDrawable(progressShape, Gravity.START | Gravity.CENTER_VERTICAL, ClipDrawable.HORIZONTAL);

        // 3. LayerDrawable
        Drawable[] layers = new Drawable[]{bgTrack, progressClip};
        LayerDrawable trackLayer = new LayerDrawable(layers);
        trackLayer.setId(0, android.R.id.background);
        trackLayer.setId(1, android.R.id.progress);
        seekBar.setProgressDrawable(trackLayer);

        // 4. Sleek Minimal Thumb
        GradientDrawable thumbNormal = new GradientDrawable();
        thumbNormal.setColor(tokens.accent);
        thumbNormal.setStroke((int) (2 * density), tokens.bg);
        thumbNormal.setCornerRadius((int) (2 * density));
        thumbNormal.setSize(thumbWidth, thumbHeight);

        GradientDrawable thumbPressed = new GradientDrawable();
        thumbPressed.setColor(tokens.navAccent);
        thumbPressed.setStroke((int) (2 * density), tokens.bg);
        thumbPressed.setCornerRadius((int) (2 * density));
        thumbPressed.setSize((int) (14 * density), (int) (26 * density));

        StateListDrawable thumb = new StateListDrawable();
        thumb.addState(new int[]{android.R.attr.state_pressed}, thumbPressed);
        thumb.addState(new int[]{android.R.attr.state_focused}, thumbPressed);
        thumb.addState(new int[]{}, thumbNormal);

        seekBar.setThumb(thumb);
        seekBar.setThumbOffset(thumbWidth / 2);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setSplitTrack(false);
        }
        seekBar.setPadding((int) (10 * density), (int) (10 * density), (int) (10 * density), (int) (10 * density));
    }

    public static String formatBandDbText(String bandName, int db) {
        String sign = db > 0 ? "+" : "";
        return bandName + ": " + sign + db + " dB";
    }

    private static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
