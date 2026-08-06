package dev.zapret.mobile;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * Stores the chosen UI language and applies it to a Context.
 *
 * Per-app language via the platform's own LocaleManager needs API 33, and
 * AppCompat's equivalent needs AppCompat -- this app has neither (minSdk 26,
 * no library dependencies), so the locale is applied by wrapping each
 * component's base context before its resources are first read.
 */
final class LanguageSettings {
    private static final String PREFERENCES = "language_settings";
    private static final String KEY_LANGUAGE = "ui_language";

    private LanguageSettings() {
    }

    static AppLanguage getLanguage(Context context) {
        String tag = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.RUSSIAN.tag);
        return AppLanguage.fromTag(tag);
    }

    static void setLanguage(Context context, AppLanguage language) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.tag)
            .apply();
    }

    /**
     * Returns `base` re-configured for the saved language. Call from
     * {@code attachBaseContext} in every Activity and Service that reads
     * strings -- resources are resolved against the context they were loaded
     * from, so a component that skips this keeps the system language.
     */
    static Context wrap(Context base) {
        Locale locale = new Locale(getLanguage(base).tag);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return base.createConfigurationContext(configuration);
    }
}
