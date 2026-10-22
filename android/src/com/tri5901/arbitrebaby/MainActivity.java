package com.tri5901.arbitrebaby;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/*
 * Arbitre Baby en appli Android : affiche le site en ligne dans une WebView et ajoute
 * ce que Chrome ne permet pas :
 *  - volume + lance le chrono 10 s, volume - lance le chrono 15 s ;
 *  - vibration faite par l'appli en mode « alarme » (passe outre le mode silencieux) ;
 *  - écran toujours allumé tant que l'appli est ouverte ;
 *  - match Coral : la page publique du tournoi est ouverte en arrière-plan (comme par un
 *    spectateur) et coral.js y lit le match de la table demandée.
 */
public class MainActivity extends Activity {
    private static final String SITE = "https://arbitre-baby.vercel.app/";
    private static final String HOST = "arbitre-baby.vercel.app";
    private static final String FALLBACK = "file:///android_asset/index.html";
    // À monter avec versionCode (AndroidManifest.xml) et "apk" dans version.json
    private static final int VERSION_APK = 4;
    private static final String CORAL = "https://app.tablesoccer.org/p/";
    // Nombre d'essais (un par seconde) avant de déclarer le tournoi introuvable
    private static final int CORAL_ESSAIS = 30;

    // Injecté à chaque chargement : remplace navigator.vibrate par la vibration de l'appli
    // et expose arbitreVolume() qui clique sur les boutons 10 / 15 de la page.
    private static final String INJECT =
        "(function(){" +
        "if(window.__arbitreApp)return;window.__arbitreApp=true;" +
        "navigator.vibrate=function(p){return ArbitreApp.vibrate(JSON.stringify(p===undefined?0:p));};" +
        "window.arbitreVolume=function(d){" +
        "var b=document.querySelector('[data-t=\"'+(d==='up'?10:15)+'\"]');if(b)b.click();};" +
        "})();";

    private WebView web;
    private Vibrator vibrator;
    private boolean onFallback = false;

