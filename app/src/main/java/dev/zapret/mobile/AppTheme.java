package dev.zapret.mobile;

/**
 * A selectable visual design for the whole app.
 *
 * Every palette is dark-surfaced and uses the same grey-white text: only the
 * accent colour and the header gradient change. That is why styles.xml holds
 * a single dark platform theme rather than one per palette -- surfaces and
 * text cannot drift out of step with it the way they did when a light
 * platform theme was shared by a dark palette.
 */
enum AppTheme {
    MIDNIGHT(
        "midnight", R.string.theme_midnight,
        0xFF3DDC97,
        0xFF0B2A22, 0xFF14202B
    ),
    CLASSIC(
        "classic", R.string.theme_classic,
        0xFF16B37D,
        0xFF0C2A1F, 0xFF103028
    ),
    AURORA(
        "aurora", R.string.theme_aurora,
        0xFF9B7BFF,
        0xFF241A45, 0xFF12303A
    );

    // Shared by every palette. These are constant variables, so referencing
    // them from the constructor is permitted despite the usual rule against
    // touching static state during enum construction. Keep in sync with
    // colors.xml, which feeds the same values to the platform theme.
    private static final int BACKGROUND = 0xFF10151A;
    private static final int CARD_BACKGROUND = 0xFF1A2228;
    private static final int TEXT_PRIMARY = 0xFFECF1EE;
    private static final int TEXT_SECONDARY = 0xFFA7B4B0;
    /** Text on the dark surfaces the accent colour itself is painted on. */
    private static final int ON_GRADIENT = 0xFFECF1EE;

    final String id;
    final int labelResource;
    final int background;
    final int textPrimary;
    final int textSecondary;
    /** The one colour that distinguishes the palettes. Always bright. */
    final int accent;
    /**
     * Text drawn on top of the flat accent colour -- dark, because the accent
     * is bright. Not to be confused with {@link #onGradient}: the header
     * gradient is dark and needs light text, and sharing one colour for both
     * once left the app's own title near-black on a near-black banner.
     */
    final int onAccent;
    final int cardBackground;
    final int gradientStart;
    final int gradientEnd;
    final int onGradient;

    AppTheme(String id, int labelResource, int accent, int gradientStart, int gradientEnd) {
        this.id = id;
        this.labelResource = labelResource;
        this.accent = accent;
        this.gradientStart = gradientStart;
        this.gradientEnd = gradientEnd;
        this.onAccent = BACKGROUND;
        this.background = BACKGROUND;
        this.cardBackground = CARD_BACKGROUND;
        this.textPrimary = TEXT_PRIMARY;
        this.textSecondary = TEXT_SECONDARY;
        this.onGradient = ON_GRADIENT;
    }

    static AppTheme fromId(String id) {
        for (AppTheme theme : values()) {
            if (theme.id.equals(id)) {
                return theme;
            }
        }
        return MIDNIGHT;
    }
}
