package dev.zapret.mobile;

enum StrategyProfile {
    COMPATIBLE(0, R.string.profile_compatible),
    BALANCED(1, R.string.profile_balanced),
    AGGRESSIVE(2, R.string.profile_aggressive),
    ZAPTRET2(3, R.string.profile_zaptret2),
    CUSTOM(4, R.string.profile_custom),
    // Default/primary profile: low-TTL fake decoy ClientHello + early real
    // split, mirroring bol-van/zapret's "fakedsplit" as packaged by
    // Flowseal's zapret-discord-youtube presets.
    FLOWSEAL(5, R.string.profile_flowseal),
    // Cuts the first payload into several TCP segments (record header, then
    // twice inside the SNI) instead of two, mirroring bol-van/zapret's
    // --dpi-desync=multisplit. Beats DPI that reassembles a fixed number of
    // segments, which a single split does not.
    MULTISPLIT(6, R.string.profile_multisplit);

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
        return FLOWSEAL;
    }
}
