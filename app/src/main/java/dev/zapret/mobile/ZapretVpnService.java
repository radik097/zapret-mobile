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
    private static final int SOCKS_PORT = 1080;

    private final AtomicReference<VpnState> state = new AtomicReference<>(VpnState.STOPPED);
    private ParcelFileDescriptor tun;
    private Tun2SocksBridge tunBridge;
    private ConnectivityManager.NetworkCallback networkCallback;

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
        startForeground(NOTIFICATION_ID, buildNotification());

        try {
            StrategyProfile profile = EngineSettings.getStrategyProfile(this);
            boolean blockQuic = EngineSettings.isQuicBlocked(this);
            int configureResult = NativeZapretEngine.configure(this, profile.getNativeId(), blockQuic);
            if (configureResult != 0) {
                throw new IllegalStateException("Native engine configuration failed: " + configureResult);
            }
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
            state.set(VpnState.RUNNING);
            Log.i(TAG, "Strategy profile: " + profile.name().toLowerCase(java.util.Locale.ROOT));
            Log.i(TAG, "QUIC/UDP 443 policy: " + (blockQuic ? "blocked" : "allowed"));
            Log.i(TAG, "VPN started with TUN-to-SOCKS bridge and local SOCKS on 127.0.0.1:" + SOCKS_PORT);
        } catch (Exception error) {
            state.set(VpnState.ERROR);
            Log.e(TAG, "Failed to start VPN", error);
            stopVpn();
        }
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
        unregisterNetworkCallback();
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
        stopSelf();
        Log.i(TAG, "VPN stopped");
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

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
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
