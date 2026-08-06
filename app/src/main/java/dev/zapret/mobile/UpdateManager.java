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
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks GitHub Releases for a newer build, then downloads and launches the system installer. */
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
                String downloadUrl = findApkAssetUrl(release);
                String currentVersion = currentVersionName(activity);
                if (!latestVersion.isEmpty() && downloadUrl != null && isNewer(latestVersion, currentVersion)) {
                    postToMain(activity, () -> showUpdateDialog(activity, latestVersion, downloadUrl));
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
     * a "-universal.apk" fallback. Picks the asset matching this device's most
     * preferred supported ABI first, then universal, then any .apk as a last
     * resort (older/manually-created releases without ABI splits).
     */
    private static String findApkAssetUrl(JSONObject release) throws JSONException {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) {
            return null;
        }
        for (String abi : android.os.Build.SUPPORTED_ABIS) {
            String match = findAssetUrlEndingWith(assets, "-" + abi + ".apk");
            if (match != null) {
                return match;
            }
        }
        String universal = findAssetUrlEndingWith(assets, "-universal.apk");
        if (universal != null) {
            return universal;
        }
        for (int index = 0; index < assets.length(); index += 1) {
            JSONObject asset = assets.getJSONObject(index);
            String name = asset.optString("name", "");
            if (name.endsWith(".apk")) {
                return asset.optString("browser_download_url", null);
            }
        }
        return null;
    }

    private static String findAssetUrlEndingWith(JSONArray assets, String suffix) throws JSONException {
        for (int index = 0; index < assets.length(); index += 1) {
            JSONObject asset = assets.getJSONObject(index);
            String name = asset.optString("name", "");
            if (name.endsWith(suffix)) {
                return asset.optString("browser_download_url", null);
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

    private static void showUpdateDialog(Activity activity, String latestVersion, String downloadUrl) {
        if (activity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(activity)
            .setTitle(R.string.update_available_title)
            .setMessage(activity.getString(R.string.update_available_message, latestVersion))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.update_download_install, (dialog, which) -> downloadAndInstall(activity, downloadUrl))
            .show();
    }

    private static void downloadAndInstall(Activity activity, String downloadUrl) {
        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show();
        EXECUTOR.execute(() -> {
            try {
                File apkFile = new File(activity.getCacheDir(), "update.apk");
                downloadToFile(downloadUrl, apkFile);
                postToMain(activity, () -> launchInstall(activity, apkFile));
            } catch (Exception error) {
                Log.w(TAG, "Update download failed", error);
                postToMain(activity, () ->
                    Toast.makeText(activity, R.string.update_download_error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void launchInstall(Activity activity, File apkFile) {
        PackageManager packageManager = activity.getPackageManager();
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_grant_install_permission, Toast.LENGTH_LONG).show();
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            settingsIntent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settingsIntent);
            return;
        }
        Uri apkUri = new Uri.Builder()
            .scheme("content")
            .authority(ApkFileProvider.AUTHORITY)
            .path(apkFile.getName())
            .build();
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(installIntent);
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

    private static void downloadToFile(String url, File destination) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "ZapretMobile-UpdateCheck");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unexpected HTTP status " + status + " from " + url);
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(destination)) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = input.read(chunk)) != -1) {
                    output.write(chunk, 0, read);
                }
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
