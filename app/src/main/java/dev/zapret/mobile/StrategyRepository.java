package dev.zapret.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches downloadable "strategy pack" definitions (raw split-position/delay
 * parameters for the CUSTOM native profile) from a JSON URL, with a local
 * cache so the last known packs remain selectable while offline.
 */
final class StrategyRepository {
    interface Callback {
        void onSuccess(List<StrategyPack> packs);

        void onError(Exception error);
    }

    private static final String TAG = "StrategyRepository";
    private static final String PREFERENCES = "strategy_packs";
    private static final String KEY_CACHED_JSON = "cached_json";
    private static final String KEY_SOURCE_URL = "source_url";
    private static final String DEFAULT_SOURCE_URL =
        "https://raw.githubusercontent.com/radik097/zapret-mobile/main/strategy-packs.json";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_SPLIT_POSITION = 16 * 1024;
    private static final long MAX_DELAY_MS = 5_000L;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    StrategyRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    String getSourceUrl() {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_SOURCE_URL, DEFAULT_SOURCE_URL);
    }

    void setSourceUrl(String url) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOURCE_URL, url)
            .apply();
    }

    List<StrategyPack> getCachedPacks() {
        String cachedJson = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_JSON, null);
        if (cachedJson == null) {
            return new ArrayList<>();
        }
        try {
            return parsePacks(cachedJson);
        } catch (JSONException error) {
            Log.w(TAG, "Cached strategy pack JSON is corrupt", error);
            return new ArrayList<>();
        }
    }

    void fetchAvailablePacks(Callback callback) {
        String sourceUrl = getSourceUrl();
        executor.execute(() -> {
            try {
                String json = downloadJson(sourceUrl);
                List<StrategyPack> packs = parsePacks(json);
                cacheJson(json);
                postSuccess(callback, packs);
            } catch (Exception error) {
                Log.w(TAG, "Failed to download strategy packs from " + sourceUrl, error);
                List<StrategyPack> cached = getCachedPacks();
                if (!cached.isEmpty()) {
                    postSuccess(callback, cached);
                } else {
                    postError(callback, error);
                }
            }
        });
    }

    void selectPack(Context anyContext, StrategyPack pack) {
        EngineSettings.setCustomStrategy(anyContext, pack.name, pack.splitPosition, pack.delayMs);
        EngineSettings.setStrategyProfile(anyContext, StrategyProfile.CUSTOM);
    }

    private void cacheJson(String json) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CACHED_JSON, json)
            .apply();
    }

    private static String downloadJson(String sourceUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unexpected HTTP status " + status + " from " + sourceUrl);
            }
            try (InputStream input = connection.getInputStream()) {
                return readAll(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private static List<StrategyPack> parsePacks(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray entries = root.getJSONArray("packs");
        List<StrategyPack> packs = new ArrayList<>();
        for (int index = 0; index < entries.length(); index += 1) {
            JSONObject entry = entries.getJSONObject(index);
            String id = entry.getString("id");
            String name = entry.optString("name", id);
            int splitPosition = entry.getInt("splitPosition");
            long delayMs = entry.optLong("delayMs", 0L);
            if (splitPosition < 1 || splitPosition > MAX_SPLIT_POSITION || delayMs < 0 || delayMs > MAX_DELAY_MS) {
                Log.w(TAG, "Skipping out-of-range strategy pack: " + id);
                continue;
            }
            packs.add(new StrategyPack(id, name, splitPosition, delayMs));
        }
        return packs;
    }

    private void postSuccess(Callback callback, List<StrategyPack> packs) {
        mainHandler.post(() -> callback.onSuccess(packs));
    }

    private void postError(Callback callback, Exception error) {
        mainHandler.post(() -> callback.onError(error));
    }
}
