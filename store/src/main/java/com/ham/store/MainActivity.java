package com.ham.store;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    public static final String[] CATALOG_URLS = {
            "https://raw.githubusercontent.com/i77391166-netizen/apps/main/catalog.json",
            "https://cdn.jsdelivr.net/gh/i77391166-netizen/apps@main/catalog.json",
            "https://raw.githack.com/i77391166-netizen/apps/main/catalog.json"
    };

    public static volatile String LAST_ERR = "";

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
        wv.setBackgroundColor(0xFF0A0A0F);
        wv.setWebViewClient(new WebViewClient());
        wv.setWebChromeClient(new WebChromeClient());
        wv.addJavascriptInterface(new Bridge(), "Store");
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

    private static String jsStr(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append(' ');
            else if (c < ' ') b.append(' ');
            else b.append(c);
        }
        return b.toString();
    }

    private void toast(String msg) {
        post("window.mToast&&window.mToast('" + jsStr(msg) + "')");
    }

    private String httpText(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "ham-store");
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) {
                LAST_ERR = "HTTP " + code + " — " + url;
                return "";
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            String m = t.getMessage();
            LAST_ERR = t.getClass().getSimpleName() + (m == null ? "" : (": " + m)) + " — " + url;
            return "";
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable t) {
                }
            }
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("store", MODE_PRIVATE);
    }

    private String catalog() {
        StringBuilder err = new StringBuilder();
        for (String u : CATALOG_URLS) {
            LAST_ERR = "";
            String fresh = httpText(u);
            if (fresh != null && fresh.trim().startsWith("{")) {
                LAST_ERR = "";
                try {
                    prefs().edit().putString("cat", fresh).apply();
                } catch (Throwable t) {
                }
                return fresh;
            }
            String e = LAST_ERR;
            if (e == null || e.length() == 0) e = "пустой ответ — " + u;
            if (err.length() > 0) err.append("\n");
            err.append(e);
        }
        LAST_ERR = err.toString();
        String cached = prefs().getString("cat", "");
        if (cached == null) cached = "";
        return cached;
    }

    private void launchInstall(File f) {
        try {
            Uri u = ApkProvider.uriFor(this, f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, ApkProvider.TYPE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast("Не смог открыть установщик: " + t.getClass().getSimpleName());
        }
    }

    private void download(final String url, final String fileName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                File out = null;
                try {
                    post("window.mProg&&window.mProg(0)");
                    out = new File(getCacheDir(), fileName);
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(40000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", "ham-store");
                    int code = c.getResponseCode();
                    if (code != 200) {
                        toast("Скачивание не удалось, HTTP " + code);
                        post("window.mProg&&window.mProg(-1)");
                        return;
                    }
                    long total = c.getContentLength();
                    InputStream in = c.getInputStream();
                    FileOutputStream fo = new FileOutputStream(out);
                    byte[] buf = new byte[65536];
                    long got = 0;
                    int last = -1;
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        fo.write(buf, 0, r);
                        got += r;
                        if (total > 0) {
                            int pct = (int) (got * 100 / total);
                            if (pct != last && pct % 4 == 0) {
                                last = pct;
                                post("window.mProg&&window.mProg(" + pct + ")");
                            }
                        }
                    }
                    fo.close();
                    in.close();
                    if (got < 1024) {
                        toast("Файл пустой, скачивание сорвалось");
                        post("window.mProg&&window.mProg(-1)");
                        return;
                    }
                    post("window.mProg&&window.mProg(100)");
                    launchInstall(out);
                } catch (Throwable t) {
                    toast("Ошибка загрузки: " + t.getClass().getSimpleName());
                    post("window.mProg&&window.mProg(-1)");
                } finally {
                    if (c != null) {
                        try {
                            c.disconnect();
                        } catch (Throwable t) {
                        }
                    }
                }
            }
        }).start();
    }

    public class Bridge {

        @JavascriptInterface
        public String catalog() {
            return catalog();
        }

        @JavascriptInterface
        public String lastError() {
            return LAST_ERR == null ? "" : LAST_ERR;
        }

        @JavascriptInterface
        public String info(String pkg) {
            JSONObject o = new JSONObject();
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(pkg, 0);
                o.put("i", true);
                o.put("c", pi.versionCode);
                o.put("v", pi.versionName == null ? "" : pi.versionName);
            } catch (Throwable t) {
                try {
                    o.put("i", false);
                } catch (Throwable t2) {
                }
            }
            return o.toString();
        }

        @JavascriptInterface
        public String self() {
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                return pi.versionCode + "|" + (pi.versionName == null ? "" : pi.versionName);
            } catch (Throwable t) {
                return "0|";
            }
        }

        @JavascriptInterface
        public void install(String url, String fileName) {
            if (url == null || url.length() == 0) return;
            String fn = fileName == null || fileName.length() == 0 ? "app.apk" : fileName;
            fn = fn.replaceAll("[^A-Za-z0-9._-]", "_");
            if (!fn.endsWith(".apk")) fn = fn + ".apk";
            download(url, fn);
        }

        @JavascriptInterface
        public void open(String pkg) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i == null) {
                    toast("Не нашёл, чем открыть " + pkg);
                    return;
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Throwable t) {
                toast("Не открылось: " + t.getClass().getSimpleName());
            }
        }

        @JavascriptInterface
        public void uninstall(String pkg) {
            try {
                Intent i = new Intent(Intent.ACTION_DELETE);
                i.setData(Uri.parse("package:" + pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Throwable t) {
                toast("Не удалось: " + t.getClass().getSimpleName());
            }
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
