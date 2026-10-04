package io.github.nanthufs.tonnage;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * Hosts the Tonnage web app (bundled under assets/www) in a full-screen WebView.
 *
 * Assets are served from https://appassets.androidplatform.net so the page runs in a
 * normal secure origin. A small JavaScript bridge (window.TonnageAndroid) gives the page
 * durable file storage, the system save dialog for backups, and keep-screen-on.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/index.html";
    private static final int REQUEST_PICK_FILE = 1;
    private static final int REQUEST_SAVE_FILE = 2;
    private static final Pattern KEY = Pattern.compile("[a-z0-9.]{1,64}");

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            web = new WebView(this);
        } catch (RuntimeException e) {
            // The WebView provider can be missing or mid-update on some devices.
            TextView message = new TextView(this);
            message.setText("Tonnage needs Android System WebView. Update it from the Play Store, then open Tonnage again.");
            message.setTextColor(0xFFFFFFFF);
            message.setBackgroundColor(0xFF000000);
            message.setGravity(Gravity.CENTER);
            message.setPadding(48, 48, 48, 48);
            setContentView(message);
            return;
        }
        web.setBackgroundColor(0xFF000000);
        setContentView(web);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        web.addJavascriptInterface(new Bridge(), "TonnageAndroid");
        web.setWebViewClient(new AssetClient());
        web.setWebChromeClient(new ChromeClient());
        web.loadUrl(START_URL);
    }

    @Override
    public void onBackPressed() {
        // The page keeps its own history entries for open sheets and screens.
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
        } else if (requestCode == REQUEST_SAVE_FILE) {
            String content = pendingSave;
            pendingSave = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && content != null) {
                writeDocument(data.getData(), content);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void writeDocument(Uri uri, String content) {
        OutputStream out = null;
        try {
            out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new IOException("No output stream for " + uri);
            out.write(content.getBytes(StandardCharsets.UTF_8));
            toast("Backup saved");
        } catch (IOException | SecurityException e) {
            toast("The backup could not be saved");
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException ignored) { }
            }
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static String mimeType(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".js")) return "text/javascript";
        if (p.endsWith(".json") || p.endsWith(".webmanifest")) return "application/json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    /** Serves bundled files for the app origin and sends every other link to the browser. */
    private final class AssetClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (!HOST.equals(url.getHost())) return null;
            String path = url.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) path = "/index.html";
            if (path.contains("..")) return notFound();
            try {
                InputStream in = getAssets().open("www" + path);
                String mime = mimeType(path);
                return new WebResourceResponse(mime, mime.startsWith("text/") || mime.endsWith("json") || mime.endsWith("xml") ? "UTF-8" : null, in);
            } catch (IOException e) {
                return notFound();
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (HOST.equals(uri.getHost())) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                // Nothing can open it; stay on the current page.
            }
            return true;
        }

        private WebResourceResponse notFound() {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                    new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
        }
    }

    /** Opens the system picker for <input type="file"> (profile photo and backup import). */
    private final class ChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            boolean image = false;
            String[] types = params.getAcceptTypes();
            if (types != null) {
                for (String type : types) {
                    if (type != null && type.startsWith("image/")) image = true;
                }
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(image ? "image/*" : "*/*");
            try {
                startActivityForResult(intent, REQUEST_PICK_FILE);
                return true;
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                toast("No app is available to choose a file");
                return false;
            }
        }
    }

    /** window.TonnageAndroid. Methods run on a background bridge thread. */
    private final class Bridge {
        private File fileFor(String key) {
            if (key == null || !KEY.matcher(key).matches()) throw new IllegalArgumentException("Bad key");
            File dir = new File(getFilesDir(), "store");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("No storage directory");
            return new File(dir, key);
        }

        @JavascriptInterface
        public synchronized String getItem(String key) {
            try {
                File file = fileFor(key);
                if (!file.exists()) return null;
                return new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        @JavascriptInterface
        public synchronized boolean setItem(String key, String value) {
            AtomicFile file;
            try {
                file = new AtomicFile(fileFor(key));
            } catch (RuntimeException e) {
                return false;
            }
            FileOutputStream out = null;
            try {
                out = file.startWrite();
                out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
                file.finishWrite(out);
                return true;
            } catch (IOException e) {
                if (out != null) file.failWrite(out);
                return false;
            }
        }

        @JavascriptInterface
        public synchronized void removeItem(String key) {
            try {
                new AtomicFile(fileFor(key)).delete();
            } catch (RuntimeException ignored) {
                // Nothing stored under that key.
            }
        }

        @JavascriptInterface
        public void saveFile(final String name, final String mime, final String content) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pendingSave = content;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                    intent.putExtra(Intent.EXTRA_TITLE, name == null ? "tonnage-backup.json" : name);
                    try {
                        startActivityForResult(intent, REQUEST_SAVE_FILE);
                    } catch (ActivityNotFoundException e) {
                        pendingSave = null;
                        toast("No app is available to save files");
                    }
                }
            });
        }

        @JavascriptInterface
        public void keepScreenOn(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }
    }
}
