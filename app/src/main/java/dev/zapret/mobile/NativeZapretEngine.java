package dev.zapret.mobile;

public final class NativeZapretEngine {
    static {
        System.loadLibrary("zapret_engine");
    }

    private NativeZapretEngine() {
    }

    public static native String version();

    public static native int start(int socksPort);

    public static native void stop();
}
