package dev.zapret.mobile;

/** A selectable visual design for the whole app: page background, text, accent and a header gradient. */
enum AppTheme {
    CLASSIC(
        "classic",
        R.string.theme_classic,
        0xFFF6F8F7, 0xFF151A1D, 0xFF42514A,
        0xFF0F8A5F, 0xFFFFFFFF,
        0xFFFFFFFF,
        0xFF0F8A5F, 0xFF14A874
    ),
    MIDNIGHT(
        "midnight",
        R.string.theme_midnight,
        0xFF10151A, 0xFFE8F1EC, 0xFF9FB3AC,
        0xFF3DDC97, 0xFF10151A,
        0xFF1A2228,
        0xFF0B2A22, 0xFF14202B
    ),
    AURORA(
        "aurora",
        R.string.theme_aurora,
        0xFFF3EEFF, 0xFF1B1033, 0xFF5B4B8A,
        0xFF7C4DFF, 0xFFFFFFFF,
        0xFFFFFFFF,
        0xFF7C4DFF, 0xFF22D3C5
    );

    final String id;
    final int labelResource;
    final int background;
    final int textPrimary;
    final int textSecondary;
    final int accent;
    final int onAccent;
    final int cardBackground;
    final int gradientStart;
    final int gradientEnd;

    AppTheme(
        String id,
        int labelResource,
        int background,
        int textPrimary,
        int textSecondary,
        int accent,
        int onAccent,
        int cardBackground,
        int gradientStart,
        int gradientEnd
    ) {
        this.id = id;
        this.labelResource = labelResource;
        this.background = background;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.accent = accent;
        this.onAccent = onAccent;
        this.cardBackground = cardBackground;
        this.gradientStart = gradientStart;
        this.gradientEnd = gradientEnd;
    }

    static AppTheme fromId(String id) {
        for (AppTheme theme : values()) {
            if (theme.id.equals(id)) {
                return theme;
            }
        }
        return CLASSIC;
    }
}
