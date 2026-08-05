package dev.zapret.mobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal single-purpose content provider that exposes the last downloaded
 * update APK (cacheDir/update.apk) as a content:// URI, so the system
 * installer can read it without a FileUriExposedException. Avoids pulling in
 * androidx.core.content.FileProvider, since this project has no AndroidX
 * dependency otherwise.
 */
public final class ApkFileProvider extends ContentProvider {
    static final String AUTHORITY = "dev.zapret.mobile.apkprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = new File(attachedContext().getCacheDir(), uri.getLastPathSegment());
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    private android.content.Context attachedContext() {
        android.content.Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("ApkFileProvider has no attached context");
        }
        return context;
    }
}
