package dev.zapret.mobile;

public final class NativeZapretEngine {
    static {
        System.loadLibrary("zapret_engine");
    }

    private NativeZapretEngine() {
    }

    public static native String version();

    public static native int configure(
        ZapretVpnService vpnService,
        int profileId,
        boolean blockQuic
    );

    public static native int configureCustomStrategy(int splitPosition, long delayMs);

    public static native int configureFakeTtl(int ttl);

    public static native int configureHostlist(String domainsCsv, boolean hostlistOnly);

    public static native int pollFailureCount();

    public static native int start(int socksPort);

    public static native void stop();
}
