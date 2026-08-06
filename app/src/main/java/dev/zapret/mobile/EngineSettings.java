package dev.zapret.mobile;

import android.content.Context;

final class EngineSettings {
    private static final String PREFERENCES = "engine_settings";
    private static final String KEY_BLOCK_QUIC = "block_quic";
    private static final String KEY_PROFILE = "strategy_profile";
    private static final String KEY_CUSTOM_SPLIT_POSITION = "custom_strategy_split_position";
    private static final String KEY_CUSTOM_DELAY_MS = "custom_strategy_delay_ms";
    private static final String KEY_CUSTOM_STRATEGY_NAME = "custom_strategy_name";
    private static final String KEY_FAKE_TTL = "fake_ttl";
    private static final int DEFAULT_FAKE_TTL = 6;
    private static final String KEY_FAKE_DECOY_ENABLED = "fake_decoy_enabled";
    private static final String KEY_HOSTLIST_ONLY = "hostlist_only";
    private static final String KEY_HOSTLIST_DOMAINS = "hostlist_domains";
    // Mirrors Flowseal's default Discord/YouTube targeting.
    static final String DEFAULT_HOSTLIST =
        "discord.com,discordapp.com,discord.gg,discordapp.net,"
            + "youtube.com,youtu.be,googlevideo.com,ytimg.com,ggpht.com";

    private EngineSettings() {
    }

    static boolean isQuicBlocked(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLOCK_QUIC, true);
    }

    static void setQuicBlocked(Context context, boolean blocked) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BLOCK_QUIC, blocked)
            .apply();
    }

    static StrategyProfile getStrategyProfile(Context context) {
        int nativeId = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getInt(KEY_PROFILE, StrategyProfile.FLOWSEAL.getNativeId());
        return StrategyProfile.fromNativeId(nativeId);
    }

    static void setStrategyProfile(Context context, StrategyProfile profile) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PROFILE, profile.getNativeId())
            .apply();
    }

    static int getCustomStrategySplitPosition(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getInt(KEY_CUSTOM_SPLIT_POSITION, 1);
    }

    static long getCustomStrategyDelayMs(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getLong(KEY_CUSTOM_DELAY_MS, 0L);
    }

    static String getCustomStrategyName(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_STRATEGY_NAME, "");
    }

    static void setCustomStrategy(Context context, String name, int splitPosition, long delayMs) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_STRATEGY_NAME, name)
            .putInt(KEY_CUSTOM_SPLIT_POSITION, splitPosition)
            .putLong(KEY_CUSTOM_DELAY_MS, delayMs)
            .apply();
    }

    static int getFakeTtl(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getInt(KEY_FAKE_TTL, DEFAULT_FAKE_TTL);
    }

    static void setFakeTtl(Context context, int ttl) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FAKE_TTL, ttl)
            .apply();
    }

    /**
     * Off by default: a low-TTL fake decoy only helps if the TTL is tuned
     * below this network's real hop count to the destination. Guessed wrong
     * (too high), the decoy reaches the real server intact and corrupts the
     * handshake instead of the DPI middlebox alone seeing it. Split-only is
     * the safe default; this is an opt-in for users who tune the TTL above.
     */
    static boolean isFakeDecoyEnabled(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_FAKE_DECOY_ENABLED, false);
    }

    static void setFakeDecoyEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FAKE_DECOY_ENABLED, enabled)
            .apply();
    }

    static boolean isHostlistOnly(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_HOSTLIST_ONLY, false);
    }

    static String getHostlistDomains(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_HOSTLIST_DOMAINS, DEFAULT_HOSTLIST);
    }

    static void setHostlistTargeting(Context context, boolean hostlistOnly, String domainsCsv) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HOSTLIST_ONLY, hostlistOnly)
            .putString(KEY_HOSTLIST_DOMAINS, domainsCsv)
            .apply();
    }
}
