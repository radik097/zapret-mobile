package dev.zapret.mobile;

import android.content.Context;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

/**
 * Posts the current day's diagnostics log to a Cloudflare Worker, which
 * forwards it to GitHub. The Worker holds the GitHub credential -- this app
 * never carries one, so a decompiled APK yields nothing but the Worker URL
 * and a shared secret that only grants the ability to file a log.
 *
 * Uploading is opt-in and off by default (see
 * {@link EngineSettings#isLogUploadEnabled}). Every attempt, including every
 * refusal to upload and every failure, is written to the log itself, so the
 * log is an honest record of whether it was sent anywhere.
 */
final class LogUploader {
    private static final String TAG = "LogUploader";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    /** GitHub rejects issue bodies over 65536 chars; stay well clear of it. */
    private static final int MAX_LOG_CHARS = 60_000;

    private LogUploader() {
    }

    /**
     * Uploads today's log if the user enabled it and configured an endpoint.
     * Returns immediately; the transfer runs on a background thread.
     */
    static void uploadTodayIfEnabled(Context context, String reason) {
        Context appContext = context.getApplicationContext();
        if (!EngineSettings.isLogUploadEnabled(appContext)) {
            return;
        }
        String url = EngineSettings.getLogUploadUrl(appContext);
        if (url.isEmpty()) {
            AppLog.w(appContext, TAG, "Upload is enabled but no endpoint URL is set; skipping");
            return;
        }
        if (!url.startsWith("https://")) {
            AppLog.w(appContext, TAG, "Refusing to upload over a non-HTTPS endpoint: " + url);
            return;
        }
        EXECUTOR.execute(() -> upload(appContext, url, reason));
    }

    private static void upload(Context context, String endpoint, String reason) {
        String log = AppLog.readToday(context);
        if (log.isEmpty()) {
            AppLog.w(context, TAG, "Nothing to upload: today's log is empty");
            return;
        }
        boolean truncated = log.length() > MAX_LOG_CHARS;
        if (truncated) {
            log = log.substring(log.length() - MAX_LOG_CHARS);
        }

        String payload;
        try {
            payload = new JSONObject()
                .put("version", AppLog.appVersion(context))
                .put("reason", reason)
                .put("truncated", truncated)
                .put("log", log)
                .toString();
        } catch (Exception error) {
            AppLog.e(context, TAG, "Could not build upload payload", error);
            return;
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            String token = EngineSettings.getLogUploadToken(context);
            if (!token.isEmpty()) {
                connection.setRequestProperty("X-Zapret-Token", token);
            }

            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                AppLog.i(context, TAG, "Uploaded today's log (" + body.length + " bytes"
                    + (truncated ? ", truncated to the most recent entries" : "") + ")");
            } else {
                AppLog.w(context, TAG, "Upload rejected by the endpoint, HTTP " + status);
            }
        } catch (IOException error) {
            AppLog.e(context, TAG, "Upload failed", error);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
