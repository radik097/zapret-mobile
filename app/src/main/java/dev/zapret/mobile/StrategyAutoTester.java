package dev.zapret.mobile;

import android.os.Handler;
import android.os.Looper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An in-app equivalent of bol-van/zapret's blockcheck.sh: while the VPN is
 * running, temporarily switches the live native engine through each
 * built-in profile and makes a real SOCKS5 CONNECT + TLS handshake + tiny
 * HTTP GET to a couple of commonly-blocked domains, so the result reflects
 * whether the strategy actually gets a TLS ClientHello past this network's
 * DPI right now -- not just whether a socket opens.
 */
final class StrategyAutoTester {
    private static final String TAG = "StrategyAutoTester";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String[] TEST_DOMAINS = {"discord.com", "www.youtube.com"};
    private static final int SOCKET_TIMEOUT_MS = 6_000;

    private static final StrategyProfile[] TESTABLE_PROFILES = {
        StrategyProfile.FLOWSEAL,
        StrategyProfile.MULTISPLIT,
        StrategyProfile.ZAPTRET2,
        StrategyProfile.AGGRESSIVE,
        StrategyProfile.BALANCED,
        StrategyProfile.COMPATIBLE
    };

    static final class DomainResult {
        final String domain;
        final boolean success;
        final long elapsedMs;
        final String detail;

        DomainResult(String domain, boolean success, long elapsedMs, String detail) {
            this.domain = domain;
            this.success = success;
            this.elapsedMs = elapsedMs;
            this.detail = detail;
        }
    }

    static final class ProfileResult {
        final StrategyProfile profile;
        final List<DomainResult> domainResults;

        ProfileResult(StrategyProfile profile, List<DomainResult> domainResults) {
            this.profile = profile;
            this.domainResults = domainResults;
        }

        int successCount() {
            int count = 0;
            for (DomainResult result : domainResults) {
                if (result.success) {
                    count += 1;
                }
            }
            return count;
        }

        boolean allSucceeded() {
            return successCount() == domainResults.size();
        }
    }

    interface Callback {
        void onProfileStarted(StrategyProfile profile);

        void onProfileFinished(ProfileResult result);

        void onComplete(List<ProfileResult> results);
    }

    private StrategyAutoTester() {
    }

    static void runAll(ZapretVpnService service, Callback callback) {
        EXECUTOR.execute(() -> {
            AppLog.i(service, TAG, "Auto-test started");
            List<ProfileResult> results = new ArrayList<>();
            for (StrategyProfile profile : TESTABLE_PROFILES) {
                postToMain(() -> callback.onProfileStarted(profile));
                service.applyProfileForTesting(profile);

                List<DomainResult> domainResults = new ArrayList<>();
                for (String domain : TEST_DOMAINS) {
                    domainResults.add(testDomain(service, profile, domain));
                }

                ProfileResult profileResult = new ProfileResult(profile, domainResults);
                results.add(profileResult);
                AppLog.i(service, TAG, "Profile " + profile + ": " + profileResult.successCount()
                    + "/" + profileResult.domainResults.size() + " domains OK");
                postToMain(() -> callback.onProfileFinished(profileResult));
            }

            try {
                service.applyConfiguredStrategy();
            } catch (Exception error) {
                AppLog.w(service, TAG, "Failed to restore configured strategy after auto-test: " + error);
            }
            AppLog.i(service, TAG, "Auto-test finished");

            List<ProfileResult> finalResults = results;
            postToMain(() -> callback.onComplete(finalResults));
        });
    }

    /**
     * Records the certificate chain the peer presented, then hands it to the
     * platform's own trust manager unchanged. This is deliberately *not* a
     * trust-all manager: validation still happens exactly as before and a bad
     * chain still fails the handshake. The only added behaviour is keeping the
     * chain around so a failure can be reported as "who signed this", which is
     * what distinguishes a DPI-injected substitute certificate from an
     * ordinary handshake failure.
     */
    private static final class ChainRecordingTrustManager implements X509TrustManager {
        private final X509TrustManager delegate;
        private volatile X509Certificate[] lastChain;

