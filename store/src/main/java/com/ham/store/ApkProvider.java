package com.ham.store;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Отдаёт установщику скачанный APK по content:// — начиная с Android 7 file:// запрещён.
 */
public class ApkProvider extends ContentProvider {

    public static final String AUTH = "com.ham.store.apk";
    public static final String TYPE = "application/vnd.android.package-archive";

    public static Uri uriFor(Context c, File f) {
        return new Uri.Builder().scheme("content").authority(AUTH).appendPath(f.getName()).build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.indexOf('/') >= 0) throw new FileNotFoundException("bad name");
        File f = new File(getContext().getCacheDir(), name);
        if (!f.exists()) throw new FileNotFoundException(name);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return TYPE;
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) {
        return 0;
    }
}
