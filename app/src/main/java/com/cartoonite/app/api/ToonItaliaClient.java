package com.cartoonite.app.api;

import com.cartoonite.app.model.Models.Episode;
import com.cartoonite.app.model.Models.Player;
import com.cartoonite.app.model.Models.Season;
import com.cartoonite.app.model.Models.Title;
import com.cartoonite.app.model.Models.TitleDetail;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client che scarica e parsa toonitalia.xyz in tempo reale.
 * Nessun dato viene salvato in JSON: il sito E' il database.
 * Le pagine vengono cacheate su disco per qualche ora.
 */
public class ToonItaliaClient {

    public static final String BASE_URL = "https://toonitalia.xyz";

    public static final LinkedHashMap<String, String> CATEGORIES = new LinkedHashMap<String, String>() {{
        put("anime_ita", "/anime-ita/");
        put("anime_sub_ita", "/contatti/");
        put("film_animazione", "/film-animazione/");
        put("serie_tv", "/serie-tv/");
    }};

    public static final LinkedHashMap<String, String> CATEGORY_LABELS = new LinkedHashMap<String, String>() {{
        put("anime_ita", "Anime ITA");
        put("anime_sub_ita", "Anime Sub-ITA");
        put("film_animazione", "Film");
        put("serie_tv", "Serie TV");
    }};

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final Set<String> STREAMING_DOMAINS = new HashSet<>(Arrays.asList(
            "uqload.is", "uqload.co", "uqload.to", "chuckle-tube.com", "ryderjet.com",
            "streamtape.com", "streamtape.to", "doodstream.com", "dood.to", "dood.la",
            "mixdrop.co", "mixdrop.to", "supervideo.tv", "vidoza.net", "upstream.to",
            "streamz.ws", "vidmoly.to", "vidmoly.me", "filemoon.sx", "filemoon.to",
            "voe.sx", "vidhidepro.com", "vidhide.com", "maxstream.video",
            "dropload.io", "savefiles.co"
    ));