        ChainRecordingTrustManager(X509TrustManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            lastChain = chain;
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }

        /** e.g. {@code "subject=CN=discord.com, issuer=CN=R11,O=Let's Encrypt"}, or null if nothing was presented. */
        String describeLeaf() {
            X509Certificate[] chain = lastChain;
            if (chain == null || chain.length == 0) {
                return null;
            }
            X509Certificate leaf = chain[0];
            return "subject=" + leaf.getSubjectX500Principal().getName()
                + ", issuer=" + leaf.getIssuerX500Principal().getName()
                + ", chain_length=" + chain.length;
        }
    }

    private static X509TrustManager platformTrustManager() throws Exception {
        TrustManagerFactory factory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new IllegalStateException("No platform X509TrustManager available");
    }

    private static DomainResult testDomain(ZapretVpnService service, StrategyProfile profile, String domain) {
        long start = System.currentTimeMillis();
        DomainResult result;
        ChainRecordingTrustManager trustManager = null;
        try (Socket socksSocket = new Socket()) {
            socksSocket.connect(new InetSocketAddress("127.0.0.1", ZapretVpnService.SOCKS_PORT), SOCKET_TIMEOUT_MS);
            socksSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
            socksHandshake(socksSocket, domain);

            trustManager = new ChainRecordingTrustManager(platformTrustManager());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {trustManager}, null);
            SSLSocketFactory sslFactory = sslContext.getSocketFactory();
            try (SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(socksSocket, domain, 443, true)) {
                sslSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
                sslSocket.startHandshake();

                String request = "GET / HTTP/1.1\r\nHost: " + domain
                    + "\r\nUser-Agent: zapret-mobile-autotest\r\nConnection: close\r\n\r\n";
                OutputStream output = sslSocket.getOutputStream();
                output.write(request.getBytes(StandardCharsets.US_ASCII));
                output.flush();

                byte[] buffer = new byte[64];
                int read = sslSocket.getInputStream().read(buffer);
                long elapsed = System.currentTimeMillis() - start;
                result = read > 0
                    ? new DomainResult(domain, true, elapsed, "ok")
                    : new DomainResult(domain, false, elapsed, "empty_response");
            }
        } catch (Exception error) {
            long elapsed = System.currentTimeMillis() - start;
            String detail = error.getClass().getSimpleName() + ": " + error.getMessage();
            String presented = trustManager == null ? null : trustManager.describeLeaf();
            if (presented != null) {
                detail = detail + " | peer cert: " + presented;
            }
            result = new DomainResult(domain, false, elapsed, detail);
        }
        AppLog.i(service, TAG, profile + " " + domain + ": " + (result.success ? "OK" : "FAILED (" + result.detail + ")")
            + " in " + result.elapsedMs + "ms");
        return result;
    }

    /** Speaks SOCKS5 to our own native engine's local proxy; the reply shape is fixed (see write_socks_success in lib.rs). */
    private static void socksHandshake(Socket socket, String domain) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        out.write(new byte[] {0x05, 0x01, 0x00});
        out.flush();
        byte[] greeting = readExactly(in, 2);
        if (greeting[0] != 0x05 || greeting[1] != 0x00) {
            throw new IOException("SOCKS5 greeting rejected");
        }

        byte[] hostBytes = domain.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        request.write(0x05);
        request.write(0x01);
        request.write(0x00);
        request.write(0x03);
        request.write(hostBytes.length);
        request.write(hostBytes, 0, hostBytes.length);
        request.write((443 >> 8) & 0xFF);
        request.write(443 & 0xFF);
        out.write(request.toByteArray());
        out.flush();

        byte[] reply = readExactly(in, 10);
        if (reply[1] != 0x00) {
            throw new IOException("SOCKS5 CONNECT rejected, status=" + (reply[1] & 0xFF));
        }
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] buffer = new byte[length];
        int total = 0;
        while (total < length) {
            int read = input.read(buffer, total, length - total);
            if (read == -1) {
                throw new IOException("Unexpected end of stream after " + total + " of " + length + " bytes");
            }
            total += read;
        }
        return buffer;
    }

    private static void postToMain(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }
}
