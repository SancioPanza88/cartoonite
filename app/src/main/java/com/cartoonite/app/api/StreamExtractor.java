package com.cartoonite.app.api;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estrae l'URL video diretto (mp4/m3u8) dalle pagine embed degli host,
 * per la riproduzione nativa con ExoPlayer.
 *
 * Strategie verificate sul campo:
 *  - uqload:  POST /dl -> JS packed (Dean Edwards) -> sources m3u8  [VERIFICATO]
 *  - chuckle-tube/voe: var source / sources / hls nella pagina      [parziale]
 *  - dhtpre & cloni vidhide: GET -> JS packed -> m3u8               [VERIFICATO]
 *  - fallback generico: qualsiasi URL .mp4/.m3u8 nella pagina
 *
 * Host NON estraibili (ritorna null -> l'app usa la WebView):
 *  - uprot.net (Cloudflare challenge), rpmplay (API cifrata)
 */
public class StreamExtractor {

    public static class Result {
        public final String videoUrl;
        public final String referer;

        Result(String videoUrl, String referer) {
            this.videoUrl = videoUrl;
            this.referer = referer;
        }
    }

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final Pattern PACKED_TAIL = Pattern.compile(
            "',\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*'([^']*)'\\.split\\('\\|'\\)");

    private static final Pattern[] VIDEO_PATTERNS = new Pattern[]{
            Pattern.compile("sources\\s*:\\s*\\[?\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("file\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\"']hls[\"']\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("var\\s+source\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\"']([^\"']*\\.m3u8[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\"']([^\"']*\\.mp4[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
    };

    public static Result extract(String embedUrl) {
        try {
            String host = new java.net.URL(embedUrl).getHost();
            if (host == null) return null;
            host = host.toLowerCase();

            if (host.contains("uqload")) {
                return extractUqload(embedUrl);
            }
            // vidhide clones e packer generici
            if (host.contains("dhtpre") || host.contains("peytonepre")
                    || host.contains("smoothpre") || host.contains("ryderjet")
                    || host.contains("vidhide")) {
                return extractPacked(embedUrl);
            }
            // voe / chuckle-tube e qualsiasi altro: pattern diretti + fallback generico
            return extractGeneric(embedUrl);
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────── uqload ───────────────────────────

    private static Result extractUqload(String embedUrl) throws IOException {
        java.net.URL u = new java.net.URL(embedUrl);
        String base = u.getProtocol() + "://" + u.getHost();
        String path = u.getPath().replace(".html", "");
        String[] parts = path.split("/");
        String code = parts[parts.length - 1];
        if (code.isEmpty()) return null;

        String response = Jsoup.connect(base + "/dl")
                .userAgent(UA)
                .referrer(embedUrl)
                .timeout(20000)
                .maxBodySize(0)
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .data("op", "embed")
                .data("file_code", code)
                .data("auto", "1")
                .data("referer", "")
                .method(org.jsoup.Connection.Method.POST)
                .execute()
                .body();

        String video = findVideoUrl(response, true);
        if (video != null) {
            return new Result(video, base + "/");
        }
        return null;
    }

    // ─────────────────────── packer generico (dhtpre ecc.) ───────────────────────

    private static Result extractPacked(String embedUrl) throws IOException {
        String html = get(embedUrl, null);
        String video = findVideoUrl(html, true);
        if (video != null) {
            java.net.URL u = new java.net.URL(embedUrl);
            return new Result(video, u.getProtocol() + "://" + u.getHost() + "/");
        }
        return null;
    }

    // ─────────────────────── pattern diretti + fallback ───────────────────────

    private static Result extractGeneric(String embedUrl) throws IOException {
        String html = get(embedUrl, null);
        String video = findVideoUrl(html, false);
        if (video != null) {
            java.net.URL u = new java.net.URL(embedUrl);
            return new Result(video, u.getProtocol() + "://" + u.getHost() + "/");
        }
        // ultima spiaggia: prova comunque l'unpack (alcuni host variano)
        video = findVideoUrl(html, true);
        if (video != null) {
            java.net.URL u = new java.net.URL(embedUrl);
            return new Result(video, u.getProtocol() + "://" + u.getHost() + "/");
        }
        return null;
    }

    private static String get(String url, String referer) throws IOException {
        return Jsoup.connect(url)
                .userAgent(UA)
                .referrer(referer != null ? referer : url)
                .timeout(20000)
                .maxBodySize(0)
                .ignoreContentType(true)
                .execute()
                .body();
    }

    /**
     * Cerca un URL video nella pagina. Se tryUnpack=true, prima scompattra
     * i blocchi eval(packer) e cerca anche li' dentro.
     */
    private static String findVideoUrl(String html, boolean tryUnpack) {
        String v = scanPatterns(html);
        if (v != null) return v;

        if (tryUnpack) {
            Matcher m = Pattern.compile("eval\\(function\\(p,a,c,k,e,d\\).*?</script>",
                    Pattern.DOTALL).matcher(html);
            while (m.find()) {
                String unpacked = unpack(m.group());
                if (!unpacked.isEmpty()) {
                    v = scanPatterns(unpacked);
                    if (v != null) return v;
                }
            }
        }
        return null;
    }

    private static String scanPatterns(String text) {
        for (Pattern p : VIDEO_PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                String url = m.group(1);
                if (isValidVideoUrl(url)) return url;
            }
        }
        return null;
    }

    private static boolean isValidVideoUrl(String url) {
        if (url == null || url.length() < 12) return false;
        if (!url.startsWith("http") && !url.startsWith("/")) return false;
        String l = url.toLowerCase();
        // decoy noti / asset non video
        if (l.contains("test-videos") || l.contains("big_buck_bunny")
                || l.contains("logo") || l.contains("poster") || l.contains("thumbnail")
                || l.contains(".vtt") || l.contains(".srt") || l.contains("empty")) {
            return false;
        }
        return l.contains(".m3u8") || l.contains(".mp4");
    }

    // ─────────────────────── Dean Edwards unpacker ───────────────────────

    private static final String DIGITS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** Scompatte eval(function(p,a,c,k,e,d){...}('p',a,c,'k'.split('|'),0,{})). */
    static String unpack(String packedScript) {
        int start = packedScript.indexOf("}('");
        if (start < 0) return "";
        String body = packedScript.substring(start + 3);

        // la coda: ',radix,count,'kw1|kw2'.split('|')
        Matcher tailM = PACKED_TAIL.matcher(body);
        int tailPos = -1;
        int radix = 0, count = 0;
        String[] keywords = null;
        while (tailM.find()) {
            tailPos = tailM.start();
            try {
                radix = Integer.parseInt(tailM.group(1));
                count = Integer.parseInt(tailM.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            keywords = tailM.group(3).split("\\|", -1);
        }
        if (tailPos < 0 || keywords == null || radix <= 1) return "";

        final String payload = body.substring(0, tailPos);
        final String[] kws = keywords;
        final int rdx = radix;

        // token = indice in base `radix`; sostituisci con keywords[indice]
        Pattern tokenRe = Pattern.compile("\\b[0-9a-zA-Z]+\\b");
        Matcher m = tokenRe.matcher(payload);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String token = m.group();
            int n = 0;
            boolean ok = true;
            for (int i = 0; i < token.length(); i++) {
                int d = DIGITS.indexOf(token.charAt(i));
                if (d < 0 || d >= rdx) { ok = false; break; }
                n = n * rdx + d;
            }
            String rep = (ok && n >= 0 && n < kws.length && !kws[n].isEmpty()) ? kws[n] : token;
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