    private static final Pattern PLAYER_NAME = Pattern.compile(
            "^(PLAYER\\d*|VOE|VIDHIDE|MIXDROP|STREAMTAPE|DOOD(STREAM)?|SUPERVIDEO|VIDOZA|" +
            "UPSTREAM|STREAMZ|VIDMOLY|FILEMOON|UQLOAD|MAXSTREAM|HYDRAX|DROPLOAD|SAVEFILES|" +
            "ALPHA|DELTA|WOLFSTREAM|NINJASTREAM)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern EP_LINE = Pattern.compile("^\\s*(\\d{1,4})\\s*[–—]\\s*(.+)$");

    private static final Pattern TITLE_CUT = Pattern.compile(
            "\\s*[–—-]\\s*(PLAYER\\d*|VOE|VIDHIDE|MIXDROP|STREAMTAPE|DOOD|SUPERVIDEO|VIDOZA|" +
            "UPSTREAM|STREAMZ|VIDMOLY|FILEMOON|UQLOAD|MAXSTREAM|DROPLOAD|SAVEFILES|HYDRAX|" +
            "ALPHA|DELTA)\\b.*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern ANCHOR = Pattern.compile(
            "<a\\s+name=\"(S[\\w]+)\"[^>]*>\\s*</a>", Pattern.CASE_INSENSITIVE);

    private static File cacheDir;
    private static final long CACHE_TTL_MS = 6L * 60 * 60 * 1000; // 6 ore

    public static void setCacheDir(File dir) {
        cacheDir = dir;
    }

    // ────────────────────────────── rete ──────────────────────────────

    private static String fetch(String url) throws IOException {
        String cached = readCache(url);
        if (cached != null) return cached;

        String html = Jsoup.connect(url)
                .userAgent(UA)
                .timeout(30000)
                .maxBodySize(0)
                .get()
                .outerHtml();
        writeCache(url, html);
        return html;
    }

    private static String cacheKey(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }

    private static String readCache(String url) {
        if (cacheDir == null) return null;
        try {
            File f = new File(cacheDir, cacheKey(url) + ".html");
            if (!f.exists()) return null;
            if (System.currentTimeMillis() - f.lastModified() > CACHE_TTL_MS) return null;
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeCache(String url, String html) {
        if (cacheDir == null) return;
        try {
            File f = new File(cacheDir, cacheKey(url) + ".html");
            Files.write(f.toPath(), html.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    // ────────────────────────────── catalogo ──────────────────────────────

    /** Scarica la lista titoli di una categoria (id, titolo, url). */
    public static List<Title> getCategoryTitles(String categoryKey) throws IOException {
        String path = CATEGORIES.get(categoryKey);
        if (path == null) throw new IOException("Categoria sconosciuta: " + categoryKey);

        Document doc = Jsoup.parse(fetch(BASE_URL + path), BASE_URL);
        Element content = doc.selectFirst("div.entry-content");
        if (content == null) content = doc.selectFirst("article");
        if (content == null) content = doc.body();

        Set<String> skip = new HashSet<>(Arrays.asList(
                "/anime-ita/", "/contatti/", "/film-animazione/", "/serie-tv/",
                "/toonitalia-sito-ufficiale/", "/author/", "/category/", "/tag/"));

        List<Title> titles = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Element a : content.select("a[href]")) {
            String href = a.attr("href").trim();
            String text = a.text().trim();
            if (text.isEmpty() || !href.startsWith(BASE_URL + "/")) continue;
            if (href.contains("#")) continue;
            boolean skipPath = false;
            for (String p : skip) if (href.contains(p)) { skipPath = true; break; }
            if (skipPath) continue;

            String slug = href.substring((BASE_URL + "/").length());
            slug = slug.replaceAll("/+$", "");
            if (slug.isEmpty() || slug.contains("/")) continue;
            if (!seen.add(slug)) continue;

            if (!href.endsWith("/")) href += "/";
            titles.add(new Title(slug, text, href, categoryKey));
        }
        return titles;
    }

    // ────────────────────────────── dettaglio ──────────────────────────────

    public static TitleDetail getDetail(Title t) throws IOException {
        Document doc = Jsoup.parse(fetch(t.url), t.url);

        TitleDetail d = new TitleDetail();
        d.id = t.id;
        d.title = t.title;
        d.url = t.url;
        d.category = t.category;

        Element og = doc.selectFirst("meta[property=og:image]");
        if (og != null) d.image = og.attr("content");
        if (d.image == null || d.image.isEmpty()) {
            Element img = doc.selectFirst("div.entry-content img, article img");
            if (img != null) d.image = img.absUrl("src");
        }

        Element content = doc.selectFirst("div.entry-content");
        if (content == null) content = doc.selectFirst("article");
        if (content == null) return d;

        String text = content.text();
        String html = content.html();

        d.originalTitle = meta(text, "Titolo originale");
        d.country = meta(text, "Paese di origine");
        d.status = meta(text, "Stato Opera");
        d.totalEpisodes = meta(text, "N. Episodi");

        String year = meta(text, "Data di pubblicazione");
        if (year != null) {
            Matcher ym = Pattern.compile("(?:19|20)\\d{2}").matcher(year);
            String last = null;
            while (ym.find()) last = ym.group();
            d.year = last;
        }

        // Trama
        Matcher tm = Pattern.compile(
                "Trama:\\s*([\\s\\S]+?)(?:\\s*Scegli Stagione:|\\s*Fonte:|\\s*Sigla|\\s*Sigle|\\s*\\d{3}\\s*[–—]|$)",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (tm.find()) {
            d.description = tm.group(1).replaceAll("\\s+", " ").trim();
            if (d.description.length() > 2000)
                d.description = d.description.substring(0, 2000);
        }
        if (d.description == null || d.description.isEmpty()) {
            Element ogd = doc.selectFirst("meta[property=og:description]");
            if (ogd != null) d.description = ogd.attr("content");
        }

        // ── stagioni ed episodi ──
        d.seasons = splitByAnchors(content, html);
        if (d.seasons.isEmpty()) d.seasons = splitByHeaders(content);
        if (d.seasons.isEmpty()) {
            List<Episode> eps = parseEpisodeLines(html);
            if (!eps.isEmpty()) {
                Season s = new Season("Stagione 1");
                s.episodes = eps;
                d.seasons.add(s);
            }
        }
        if (d.seasons.isEmpty()) {
            // film / contenuto singolo
            List<Player> players = new ArrayList<>();
            Set<String> seenUrls = new HashSet<>();
            for (Element a : content.select("a[href]")) {
                String href = a.attr("href").trim();
                if (isPlayerLink(a.text().trim(), href) && seenUrls.add(href)) {
                    players.add(new Player(a.text().trim().isEmpty()
                            ? "PLAYER" + (players.size() + 1) : a.text().trim(), href));
                }
            }
            if (!players.isEmpty()) {
                Season s = new Season("Contenuto");
                Episode e = new Episode(1,
                        "film_animazione".equals(d.category) ? "Film Completo" : d.title);
                e.players = players;
                s.episodes.add(e);
                d.seasons.add(s);
            }
        }
        return d;
    }

    // ────────────────────────────── parsing interno ──────────────────────────────

    private static String meta(String text, String label) {
        Pattern p = Pattern.compile(
                Pattern.quote(label) + "\\s*:\\s*(.+?)(?:\\s*(?:Titolo originale|Titolo Alternativo|Titoli Alternativi|" +
                "Paese di origine|Data di pubblicazione|Stato Opera|N\\. Episodi|Episodi disponibili|" +
                "Aggiornamento episodi|Trama|Link Streaming|Scegli Stagione)\\s*:|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(text);
        if (!m.find()) return null;
        String v = m.group(1).replaceAll("\\s+", " ").replaceAll("^[ \\u00A0\\-–—]+|[ \\u00A0\\-–—]+$", "").trim();
        return v.isEmpty() ? null : v;
    }

    private static boolean isStreamingUrl(String url) {
        try {
            String host = new java.net.URL(url).getHost();
            if (host == null) return false;
            for (String dom : STREAMING_DOMAINS) if (host.contains(dom)) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isPlayerLink(String name, String url) {
        if (url == null || !url.startsWith("http")) return false;
        if (name != null && PLAYER_NAME.matcher(name.trim()).matches()) return true;
        return isStreamingUrl(url);
    }

    /** Strategia A: split sulle ancore <a name="Sxx"> (pagine con "Scegli Stagione"). */
    private static List<Season> splitByAnchors(Element content, String html) {
        Matcher m = ANCHOR.matcher(html);
        List<int[]> spans = new ArrayList<>();
        List<String> anchors = new ArrayList<>();
        int lastEnd = -1;
        while (m.find()) {
            anchors.add(m.group(1));
            spans.add(new int[]{m.start(), m.end()});
        }
        List<Season> seasons = new ArrayList<>();
        if (anchors.isEmpty()) return seasons;

        Map<String, String> selectorNames = new HashMap<>();
        for (Element a : content.select("a[href^='#S']")) {
            String name = a.text().replaceAll("\\s+", " ").trim();
            if (!name.isEmpty()) selectorNames.put(a.attr("href").substring(1), name);
        }

        for (int i = 0; i < anchors.size(); i++) {
            int start = spans.get(i)[1];
            int end = (i + 1 < spans.size()) ? spans.get(i + 1)[0] : html.length();
            String block = html.substring(start, end);
            List<Episode> eps = parseEpisodeLines(block);
            if (eps.isEmpty()) continue;
            String name = selectorNames.get(anchors.get(i));
            if (name == null) {
                Element h = Jsoup.parse(block).selectFirst("h2, h3");
                name = h != null ? h.text().trim()
                        : "Stagione " + anchors.get(i).substring(1);
            }
            Season s = new Season(name);
            s.episodes = eps;
            seasons.add(s);
        }
        return seasons;
    }

    /** Strategia B: split su <h2>/<h3> che contengono "stagione". */
    private static List<Season> splitByHeaders(Element content) {
        List<Season> seasons = new ArrayList<>();
        Elements headers = content.select("h2, h3");
        for (Element h : headers) {
            String t = h.text().trim();
            boolean isSeason = !t.isEmpty()
                    && (t.toLowerCase().contains("stagione")
                    || t.toLowerCase().contains("season")
                    || Pattern.matches("^\\d+\\s*[°ª].*", t));
            if (!isSeason) continue;

            StringBuilder block = new StringBuilder();
            Element sib = h.nextElementSibling();
            while (sib != null) {
                if ((sib.tagName().equals("h2") || sib.tagName().equals("h3"))) {
                    String st = sib.text().trim().toLowerCase();
                    if (st.contains("stagione") || st.contains("season")
                            || Pattern.matches("^\\d+\\s*[°ª].*", st)) break;
                }
                block.append(sib.outerHtml()).append('\n');
                sib = sib.nextElementSibling();
            }
            List<Episode> eps = parseEpisodeLines(block.toString());
            if (!eps.isEmpty()) {
                Season s = new Season(t);
                s.episodes = eps;
                seasons.add(s);
            }
        }
        return seasons;
    }

    /**
     * Parsa righe tipo: {@code 001 – Titolo – <a>PLAYER1</a> – <a>VIDHIDE</a>}
     * separate da &lt;br&gt; o &lt;p&gt;.
     */
    private static List<Episode> parseEpisodeLines(String blockHtml) {
        String[] lines = blockHtml.split("(?i)<br\\s*/?>|</p>\\s*|<p[^>]*>|</li>\\s*<li[^>]*>|<li[^>]*>");

        Map<Integer, Episode> byNum = new LinkedHashMap<>();
        for (String line : lines) {
            if (!line.contains("<a") && !line.contains("–") && !line.contains("—")) continue;
            Document lineDoc = Jsoup.parse(line);
            String lineText = lineDoc.text().trim();
            Matcher m = EP_LINE.matcher(lineText);
            if (!m.find()) continue;

            int num;
            try {
                num = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            String rest = m.group(2);

            List<Player> players = new ArrayList<>();
            Set<String> seenUrls = new HashSet<>();
            for (Element a : lineDoc.select("a[href]")) {
                String href = a.attr("href").trim();
                String name = a.text().trim();
                if (!isPlayerLink(name, href) || !seenUrls.add(href)) continue;
                players.add(new Player(name.isEmpty() ? "PLAYER" + (players.size() + 1) : name, href));
            }

            String title = TITLE_CUT.matcher(rest).replaceFirst("");
            title = title.replaceAll("\\s*[–—-]\\s*$", "").replaceAll("\\s+", " ").trim();
            if (title.isEmpty()) title = "Episodio " + num;

            Episode ep = new Episode(num, title);
            ep.players = players;
            Episode prev = byNum.get(num);
            if (prev == null || ep.players.size() > prev.players.size()) byNum.put(num, ep);
        }
        return new ArrayList<>(byNum.values());
    }
}
