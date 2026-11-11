package com.ham.music;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

public class MainActivity extends Activity {

    public static MainActivity I;
    private WebView wv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        I = this;
        getWindow().setStatusBarColor(0xFF0A0A0F);
        getWindow().setNavigationBarColor(0xFF0A0A0F);

        wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        wv.setBackgroundColor(0xFF0A0A0F);
        wv.setWebViewClient(new WebViewClient());
        wv.setWebChromeClient(new WebChromeClient());
        wv.addJavascriptInterface(new Bridge(), "Mus");
        setContentView(wv);
        wv.loadUrl("file:///android_asset/index.html");
    }

    public static void post(final String js) {
        final MainActivity a = I;
        if (a == null) return;
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (a.wv != null) a.wv.evaluateJavascript(js, null);
                } catch (Throwable t) {
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        post("window.mBack&&window.mBack()");
    }

    private String[] perms() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{Manifest.permission.READ_MEDIA_AUDIO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean hasPerm() {
        try {
            for (String p : perms()) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void askPerm() {
        if (hasPerm()) {
            post("window.mPerm&&window.mPerm('granted')");
            return;
        }
        try {
            requestPermissions(perms(), 7);
        } catch (Throwable t) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        post("window.mPerm&&window.mPerm('" + (hasPerm() ? "granted" : "denied") + "')");
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 8);
                }
            } catch (Throwable t) {
            }
        }
    }

    private static String clean(String s) {
        if (s == null) return "";
        if (s.equals("<unknown>")) return "";
        return s;
    }

    private String scanJson() {
        JSONArray arr = new JSONArray();
        if (!hasPerm()) return arr.toString();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] cols = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION
        };
        String sel = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.DURATION + ">8000";
        Cursor c = null;
        try {
            c = getContentResolver().query(base, cols, sel, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
            while (c != null && c.moveToNext()) {
                long id = c.getLong(0);
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("t", clean(c.getString(1)));
                o.put("a", clean(c.getString(2)));
                o.put("al", clean(c.getString(3)));
                o.put("d", c.getLong(4));
                o.put("u", ContentUris.withAppendedId(base, id).toString());
                arr.put(o);
            }
        } catch (Throwable t) {
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable t) {
                }
            }
        }
        return arr.toString();
    }

    private String artB64(String uriStr) {
        MediaMetadataRetriever r = null;
        try {
            if (uriStr == null || uriStr.length() == 0) return "";
            r = new MediaMetadataRetriever();
            r.setDataSource(this, Uri.parse(uriStr));
            byte[] pic = r.getEmbeddedPicture();
            if (pic == null || pic.length == 0) return "";
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            Bitmap bm = BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
            if (bm == null) return "";
            int w = bm.getWidth();
            int h = bm.getHeight();
            int max = Math.max(w, h);
            if (max > 640) {
                float k = 640f / max;
                Bitmap sc = Bitmap.createScaledBitmap(bm, Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)), true);
                if (sc != bm) bm = sc;
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 85, bo);
            return "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            return "";
        } finally {
            if (r != null) {
                try {
                    r.release();
                } catch (Throwable t) {
                }
            }
        }
    }

    public class Bridge {

        @JavascriptInterface
        public String perm() {
            return hasPerm() ? "granted" : "ask";
        }

        @JavascriptInterface
        public void requestPerm() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    askPerm();
                }
            });
        }

        @JavascriptInterface
        public String scan() {
            return scanJson();
        }

        @JavascriptInterface
        public String art(String uri) {
            return artB64(uri);
        }

        @JavascriptInterface
        public String state() {
            return MusicService.stateJson();
        }

        @JavascriptInterface
        public void play(String q, int i, int r) {
            MusicService.setQueue(MainActivity.this, q, i, r);
        }

        @JavascriptInterface
        public void toggle() {
            MusicService.cmd(MusicService.A_TOGGLE);
        }

        @JavascriptInterface
        public void next() {
            MusicService.cmd(MusicService.A_NEXT);
        }

        @JavascriptInterface
        public void prev() {
            MusicService.cmd(MusicService.A_PREV);
        }

        @JavascriptInterface
        public void seek(int ms) {
            MusicService.seekTo(ms);
        }

        @JavascriptInterface
        public void repeat(int m) {
            MusicService.setRepeat(m);
        }

        @JavascriptInterface
        public void stop() {
            MusicService.cmd(MusicService.A_STOP);
        }

        @JavascriptInterface
        public void exit() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
        }
    }
}
