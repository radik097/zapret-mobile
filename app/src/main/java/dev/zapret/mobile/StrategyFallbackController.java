package dev.zapret.mobile;

import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches the native engine's per-connection failure counter while the VPN is
 * running and automatically escalates through Compatible -> Balanced ->
 * Aggressive -> Zaptret2 when the current profile keeps failing. A downloaded
 * CUSTOM strategy is left untouched (it is not part of the escalation order),
 * since that is an explicit user choice rather than a built-in fallback step.
 */
final class StrategyFallbackController {
    private static final String TAG = "StrategyFallback";
    private static final long POLL_INTERVAL_MS = 15_000L;
    private static final int FAILURE_THRESHOLD = 3;
    private static final StrategyProfile[] ESCALATION_ORDER = {
        StrategyProfile.COMPATIBLE,
        StrategyProfile.BALANCED,
        StrategyProfile.AGGRESSIVE,
        StrategyProfile.ZAPTRET2
    };

    interface Listener {
        void onProfileFallback(StrategyProfile newProfile);
    }

    private final ZapretVpnService service;
    private final boolean blockQuic;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile StrategyProfile currentProfile;
    private volatile Listener listener;
    private Thread worker;

    StrategyFallbackController(ZapretVpnService service, StrategyProfile startingProfile, boolean blockQuic) {
        this.service = service;
        this.blockQuic = blockQuic;
        this.currentProfile = startingProfile;
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    StrategyProfile getCurrentProfile() {
        return currentProfile;
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker = new Thread(this::run, "zapret-fallback");
        worker.setDaemon(true);
        worker.start();
    }

    void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    private void run() {
        while (running.get()) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException interrupted) {
                break;
            }
            if (!running.get()) {
                break;
            }

            int failures = NativeZapretEngine.pollFailureCount();
            if (failures < FAILURE_THRESHOLD) {
                continue;
            }

            StrategyProfile next = nextProfile(currentProfile);
            if (next == currentProfile) {
                Log.w(TAG, "All strategy profiles exhausted (" + failures + " failures); staying on " + currentProfile);
                continue;
            }

            Log.w(TAG, "Falling back from " + currentProfile + " to " + next + " after " + failures + " connection failures");
            currentProfile = next;
            NativeZapretEngine.configure(service, next.getNativeId(), blockQuic);
            Listener currentListener = listener;
            if (currentListener != null) {
                currentListener.onProfileFallback(next);
            }
        }
    }

    private static StrategyProfile nextProfile(StrategyProfile profile) {
        for (int index = 0; index < ESCALATION_ORDER.length; index += 1) {
            if (ESCALATION_ORDER[index] == profile) {
                int nextIndex = index + 1;
                return nextIndex < ESCALATION_ORDER.length ? ESCALATION_ORDER[nextIndex] : profile;
            }
        }
        return profile;
    }
}
