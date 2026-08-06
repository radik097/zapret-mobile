package dev.zapret.mobile;

import android.content.Context;

final class ThemeSettings {
    private static final String PREFERENCES = "theme_settings";
    private static final String KEY_THEME = "selected_theme";

    private ThemeSettings() {
    }

    static AppTheme getTheme(Context context) {
        String id = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_THEME, AppTheme.MIDNIGHT.id);
        return AppTheme.fromId(id);
    }

    static void setTheme(Context context, AppTheme theme) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.id)
            .apply();
    }
}
