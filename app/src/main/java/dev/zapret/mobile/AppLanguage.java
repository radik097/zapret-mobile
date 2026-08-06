package dev.zapret.mobile;

/**
 * The languages the app ships translations for. Russian is the default: the
 * app targets networks where Russian-language DPI circumvention is the point,
 * so it does not follow the system locale unless the user says otherwise.
 */
enum AppLanguage {
    RUSSIAN("ru", "Русский"),
    ENGLISH("en", "English");

    final String tag;
    /** Always written in the language itself, never translated. */
    final String ownName;

    AppLanguage(String tag, String ownName) {
        this.tag = tag;
        this.ownName = ownName;
    }

    /** The language the toggle would switch to. */
    AppLanguage other() {
        return this == RUSSIAN ? ENGLISH : RUSSIAN;
    }

    static AppLanguage fromTag(String tag) {
        for (AppLanguage language : values()) {
            if (language.tag.equals(tag)) {
                return language;
            }
        }
        return RUSSIAN;
    }
}
