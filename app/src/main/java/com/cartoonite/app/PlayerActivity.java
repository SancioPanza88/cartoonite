package com.cartoonite.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.cartoonite.app.api.StreamExtractor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Player a due stadi:
 *  1. StreamExtractor prova a ricavare l'URL video diretto (m3u8/mp4)
 *     -> riproduzione NATIVA con ExoPlayer (premi play e parte)
 *  2. Se l'estrazione fallisce (host offuscati/Cloudflare)
 *     -> WebView del player embed con autoplay e blocco pubblicita'
 */
@UnstableApi
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_NAME = "extra_name";

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final Set<String> AD_DOMAINS = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "adservice.google.com",
            "popads.net", "popcash.net", "adnxs.com", "adsrvr.org",
            "advertising.com", "taboola.com", "outbrain.com", "criteo.com",
            "criteo.net", "adform.net", "bidswitch.net", "casalemedia.com",
            "moatads.com", "scorecardresearch.com", "zedo.com", "exoclick.com",
            "trafficjunky.com", "juicyads.com", "adsterra.com", "tsyndicate.com",
            "propellerads.com", "adcash.com", "hilltopads.net", "onclickads.net",
            "youradexchange.com", "ad-maven.com", "pushalert.co", "onesignal.com",
            "goingsimmerfear.com", "imasdk.googleapis.com"
    ));

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private PlayerView playerView;
    private WebView webView;
    private ProgressBar progress;
    private ExoPlayer exoPlayer;
    private String embedUrl;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        embedUrl = getIntent().getStringExtra(EXTRA_URL);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (embedUrl == null) {
            finish();
            return;
        }

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.playerToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(name != null ? name : "Player");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        playerView = findViewById(R.id.exoView);
        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.playerProgress);

        setupWebView();

        // stadio 1: estrazione URL video diretto
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            StreamExtractor.Result result = StreamExtractor.extract(embedUrl);
            handler.post(() -> {
                if (result != null) {
                    startNativePlayback(result);
                } else {
                    startWebViewPlayback();
                }
            });
        });
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ ExoPlayer (nativo) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void startNativePlayback(StreamExtractor.Result result) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", result.referer);
        headers.put("User-Agent", UA);

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(UA)
                .setDefaultRequestProperties(headers);

        MediaItem item = MediaItem.fromUri(result.videoUrl);
        MediaSource source;
        if (result.videoUrl.contains(".m3u8")) {
            source = new HlsMediaSource.Factory(httpFactory).createMediaSource(item);
        } else {
            source = new ProgressiveMediaSource.Factory(httpFactory).createMediaSource(item);
        }

        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                // CDN schizzinoso (403/401): ripiega sulla WebView embed
                stopNativePlayback();
                startWebViewPlayback();
            }
        });
        playerView.setPlayer(exoPlayer);
        exoPlayer.setMediaSource(source);
        exoPlayer.prepare();
        exoPlayer.play();

        progress.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
    }

    private void stopNativePlayback() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
        playerView.setVisibility(View.GONE);
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ WebView (embed + autoplay) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
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
                if (isAd(request.getUrl().toString())) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new java.io.ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Blocca solo domini pubblicitari noti. NON bloccare iframe
                // cross-domain: i player caricano video da CDN esterni legittimi.
                return isAd(request.getUrl().toString());
            }

            @Override
            public void onPageFinished(WebView view, String u) {
                progress.setVisibility(View.GONE);
                // Autoplay: clicca il tasto play del player e avvia il video
                String js = "(function(){"
                        + "function tryPlay(){"
                        + "  var b=document.querySelector('#vid_play,.play-button,.vjs-big-play-button,"
                        + ".jw-icon-playback,.plyr__control--overlaid,button.play,.play');"
                        + "  if(b){try{b.click();}catch(e){}}"
                        + "  var v=document.querySelector('video');"
                        + "  if(v){try{v.play&&v.play();}catch(e){}}"
                        + "}"
                        + "tryPlay();setTimeout(tryPlay,700);setTimeout(tryPlay,1800);setTimeout(tryPlay,3500);"
                        + "})();";
                view.evaluateJavascript(js, null);
            }
        });
    }

    private void startWebViewPlayback() {
        progress.setVisibility(View.VISIBLE);
        playerView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(embedUrl);
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ lifecycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) exoPlayer.pause();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        stopNativePlayback();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}

