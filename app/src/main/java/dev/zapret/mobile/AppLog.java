package dev.zapret.mobile;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A small file-backed log, separate from the system's shared logcat buffer,
 * which on a busy device can rotate past anything relevant within seconds.
 * Keeps only today's entries: any log file whose name doesn't match the
 * current date is deleted the next time something is logged, so the log
 * never grows across days and never needs a manual "clear old logs" step.
 */
final class AppLog {
    private static final String LOG_DIR = "logs";
    private static final String FILE_PREFIX = "zapret-";
    private static final String FILE_SUFFIX = ".log";
    private static final String USER_ACTION_TAG = "UserAction";

    private AppLog() {
    }

    /**
     * Logs something the user explicitly did (tapped a button, changed a
     * switch, picked a spinner item) under one consistent tag, so the log
     * clearly distinguishes "the user pressed this" from automatic engine/
     * lifecycle events when reconstructing what happened in a session.
     */
    static void userAction(Context context, String description) {
        i(context, USER_ACTION_TAG, description);
    }

    static void i(Context context, String tag, String message) {
        Log.i(tag, message);
        write(context, "I", tag, message);
    }

    static void w(Context context, String tag, String message) {
        Log.w(tag, message);
        write(context, "W", tag, message);
    }

    static void e(Context context, String tag, String message, Throwable error) {
        Log.e(tag, message, error);
        write(context, "E", tag, error == null ? message : message + " " + error);
    }

    static synchronized String readToday(Context context) {
        File file = todayLogFile(context, false);
        if (!file.exists()) {
            return "";
        }
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        } catch (IOException error) {
            return "";
        }
    }

    static synchronized void clearToday(Context context) {
        todayLogFile(context, false).delete();
    }

    private static synchronized void write(Context context, String level, String tag, String message) {
        File file = todayLogFile(context, true);
        String line = timestamp() + " " + level + "/" + tag + ": " + message + "\n";
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // Logging must never throw for the caller; losing a line beats crashing.
        }
    }

    private static File todayLogFile(Context context, boolean pruneOtherDays) {
        File dir = new File(context.getApplicationContext().getFilesDir(), LOG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String currentName = FILE_PREFIX + today() + FILE_SUFFIX;
        if (pruneOtherDays) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.getName().equals(currentName)) {
                        file.delete();
                    }
                }
            }
        }
        return new File(dir, currentName);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static String timestamp() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
