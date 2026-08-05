package dev.zapret.mobile;

enum StrategyProfile {
    COMPATIBLE(0, R.string.profile_compatible),
    BALANCED(1, R.string.profile_balanced),
    AGGRESSIVE(2, R.string.profile_aggressive),
    ZAPTRET2(3, R.string.profile_zaptret2),
    CUSTOM(4, R.string.profile_custom);

    private final int nativeId;
    private final int labelResource;

    StrategyProfile(int nativeId, int labelResource) {
        this.nativeId = nativeId;
        this.labelResource = labelResource;
    }

    int getNativeId() {
        return nativeId;
    }

    int getLabelResource() {
        return labelResource;
    }

    static StrategyProfile fromNativeId(int nativeId) {
        for (StrategyProfile profile : values()) {
            if (profile.nativeId == nativeId) {
                return profile;
            }
        }
        return BALANCED;
    }
}
