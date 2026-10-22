package com.tri5901.arbitrebaby;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;

/*
 * Arbitre Baby en appli Android : affiche le site en ligne dans une WebView et ajoute
 * ce que Chrome ne permet pas :
 *  - volume + lance le chrono 10 s, volume - lance le chrono 15 s ;
 *  - vibration faite par l'appli en mode « alarme » (passe outre le mode silencieux) ;
 *  - écran toujours allumé tant que l'appli est ouverte.
 */
public class MainActivity extends Activity {
    private static final String SITE = "https://arbitre-baby.vercel.app/";
    private static final String HOST = "arbitre-baby.vercel.app";
    private static final String FALLBACK = "file:///android_asset/index.html";

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
                if (HOST.equals(u.getHost()) || "file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {}
                return true;
            }
        });
        setContentView(web);

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

    private class Bridge {
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
