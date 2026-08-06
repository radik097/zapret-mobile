package dev.zapret.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public final class ZapretVpnService extends android.net.VpnService {
    public static final String ACTION_START = "dev.zapret.mobile.action.START";
    public static final String ACTION_STOP = "dev.zapret.mobile.action.STOP";
    private static final String TAG = "ZapretVpnService";
    private static final String CHANNEL_ID = "zapret_mobile_vpn";
    private static final int NOTIFICATION_ID = 7101;
    static final int SOCKS_PORT = 1080;

    private static volatile ZapretVpnService runningInstance;

    private final AtomicReference<VpnState> state = new AtomicReference<>(VpnState.STOPPED);
    private ParcelFileDescriptor tun;
    private Tun2SocksBridge tunBridge;
    private ConnectivityManager.NetworkCallback networkCallback;
    private StrategyFallbackController fallbackController;

    /** The currently running instance, or null if the VPN isn't started. Same-process only. */
    static ZapretVpnService getRunningInstance() {
        return runningInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopVpn();
            return START_NOT_STICKY;
        }
        startVpn();
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        stopVpn();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    private synchronized void startVpn() {
        VpnState current = state.get();
        if (current == VpnState.RUNNING || current == VpnState.STARTING) {
            return;
        }

        state.set(VpnState.STARTING);
        startForeground(NOTIFICATION_ID, buildNotification(true, null));

        try {
            StrategyProfile profile = EngineSettings.getStrategyProfile(this);
            boolean blockQuic = EngineSettings.isQuicBlocked(this);
            applyConfiguredStrategy();
            NativeZapretEngine.start(SOCKS_PORT);
            Builder builder = new Builder()
                .setSession(getString(R.string.vpn_session))
                .setMtu(1500)
                .addAddress("10.71.0.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .allowFamily(OsConstants.AF_INET);
            configureAppRouting(builder);
            tun = builder.establish();
            if (tun == null) {
                throw new IllegalStateException("VpnService.Builder.establish returned null");
            }
            tunBridge = Tun2SocksBridge.start(this, tun, SOCKS_PORT);
            registerNetworkCallback();
            fallbackController = new StrategyFallbackController(this, profile, blockQuic);
            fallbackController.setListener(this::onStrategyFallback);
            fallbackController.start();
            state.set(VpnState.RUNNING);
            runningInstance = this;
            Log.i(TAG, "Strategy profile: " + profile.name().toLowerCase(java.util.Locale.ROOT));
            Log.i(TAG, "QUIC/UDP 443 policy: " + (blockQuic ? "blocked" : "allowed"));
            Log.i(TAG, "VPN started with TUN-to-SOCKS bridge and local SOCKS on 127.0.0.1:" + SOCKS_PORT);
        } catch (Exception error) {
            state.set(VpnState.ERROR);
            Log.e(TAG, "Failed to start VPN", error);
            stopVpn();
        }
    }

    /** Applies the user's saved strategy/QUIC/hostlist settings to the native engine. */
    void applyConfiguredStrategy() {
        StrategyProfile profile = EngineSettings.getStrategyProfile(this);
        boolean blockQuic = EngineSettings.isQuicBlocked(this);
        int configureResult = NativeZapretEngine.configure(this, profile.getNativeId(), blockQuic);
        if (configureResult != 0) {
            throw new IllegalStateException("Native engine configuration failed: " + configureResult);
        }
        if (profile == StrategyProfile.CUSTOM) {
            int splitPosition = EngineSettings.getCustomStrategySplitPosition(this);
            long delayMs = EngineSettings.getCustomStrategyDelayMs(this);
            int customResult = NativeZapretEngine.configureCustomStrategy(splitPosition, delayMs);
            if (customResult != 0) {
                throw new IllegalStateException("Custom strategy configuration failed: " + customResult);
            }
        }
        if (profile == StrategyProfile.FLOWSEAL) {
            int fakeTtlResult = NativeZapretEngine.configureFakeTtl(EngineSettings.getFakeTtl(this));
            if (fakeTtlResult != 0) {
                throw new IllegalStateException("Fake-TTL configuration failed: " + fakeTtlResult);
            }
        }
        int hostlistResult = NativeZapretEngine.configureHostlist(
            EngineSettings.getHostlistDomains(this),
            EngineSettings.isHostlistOnly(this)
        );
        if (hostlistResult != 0) {
            throw new IllegalStateException("Hostlist configuration failed: " + hostlistResult);
        }
    }

    /**
     * Temporarily switches the live native engine to `candidate` for auto-testing,
     * bypassing hostlist targeting entirely so results reflect the strategy
     * itself rather than whether the test domains happen to be listed. Callers
     * must call {@link #applyConfiguredStrategy()} afterward to restore the
     * user's real settings.
     */
    void applyProfileForTesting(StrategyProfile candidate) {
        boolean blockQuic = EngineSettings.isQuicBlocked(this);
        NativeZapretEngine.configure(this, candidate.getNativeId(), blockQuic);
        if (candidate == StrategyProfile.FLOWSEAL) {
            NativeZapretEngine.configureFakeTtl(EngineSettings.getFakeTtl(this));
        }
        NativeZapretEngine.configureHostlist("", false);
    }

    public boolean protectSocket(int socketFd) {
        boolean protectedSocket = protect(socketFd);
        if (protectedSocket) {
            Log.i(TAG, "Protected outbound socket fd=" + socketFd);
        } else {
            Log.e(TAG, "Failed to protect outbound socket fd=" + socketFd);
        }
        return protectedSocket;
    }

    private void configureAppRouting(Builder builder) {
        AppRoutingSettings.Snapshot routing = AppRoutingSettings.load(this);
        if (!routing.isSelectedOnly()) {
            try {
                builder.addDisallowedApplication(getPackageName());
            } catch (android.content.pm.PackageManager.NameNotFoundException error) {
                throw new IllegalStateException("VPN package is not installed", error);
            }
            Log.i(TAG, "Routing all apps except the VPN process");
            return;
        }

        int addedPackages = 0;
        for (String packageName : routing.getPackages()) {
            try {
                builder.addAllowedApplication(packageName);
                addedPackages += 1;
            } catch (android.content.pm.PackageManager.NameNotFoundException error) {
                Log.w(TAG, "Ignoring uninstalled routed app: " + packageName);
            }
        }
        if (addedPackages == 0) {
            throw new IllegalStateException("Selected-app routing requires at least one installed app");
        }
        Log.i(TAG, "Routing " + addedPackages + " selected app(s)");
    }

    private synchronized void stopVpn() {
        VpnState current = state.get();
        if (current == VpnState.STOPPED || current == VpnState.STOPPING) {
            return;
        }

        Log.i(TAG, "Stopping VPN");
        state.set(VpnState.STOPPING);
        if (runningInstance == this) {
            runningInstance = null;
        }
        unregisterNetworkCallback();
        if (fallbackController != null) {
            fallbackController.stop();
            fallbackController = null;
        }
        if (tunBridge != null) {
            tunBridge.stop();
            tunBridge = null;
        }
        NativeZapretEngine.stop();
        if (tun != null) {
            try {
                tun.close();
            } catch (IOException closeError) {
                Log.w(TAG, "Failed to close TUN", closeError);
            }
            tun = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        state.set(VpnState.STOPPED);
        postStoppedNotification();
        stopSelf();
        Log.i(TAG, "VPN stopped");
    }

    private void postStoppedNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(false, null));
        }
    }

    private void onStrategyFallback(StrategyProfile newProfile) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            String text = getString(R.string.notification_text_fallback, getString(newProfile.getLabelResource()));
            manager.notify(NOTIFICATION_ID, buildNotification(true, text));
        }
    }

    private void registerNetworkCallback() {
        if (networkCallback != null) {
            return;
        }
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        if (manager == null) {
            return;
        }
        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.i(TAG, "Network available: " + network);
            }

            @Override
            public void onLost(Network network) {
                Log.w(TAG, "Network lost: " + network);
            }
        };
        manager.registerNetworkCallback(request, networkCallback);
    }

    private void unregisterNetworkCallback() {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        if (manager != null && networkCallback != null) {
            manager.unregisterNetworkCallback(networkCallback);
        }
        networkCallback = null;
    }

    private Notification buildNotification(boolean running, String overrideText) {
        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE
        );

        String text = overrideText != null
            ? overrideText
            : getString(running ? R.string.notification_text : R.string.notification_text_stopped);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(running ? R.string.notification_title : R.string.notification_title_stopped))
            .setContentText(text)
            .setContentIntent(contentPendingIntent)
            .setOngoing(running);

        if (running) {
            builder.addAction(buildNotificationAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_stop),
                ACTION_STOP,
                1
            ));
        } else {
            builder.addAction(buildNotificationAction(
                android.R.drawable.ic_media_play,
                getString(R.string.notification_action_start),
                ACTION_START,
                2
            ));
        }
        return builder.build();
    }

    private Notification.Action buildNotificationAction(int iconResource, CharSequence label, String action, int requestCode) {
        Intent intent = new Intent(this, ZapretVpnService.class).setAction(action);
        PendingIntent pendingIntent = PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        android.graphics.drawable.Icon icon = android.graphics.drawable.Icon.createWithResource(this, iconResource);
        return new Notification.Action.Builder(icon, label, pendingIntent).build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
