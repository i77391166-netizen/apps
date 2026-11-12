package com.ham.ai;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends Activity {

    public static MainActivity I;
    private WebView wv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        I = this;
        getWindow().setStatusBarColor(0xFF0B0B12);
        getWindow().setNavigationBarColor(0xFF0B0B12);

        wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        wv.setBackgroundColor(0xFF0B0B12);
        wv.setWebViewClient(new WebViewClient());
        wv.setWebChromeClient(new WebChromeClient());
        wv.addJavascriptInterface(new Bridge(), "AI");
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

    private SharedPreferences prefs() {
        return getSharedPreferences("ai", MODE_PRIVATE);
    }

    private static boolean empty(String s) {
        return s == null || s.trim().length() == 0;
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        s = s.replace("\n", " ").replace("\r", " ");
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static String http(String urlStr, String body, String[] headers, int readTimeout) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod(body == null ? "GET" : "POST");
            c.setConnectTimeout(20000);
            c.setReadTimeout(readTimeout);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "ham-ai");
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
            }
            if (headers != null) {
                for (int i = 0; i + 1 < headers.length; i += 2) {
                    c.setRequestProperty(headers[i], headers[i + 1]);
                }
            }
            if (body != null) {
                OutputStream o = c.getOutputStream();
                o.write(body.getBytes("UTF-8"));
                o.close();
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            if (in != null) {
                byte[] buf = new byte[16384];
                int r;
                while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
                in.close();
            }
            String text = new String(bo.toByteArray(), "UTF-8");
            if (code >= 400) throw new Exception("HTTP " + code + ": " + cut(text, 200));
            return text;
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable t) {
                }
            }
        }
    }

    private void reply(String reqId, boolean ok, String text) {
        post("window.mReply('" + reqId + "', " + ok + ", " + JSONObject.quote(text == null ? "" : text) + ")");
    }

    private void doAsk(String reqId, String provider, String model, String key, String system, String msgsJson) {
        try {
            JSONArray msgs = new JSONArray(msgsJson);
            String text;
            if ("gemini".equals(provider)) {
                String m = empty(model) ? "gemini-2.0-flash" : model.trim();
                JSONObject body = new JSONObject();
                if (!empty(system)) {
                    body.put("systemInstruction", new JSONObject().put("parts",
                            new JSONArray().put(new JSONObject().put("text", system))));
                }
                JSONArray contents = new JSONArray();
                for (int i = 0; i < msgs.length(); i++) {
                    JSONObject m0 = msgs.getJSONObject(i);
                    String role = "assistant".equals(m0.optString("role")) ? "model" : "user";
                    contents.put(new JSONObject().put("role", role).put("parts",
                            new JSONArray().put(new JSONObject().put("text", m0.optString("content")))));
                }
                body.put("contents", contents);
                String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                        + URLEncoder.encode(m, "UTF-8") + ":generateContent?key="
                        + URLEncoder.encode(key == null ? "" : key.trim(), "UTF-8");
                JSONObject o = new JSONObject(http(url, body.toString(), null, 120000));
                JSONArray parts = o.getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length(); i++) sb.append(parts.getJSONObject(i).optString("text"));
                text = sb.toString();
            } else {
                JSONObject body = new JSONObject();
                JSONArray arr = new JSONArray();
                if (!empty(system)) {
                    arr.put(new JSONObject().put("role", "system").put("content", system));
                }
                for (int i = 0; i < msgs.length(); i++) {
                    JSONObject m0 = msgs.getJSONObject(i);
                    arr.put(new JSONObject().put("role", m0.optString("role"))
                            .put("content", m0.optString("content")));
                }
                body.put("messages", arr);

                String url;
                String[] hdr = null;
                if ("groq".equals(provider)) {
                    if (empty(key)) throw new Exception("нужен ключ Groq");
                    url = "https://api.groq.com/openai/v1/chat/completions";
                    body.put("model", empty(model) ? "llama-3.3-70b-versatile" : model.trim());
                    hdr = new String[]{"Authorization", "Bearer " + key.trim()};
                } else if ("openrouter".equals(provider)) {
                    if (empty(key)) throw new Exception("нужен ключ OpenRouter");
                    url = "https://openrouter.ai/api/v1/chat/completions";
                    body.put("model", empty(model) ? "meta-llama/llama-3.3-70b-instruct:free" : model.trim());
                    hdr = new String[]{"Authorization", "Bearer " + key.trim(),
                            "HTTP-Referer", "https://github.com/i77391166-netizen",
                            "X-Title", "Ham AI"};
                } else {
                    url = "https://text.pollinations.ai/openai";
                    body.put("model", empty(model) ? "openai" : model.trim());
                    if (!empty(key)) hdr = new String[]{"Authorization", "Bearer " + key.trim()};
                }

                JSONObject o = new JSONObject(http(url, body.toString(), hdr, 120000));
                JSONObject msg = o.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
                text = msg.optString("content");
                if (empty(text)) text = msg.optString("reasoning");
            }
            if (text == null) text = "";
            text = text.trim();
            if (text.length() == 0) throw new Exception("модель вернула пустой ответ");
            reply(reqId, true, text);
        } catch (Throwable t) {
            String m = t.getMessage();
            reply(reqId, false, t.getClass().getSimpleName() + (m == null ? "" : ": " + m));
        }
    }

    private void doImage(String reqId, String prompt) {
        HttpURLConnection c = null;
        try {
            String q = URLEncoder.encode(prompt == null ? "" : prompt, "UTF-8").replace("+", "%20");
            String url = "https://image.pollinations.ai/prompt/" + q
                    + "?width=768&height=768&nologo=true&seed=" + (System.currentTimeMillis() % 1000000);
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(180000);
            c.setRequestProperty("User-Agent", "ham-ai");
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            in.close();
            byte[] bytes = bo.toByteArray();
            if (bytes.length < 4000) throw new Exception("пришло всего " + bytes.length + " байт");
            String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            post("window.mImage('" + reqId + "', 'data:image/jpeg;base64," + b64 + "')");
        } catch (Throwable t) {
            String m = t.getMessage();
            reply(reqId, false, "Картинка не вышла — " + t.getClass().getSimpleName()
                    + (m == null ? "" : ": " + m));
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable t) {
                }
            }
        }
    }

    public class Bridge {

        @JavascriptInterface
        public String get(String k) {
            String v = prefs().getString(k, "");
            return v == null ? "" : v;
        }

        @JavascriptInterface
        public void set(String k, String v) {
            prefs().edit().putString(k, v == null ? "" : v).apply();
        }

        @JavascriptInterface
        public void ask(final String reqId, final String provider, final String model,
                        final String key, final String system, final String msgs) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    doAsk(reqId, provider, model, key, system, msgs);
                }
            }).start();
        }

        @JavascriptInterface
        public void image(final String reqId, final String prompt) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    doImage(reqId, prompt);
                }
            }).start();
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
