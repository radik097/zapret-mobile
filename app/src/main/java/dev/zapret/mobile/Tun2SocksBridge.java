package dev.zapret.mobile;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import hev.htproxy.TProxyService;

public final class Tun2SocksBridge {
    private final File configFile;
    private int tunFd;

    private Tun2SocksBridge(File configFile, int tunFd) {
        this.configFile = configFile;
        this.tunFd = tunFd;
    }

    public static Tun2SocksBridge start(Context context, ParcelFileDescriptor tun, int socksPort) throws IOException {
        File config = new File(context.getCacheDir(), "hev-socks5-tunnel.yml");
        writeConfig(config, socksPort);

        ParcelFileDescriptor duplicate = ParcelFileDescriptor.dup(tun.getFileDescriptor());
        int detachedFd = duplicate.detachFd();
        if (!TProxyService.start(config.getAbsolutePath(), detachedFd)) {
            ParcelFileDescriptor.adoptFd(detachedFd).close();
            throw new IOException("hev-socks5-tunnel refused to start");
        }
        return new Tun2SocksBridge(config, detachedFd);
    }

    public void stop() {
        closeDetachedTunFd();
        TProxyService.stop();
    }

    public boolean isRunning() {
        return TProxyService.isRunning();
    }

    public long[] stats() {
        return TProxyService.stats();
    }

    public File configFile() {
        return configFile;
    }

    private static void writeConfig(File config, int socksPort) throws IOException {
        String content = ""
            + "tunnel:\n"
            + "  name: tun0\n"
            + "  mtu: 1500\n"
            + "  multi-queue: false\n"
            + "  ipv4: 10.71.0.1\n"
            + "  icmp: 'off'\n"
            + "socks5:\n"
            + "  port: " + socksPort + "\n"
            + "  address: 127.0.0.1\n"
            + "  udp: 'udp'\n"
            + "misc:\n"
            + "  task-stack-size: 24576\n"
            + "  tcp-buffer-size: 65536\n"
            + "  udp-recv-buffer-size: 262144\n"
            + "  connect-timeout: 10000\n"
            + "  tcp-read-write-timeout: 300000\n"
            + "  udp-read-write-timeout: 60000\n"
            + "  log-file: stderr\n"
            + "  log-level: info\n";
        try (FileOutputStream output = new FileOutputStream(config, false)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void closeDetachedTunFd() {
        if (tunFd < 0) {
            return;
        }
        int fd = tunFd;
        tunFd = -1;
        try {
            ParcelFileDescriptor.adoptFd(fd).close();
        } catch (IOException ignored) {
            // The native tunnel may already have closed the fd while shutting down.
        }
    }
}
