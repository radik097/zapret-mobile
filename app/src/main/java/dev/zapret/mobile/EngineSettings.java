package dev.zapret.mobile;

import android.content.Context;

final class EngineSettings {
    private static final String PREFERENCES = "engine_settings";
    private static final String KEY_BLOCK_QUIC = "block_quic";
    private static final String KEY_PROFILE = "strategy_profile";
    private static final String KEY_CUSTOM_SPLIT_POSITION = "custom_strategy_split_position";
    private static final String KEY_CUSTOM_DELAY_MS = "custom_strategy_delay_ms";
    private static final String KEY_CUSTOM_STRATEGY_NAME = "custom_strategy_name";

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
            .getInt(KEY_PROFILE, StrategyProfile.BALANCED.getNativeId());
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
}
