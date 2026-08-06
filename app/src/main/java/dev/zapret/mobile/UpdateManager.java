package dev.zapret.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Checks GitHub Releases for a newer build and opens the release page in the
 * browser for the user to download and install.
 *
 * Deliberately does NOT download the APK or launch the installer from inside
 * this app: a VPN app that also silently fetches and installs its own APKs
 * (REQUEST_INSTALL_PACKAGES + a self-hosted content:// APK provider) matches
 * the exact heuristic Google Play Protect uses for "dropper" malware, and
 * that combination is what was getting this app's sideloaded builds flagged
 * as unsafe. Routing the actual download through the browser's normal
 * download-then-install flow avoids that pattern; changing the signing
 * certificate alone would not have fixed it.
 */
final class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String PREFERENCES = "update_manager";
    private static final String KEY_LAST_CHECK_MS = "last_check_ms";
    private static final long AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final String RELEASES_API_URL =
        "https://api.github.com/repos/radik097/zapret-mobile/releases/latest";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UpdateManager() {
    }

    static String currentVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException error) {
            return "unknown";
        }
    }

    static void checkOnLaunch(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L);
        long now = System.currentTimeMillis();
        if (now - lastCheck < AUTO_CHECK_INTERVAL_MS) {
            return;
        }
        prefs.edit().putLong(KEY_LAST_CHECK_MS, now).apply();
        performCheck(activity, false);
    }

    static void checkManually(Activity activity) {
        Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
        performCheck(activity, true);
    }

    private static void performCheck(Activity activity, boolean interactive) {
        EXECUTOR.execute(() -> {
            try {
                String json = downloadText(RELEASES_API_URL);
                JSONObject release = new JSONObject(json);
                String tagName = release.optString("tag_name", "");
                String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                String releasePageUrl = release.optString("html_url", null);
                String currentVersion = currentVersionName(activity);
                if (!latestVersion.isEmpty() && releasePageUrl != null && isNewer(latestVersion, currentVersion)) {
                    String recommendedAsset = findRecommendedAssetName(release);
                    postToMain(activity, () -> showUpdateDialog(activity, latestVersion, releasePageUrl, recommendedAsset));
                } else if (interactive) {
                    postToMain(activity, () ->
                        Toast.makeText(activity, R.string.update_up_to_date, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception error) {
                Log.w(TAG, "Update check failed", error);
                if (interactive) {
                    postToMain(activity, () ->
                        Toast.makeText(activity, R.string.update_check_error, Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    /**
     * Releases publish one APK per ABI (zapret-mobile-<version>-<abi>.apk) plus
     * a "-universal.apk" fallback. Used only to tell the user which filename to
     * tap on the release page -- the download itself always goes through the
     * browser, never through this app.
     */
    private static String findRecommendedAssetName(JSONObject release) throws JSONException {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) {
            return null;
        }
        for (String abi : android.os.Build.SUPPORTED_ABIS) {
            String match = findAssetNameEndingWith(assets, "-" + abi + ".apk");
            if (match != null) {
                return match;
            }
        }
        return findAssetNameEndingWith(assets, "-universal.apk");
    }

    private static String findAssetNameEndingWith(JSONArray assets, String suffix) throws JSONException {
        for (int index = 0; index < assets.length(); index += 1) {
            JSONObject asset = assets.getJSONObject(index);
            String name = asset.optString("name", "");
            if (name.endsWith(suffix)) {
                return name;
            }
        }
        return null;
    }

    private static boolean isNewer(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");
        int length = Math.max(latestParts.length, currentParts.length);
        for (int index = 0; index < length; index += 1) {
            int latestPart = parsePart(latestParts, index);
            int currentPart = parsePart(currentParts, index);
            if (latestPart != currentPart) {
                return latestPart > currentPart;
            }
        }
        return false;
    }

    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static void showUpdateDialog(Activity activity, String latestVersion, String releasePageUrl, String recommendedAsset) {
        if (activity.isFinishing()) {
            return;
        }
        String message = recommendedAsset != null
            ? activity.getString(R.string.update_available_message_with_asset, latestVersion, recommendedAsset)
            : activity.getString(R.string.update_available_message, latestVersion);
        new AlertDialog.Builder(activity)
            .setTitle(R.string.update_available_title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.update_open_release_page, (dialog, which) ->
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(releasePageUrl))))
            .show();
    }

    private static String downloadText(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "ZapretMobile-UpdateCheck");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unexpected HTTP status " + status + " from " + url);
            }
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                while ((read = input.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                return buffer.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void postToMain(Activity activity, Runnable action) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!activity.isFinishing()) {
                action.run();
            }
        });
    }
}
