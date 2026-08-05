package hev.htproxy;

public final class TProxyService {
    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    private TProxyService() {
    }

    public static boolean start(String configPath, int fd) {
        return TProxyStartService(configPath, fd);
    }

    public static boolean stop() {
        return TProxyStopService();
    }

    public static boolean isRunning() {
        return TProxyIsRunning();
    }

    public static long[] stats() {
        return TProxyGetStats();
    }

    private static native boolean TProxyStartService(String configPath, int fd);

    private static native boolean TProxyStopService();

    private static native boolean TProxyIsRunning();

    private static native long[] TProxyGetStats();
}
