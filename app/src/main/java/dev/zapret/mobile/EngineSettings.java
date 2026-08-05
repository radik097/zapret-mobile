package dev.zapret.mobile;

import android.content.Context;

final class EngineSettings {
    private static final String PREFERENCES = "engine_settings";
    private static final String KEY_BLOCK_QUIC = "block_quic";
    private static final String KEY_PROFILE = "strategy_profile";

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
}
