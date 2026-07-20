package com.cartoonite.app;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Player WebView con blocco pubblicità: filtra richieste verso domini
 * pubblicitari/noti per popup e blocca l'apertura di nuove finestre.
 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_NAME = "extra_name";

    private static final Set<String> AD_DOMAINS = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "adservice.google.com",
            "popads.net", "popcash.net", "adnxs.com", "adsrvr.org",
            "advertising.com", "taboola.com", "outbrain.com", "criteo.com",
            "criteo.net", "adform.net", "bidswitch.net", "casalemedia.com",
            "moatads.com", "scorecardresearch.com", "zedo.com", "exoclick.com",
            "trafficjunky.com", "juicyads.com", "adsterra.com", "tsyndicate.com",
            "propellerads.com", "adcash.com", "hilltopads.net", "onclickads.net",
            "youradexchange.com", "ad-maven.com", "pushalert.co", "onesignal.com"
    ));

    private WebView webView;
    private ProgressBar progress;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        String url = getIntent().getStringExtra(EXTRA_URL);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (url == null) {
            finish();
            return;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(name != null ? name : "Player");
        }

        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.playerProgress);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setSupportMultipleWindows(false);

        webView.setWebViewClient(new WebViewClient() {

            private boolean isAd(String url) {
                try {
                    String host = new java.net.URL(url).getHost();
                    if (host == null) return false;
                    for (String ad : AD_DOMAINS) {
                        if (host.contains(ad)) return true;
                    }
                } catch (Exception ignored) {
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString();
                if (isAd(reqUrl)) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new java.io.ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString();
                // Blocca popup/redirect pubblicitari verso altri domini
                if (isAd(reqUrl)) return true;
                try {
                    String origHost = new java.net.URL(url).getHost();
                    String newHost = new java.net.URL(reqUrl).getHost();
                    if (origHost != null && newHost != null
                            && !newHost.equalsIgnoreCase(origHost)
                            && !request.isForMainFrame()) {
                        return true; // iframe popup
                    }
                } catch (Exception ignored) {
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String u, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String u) {
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(PlayerActivity.this,
                            R.string.error_player, Toast.LENGTH_LONG).show();
                }
            }
        });

        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
