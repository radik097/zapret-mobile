package dev.zapret.mobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Serves a single exported copy of the diagnostics log so it can be attached
 * to a share intent as a real file. A content provider is required because
 * handing out a {@code file://} URI has thrown {@code FileUriExposedException}
 * since API 24, and this app's minSdk is 26.
 *
 * Written by hand rather than pulling in androidx's FileProvider: the app has
 * no library dependencies at all, and this needs to do exactly two things --
 * report a name and size, and open one file read-only.
 *
 * The provider is not exported. Read access is granted per-share, for one URI
 * at a time, by the {@code FLAG_GRANT_READ_URI_PERMISSION} on the intent, and
 * it only ever resolves paths inside its own cache subdirectory.
 */
public final class LogFileProvider extends ContentProvider {
    private static final String AUTHORITY = "dev.zapret.mobile.logs";
    private static final String SHARE_DIR = "shared-logs";
    private static final String MIME_TYPE = "text/plain";

    /**
     * Writes `content` to a fresh, dated file and returns a URI for it.
     * Returns null if the copy could not be written, so callers can report
     * the failure rather than launching a chooser for a file that isn't there.
     */
    static Uri exportLog(Context context, String content) {
        File dir = shareDir(context);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        // One file, overwritten each time: the log is a snapshot, and leaving
        // copies of previous shares in the cache serves no one.
        File file = new File(dir, "zapret-log-" + today() + ".log");
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            return null;
        }
        return new Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .path(file.getName())
            .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return MIME_TYPE;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("This provider is read-only: " + uri);
        }
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException error) {
            return null;
        }
        // Share targets ask for these two columns to label and size the
        // attachment; anything else in the projection is not ours to answer.
        String[] columns = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        cursor.addRow(new Object[] {file.getName(), file.length()});
        return cursor;
    }

    /**
     * Maps a URI to a file, refusing anything that resolves outside the share
     * directory -- a URI arrives from another process and cannot be trusted to
     * be a bare filename just because that is what we handed out.
     */
    private File resolve(Uri uri) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("Provider has no context: " + uri);
        }
        String name = uri.getLastPathSegment();
        if (name == null) {
            throw new FileNotFoundException("No file in URI: " + uri);
        }
        File dir = shareDir(context);
        File file = new File(dir, name);
        try {
            if (!file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
                throw new FileNotFoundException("Outside the shared log directory: " + uri);
            }
        } catch (IOException error) {
            throw new FileNotFoundException("Could not resolve: " + uri);
        }
        if (!file.exists()) {
            throw new FileNotFoundException("No such shared log: " + uri);
        }
        return file;
    }

    private static File shareDir(Context context) {
        return new File(context.getApplicationContext().getCacheDir(), SHARE_DIR);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }
}
