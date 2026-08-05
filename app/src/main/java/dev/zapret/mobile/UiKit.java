package dev.zapret.mobile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Shared, theme-aware view builders so Main/Settings/Strategies stay visually consistent without XML layouts. */
final class UiKit {
    private UiKit() {
    }

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    static LinearLayout.LayoutParams fullWidth(Context context, int marginTopDp, int marginBottomDp) {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, dp(context, marginTopDp), 0, dp(context, marginBottomDp));
        return params;
    }

    static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp * context.getResources().getDisplayMetrics().density);
        return drawable;
    }

    static LinearLayout headerBanner(Context context, AppTheme theme, String title, String subtitle) {
        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(context, 24);
        banner.setPadding(padH, dp(context, 28), padH, dp(context, 28));

        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {theme.gradientStart, theme.gradientEnd}
        );
        banner.setBackground(background);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(theme.onAccent);
        titleView.setTextSize(28);
        titleView.setGravity(Gravity.CENTER);
        banner.addView(titleView, fullWidth());

        if (subtitle != null) {
            TextView subtitleView = new TextView(context);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(withAlpha(theme.onAccent, 0xCC));
            subtitleView.setTextSize(14);
            subtitleView.setGravity(Gravity.CENTER);
            banner.addView(subtitleView, fullWidth(context, 8, 0));
        }
        return banner;
    }

    static LinearLayout card(Context context, AppTheme theme) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 18);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(rounded(theme.cardBackground, 16, context));
        return card;
    }

    static TextView sectionTitle(Context context, AppTheme theme, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(theme.textPrimary);
        view.setTextSize(18);
        return view;
    }

    static TextView bodyText(Context context, AppTheme theme, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(theme.textSecondary);
        view.setTextSize(14);
        return view;
    }

    static Button primaryButton(Context context, AppTheme theme, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(theme.onAccent);
        button.setBackground(rounded(theme.accent, 14, context));
        return button;
    }

    static Button dangerButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(0xFFFFFFFF);
        button.setBackground(rounded(0xFFD64545, 14, context));
        return button;
    }

    static Button outlineButton(Context context, AppTheme theme, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(theme.accent);
        GradientDrawable background = rounded(theme.cardBackground, 14, context);
        background.setStroke(Math.max(1, dp(context, 1)), theme.accent);
        button.setBackground(background);
        return button;
    }

    static Switch styledSwitch(Context context, AppTheme theme, String text) {
        Switch switchView = new Switch(context);
        switchView.setText(text);
        switchView.setTextColor(theme.textPrimary);
        ColorStateList thumbTint = new ColorStateList(
            new int[][] {{android.R.attr.state_checked}, {}},
            new int[] {theme.accent, theme.textSecondary}
        );
        ColorStateList trackTint = new ColorStateList(
            new int[][] {{android.R.attr.state_checked}, {}},
            new int[] {withAlpha(theme.accent, 0x80), withAlpha(theme.textSecondary, 0x60)}
        );
        switchView.setThumbTintList(thumbTint);
        switchView.setTrackTintList(trackTint);
        return switchView;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