    private FrameLayout racine;
    private WebView coral;          // page publique Coral, cachée sous la page de l'appli
    private String coralCode;       // tournoi actuellement chargé dans coral
    private int coralDemande = 0;   // numéro de la dernière demande : une nouvelle demande annule l'ancienne
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= 31) {
            vibrator = ((VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // Le bip Web Audio doit pouvoir sonner quand le chrono est lancé par un bouton de volume.
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "ArbitreApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(INJECT, null);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                // Pas de réseau et rien en cache : on affiche la copie embarquée dans l'appli.
                if (req.isForMainFrame() && !onFallback) {
                    onFallback = true;
                    v.loadUrl(FALLBACK);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                // Un APK se télécharge dans le navigateur du téléphone, pas dans l'appli
                boolean apk = u.getPath() != null && u.getPath().endsWith(".apk");
                if (!apk && (HOST.equals(u.getHost()) || "file".equals(u.getScheme()))) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {}
                return true;
            }
        });
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) {}
            }
        });
        racine = new FrameLayout(this);
        racine.setBackgroundColor(Color.BLACK);
        racine.addView(web);
        setContentView(racine);

        if (state == null || web.restoreState(state) == null) web.loadUrl(SITE);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Un appui = un départ ; maintenir le bouton ne relance pas le chrono en boucle.
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                String d = k == KeyEvent.KEYCODE_VOLUME_UP ? "up" : "down";
                web.evaluateJavascript("window.arbitreVolume&&arbitreVolume('" + d + "')", null);
            }
            return true; // le volume du téléphone ne bouge pas
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        // Retour = mettre l'appli en arrière-plan, sans perdre un chrono en cours.
        moveTaskToBack(true);
    }

    private void vibrate(long[] webPattern) {
        Log.i("ArbitreBaby", "vibrate " + java.util.Arrays.toString(webPattern));
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (webPattern.length == 0) { vibrator.cancel(); return; }
        // Motif web : vibre, pause, vibre... ; motif Android : pause, vibre, pause...
        long[] timings = new long[webPattern.length + 1];
        int[] amps = new int[webPattern.length + 1];
        for (int i = 0; i < webPattern.length; i++) {
            timings[i + 1] = webPattern[i];
            amps[i + 1] = i % 2 == 0 ? 255 : 0;
        }
        VibrationEffect fx = vibrator.hasAmplitudeControl()
            ? VibrationEffect.createWaveform(timings, amps, -1)
            : VibrationEffect.createWaveform(timings, -1);
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(fx, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
        } else {
            vibrator.vibrate(fx, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        }
    }

    /* ---------- Match Coral (tournoi + table) ---------- */

    private void chargerCoral(String code, final String table) {
        if (coral == null) {
            coral = new WebView(this);
            coral.getSettings().setJavaScriptEnabled(true);
            coral.getSettings().setDomStorageEnabled(true);
            coral.setWebViewClient(new WebViewClient());
            // Posée sous la page de l'appli : invisible, mais affichée pour que Coral se charge normalement
            racine.addView(coral, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        // Même tournoi : la page reste ouverte et à jour (Coral la rafraîchit en direct), on relit seulement
        if (!code.equals(coralCode)) {
            coralCode = code;
            coral.loadUrl(CORAL + code);
        }
        final int demande = ++coralDemande;
        final String c = code;
        // Téléchargement du script hors du fil de l'interface, puis lecture du match sur le fil de l'interface
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String script = scriptCoral()
                    .replace("__CODE__", JSONObject.quote(c)).replace("__TABLE__", JSONObject.quote(table));
                ui.post(new Runnable() {
                    @Override
                    public void run() { extraire(script, demande, 0); }
                });
            }
        }).start();
    }

    // coral.js : d'abord la version du site (pour corriger la lecture Coral sans refaire l'APK),
    // gardée dans un fichier du téléphone ; sans réseau, la dernière gardée ; sinon celle de l'APK.
    private String scriptCoral() {
        File garde = new File(getFilesDir(), "coral.js");
        try {
            HttpURLConnection cx = (HttpURLConnection) new URL(SITE + "coral.js?t=" + System.currentTimeMillis()).openConnection();
            cx.setConnectTimeout(4000);
            cx.setReadTimeout(4000);
            if (cx.getResponseCode() == 200) {
                String s = lireFlux(cx.getInputStream());
                if (s.contains("__CODE__") && s.contains("__TABLE__")) {
                    FileOutputStream out = new FileOutputStream(garde);
                    out.write(s.getBytes("UTF-8"));
                    out.close();
                    return s;
                }
            }
        } catch (Exception e) {
            // pas de réseau : on passe à la copie gardée
        }
        try {
            if (garde.exists()) return lireFlux(new FileInputStream(garde));
        } catch (Exception e) {
            // copie illisible : on prend celle de l'APK
        }
        return lireAsset("coral.js");
    }

    private String lireFlux(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private void extraire(final String script, final int demande, final int essai) {
        if (demande != coralDemande) return;
        coral.evaluateJavascript(script, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String r) {
                if (demande != coralDemande) return;
                boolean pret = r != null && !r.equals("null");
                if (!pret && essai + 1 < CORAL_ESSAIS) {
                    ui.postDelayed(new Runnable() {
                        @Override
                        public void run() { extraire(script, demande, essai + 1); }
                    }, 1000);
                    return;
                }
                web.evaluateJavascript("window.arbitreCoral&&arbitreCoral(" + (pret ? r : "{\"err\":\"tournoi\"}") + ")", null);
            }
        });
    }

    private String lireAsset(String nom) {
        try {
            return lireFlux(getAssets().open(nom));
        } catch (Exception e) {
            return "null";
        }
    }

    private class Bridge {
        // Demande le match en cours sur une table d'un tournoi Coral ; réponse dans window.arbitreCoral(...)
        @JavascriptInterface
        public void coral(final String code, final String table) {
            ui.post(new Runnable() {
                @Override
                public void run() { chargerCoral(code.trim().toUpperCase(), table.trim()); }
            });
        }

        // Numéro de l'APK, comparé par la page à version.json pour proposer la mise à jour
        @JavascriptInterface
        public int version() {
            return VERSION_APK;
        }

        // Partage d'un texte (résumé du match) vers WhatsApp, SMS, e-mail…
        @JavascriptInterface
        public void share(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "Partager le résumé"));
        }

        @JavascriptInterface
        public boolean vibrate(String json) {
            try {
                long[] p;
                if (json.startsWith("[")) {
                    JSONArray a = new JSONArray(json);
                    p = new long[a.length()];
                    for (int i = 0; i < a.length(); i++) p[i] = Math.max(0, a.getLong(i));
                } else {
                    long ms = (long) Double.parseDouble(json);
                    p = ms > 0 ? new long[]{ms} : new long[0];
                }
                MainActivity.this.vibrate(p);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
