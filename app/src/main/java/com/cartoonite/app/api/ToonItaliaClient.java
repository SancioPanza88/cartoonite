package com.cartoonite.app.api;

import com.cartoonite.app.model.Models.Episode;
import com.cartoonite.app.model.Models.Player;
import com.cartoonite.app.model.Models.Season;
import com.cartoonite.app.model.Models.Title;
import com.cartoonite.app.model.Models.TitleDetail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * Client live per toonitalia.xyz.
 *
 * Catalogo: API WordPress (/wp-json) -> titoli + copertine, leggero e veloce.
 * Dettaglio: parsing della pagina HTML (Jsoup) -> metadati, stagioni, episodi, player.
 * Copertine mancanti: recupero lazy dal contenuto del post.
 *
 * Nessun file JSON intermedio: il sito E' il database.
 */
public class ToonItaliaClient {

    public static final String BASE_URL = "https://toonitalia.xyz";
    private static final String API = BASE_URL + "/wp-json/wp/v2";

    /** Le 4 sezioni del sito -> id categoria WordPress (verificati). */
    public static final LinkedHashMap<String, Integer> CATEGORY_IDS = new LinkedHashMap<String, Integer>() {{
        put("anime_ita", 13);        // Anime > ITA (1356)
        put("anime_sub_ita", 14);    // Anime > Sub-Ita (401)
        put("film_animazione", 7);   // Film Animazione (808)
        put("serie_tv", 6);          // Serie Tv (465)
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
            "dropload.io", "savefiles.co",
            // rilevati dallo studio sistematico del sito
            "uprot.net", "dhtpre.com", "peytonepre.com", "smoothpre.com",
            "rpmplay.xyz", "rpmplay.com"
    ));

    // "PLAYER 1" e "PLAYER1" (con e senza spazio)
    private static final Pattern PLAYER_NAME = Pattern.compile(
            "^(PLAYER\\s?\\d*|VOE|VIDHIDE|MIXDROP|STREAMTAPE|DOOD(STREAM)?|SUPERVIDEO|VIDOZA|" +
            "UPSTREAM|STREAMZ|VIDMOLY|FILEMOON|UQLOAD|MAXSTREAM|HYDRAX|DROPLOAD|SAVEFILES|" +
            "ALPHA|DELTA|WOLFSTREAM|NINJASTREAM)$", Pattern.CASE_INSENSITIVE);

    // Formato serie: "1x01 – Titolo" oppure "01 – Titolo" oppure "001 – Titolo"
    private static final Pattern EP_LINE_SXE = Pattern.compile(
            "^\\s*(\\d{1,2})\\s*[×xX]\\s*(\\d{1,3})\\s*[–—-]\\s*(.+)$");
    private static final Pattern EP_LINE = Pattern.compile(
            "^\\s*(\\d{1,4})\\s*[–—]\\s*(.+)$");

    private static final Pattern TITLE_CUT = Pattern.compile(
            "\\s*[–—-]\\s*(PLAYER\\s?\\d*|VOE|VIDHIDE|MIXDROP|STREAMTAPE|DOOD|SUPERVIDEO|VIDOZA|" +
            "UPSTREAM|STREAMZ|VIDMOLY|FILEMOON|UQLOAD|MAXSTREAM|DROPLOAD|SAVEFILES|HYDRAX|" +
            "ALPHA|DELTA)\\b.*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern ANCHOR = Pattern.compile(
            "<a\\s+name=\"(S[\\w]+)\"[^>]*>\\s*</a>", Pattern.CASE_INSENSITIVE);

    private static final Pattern IMG_SRC = Pattern.compile(
            "<img[^>]+src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private static File cacheDir;
    private static final long PAGE_CACHE_TTL_MS = 6L * 60 * 60 * 1000; // 6 ore

    // ── cache copertine (permanente) ──
    private static final Map<String, String> coverCache = new HashMap<>();
    private static boolean coverCacheLoaded = false;

    public static void setCacheDir(File dir) {
        cacheDir = dir;
        if (cacheDir != null) cacheDir.mkdirs();
    }

    // ────────────────────────────── rete / cache ──────────────────────────────

    private static String httpGet(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(UA)
                .timeout(30000)
                .maxBodySize(0)
                .ignoreContentType(true)
                .execute()
                .body();
    }

    private static String fetchPage(String url) throws IOException {
        String cached = readPageCache(url);
        if (cached != null) return cached;
        String html = httpGet(url);
        writePageCache(url, html);
        return html;
    }

    private static String pageCacheFile(String url) {
        return String.valueOf(url.hashCode()) + ".html";
    }

    private static String readPageCache(String url) {
        if (cacheDir == null) return null;
        try {
            File f = new File(cacheDir, pageCacheFile(url));
            if (!f.exists()) return null;
            if (System.currentTimeMillis() - f.lastModified() > PAGE_CACHE_TTL_MS) return null;
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static void writePageCache(String url, String html) {
        if (cacheDir == null) return;
        try {
            Files.write(new File(cacheDir, pageCacheFile(url)).toPath(),
                    html.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static synchronized void loadCoverCache() {
        if (coverCacheLoaded || cacheDir == null) return;
        coverCacheLoaded = true;
        try {
            File f = new File(cacheDir, "covers.json");
            if (!f.exists()) return;
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                coverCache.put(k, o.getString(k));
            }
        } catch (Exception ignored) {
        }
    }

    private static synchronized void saveCoverCache() {
        if (cacheDir == null) return;
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, String> e : coverCache.entrySet()) o.put(e.getKey(), e.getValue());
            Files.write(new File(cacheDir, "covers.json").toPath(),
                    o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    // ────────────────────────────── catalogo ──────────────────────────────

    /**
     * Scarica titoli di una sezione dalla API WP (ordinati alfabeticamente),
     * con copertina quando Yoast la espone (~70%). Le mancanti si recuperano
     * dopo con {@link #loadMissingCovers}.
     */
    public static List<Title> getCategoryTitles(String categoryKey) throws IOException {
        Integer catId = CATEGORY_IDS.get(categoryKey);
        if (catId == null) throw new IOException("Categoria sconosciuta: " + categoryKey);
        loadCoverCache();

        List<Title> titles = new ArrayList<>();
        int page = 1;
        while (true) {
            String url = API + "/posts?categories=" + catId
                    + "&per_page=100&page=" + page
                    + "&orderby=title&order=asc"
                    + "&_fields=link,title,yoast_head_json.og_image";
            JSONArray arr;
            try {
                arr = new JSONArray(httpGet(url));
            } catch (Exception e) {
                // WP risponde 400 quando la pagina non esiste: fine lista
                break;
            }
            if (arr.length() == 0) break;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p == null) continue;
                String link = p.optString("link", "");
                String slug = extractSlug(link);
                if (slug.isEmpty()) continue;

                String name = decodeHtml(p.optJSONObject("title") != null
                        ? p.optJSONObject("title").optString("rendered", "") : slug);

                String img = null;
                Object ogObj = p.opt("yoast_head_json");
                if (ogObj instanceof JSONObject) {
                    JSONArray og = ((JSONObject) ogObj).optJSONArray("og_image");
                    if (og != null && og.length() > 0) {
                        img = og.optJSONObject(0) != null ? og.optJSONObject(0).optString("url", null) : null;
                    }
                }
                if (img == null) img = coverCache.get(slug);
                if (img != null) img = img.replace("http://", "https://");

                Title t = new Title(slug, name, link.replace("http://", "https://"), categoryKey);
                t.imageUrl = img;
                titles.add(t);
            }
            if (arr.length() < 100) break;
            page++;
            if (page > 60) break; // sicurezza
        }
        return titles;
    }

    /**
     * Recupera le copertine mancanti dal contenuto dei post (batch da 10).
     * Riempe coverCache e ritorna la mappa slug->url aggiornata per gli slug richiesti.
     */
    public static Map<String, String> loadMissingCovers(List<String> slugs) throws IOException {
        loadCoverCache();
        Map<String, String> result = new HashMap<>();
        List<String> toFetch = new ArrayList<>();
        for (String s : slugs) {
            if (coverCache.containsKey(s)) result.put(s, coverCache.get(s));
            else toFetch.add(s);
        }

        for (int i = 0; i < toFetch.size(); i += 10) {
            List<String> batch = toFetch.subList(i, Math.min(i + 10, toFetch.size()));
            StringBuilder sb = new StringBuilder(API + "/posts?_fields=link,content&per_page=10&slug=");
            for (int j = 0; j < batch.size(); j++) {
                if (j > 0) sb.append(',');
                sb.append(batch.get(j));
            }
            try {
                JSONArray arr = new JSONArray(httpGet(sb.toString()));
                for (int k = 0; k < arr.length(); k++) {
                    JSONObject p = arr.optJSONObject(k);
                    if (p == null) continue;
                    String slug = extractSlug(p.optString("link", ""));
                    String content = p.optJSONObject("content") != null
                            ? p.optJSONObject("content").optString("rendered", "") : "";
                    String img = null;
                    Matcher m = IMG_SRC.matcher(content);
                    if (m.find()) img = m.group(1).replace("http://", "https://");
                    if (!slug.isEmpty()) {
                        coverCache.put(slug, img != null ? img : "");
                        if (img != null) result.put(slug, img);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        // marca anche i non trovati per non ri-chiederli
        for (String s : toFetch) {
            if (!coverCache.containsKey(s)) coverCache.put(s, "");
        }
        saveCoverCache();
        return result;
    }

    private static String extractSlug(String link) {
        String s = link.replaceFirst("^https?://toonitalia\\.xyz/", "");
        s = s.replaceAll("/+$", "");
        return s.contains("/") ? "" : s;
    }

    private static String decodeHtml(String s) {
        return Jsoup.parse(s).text();
    }

    // ────────────────────────────── dettaglio ──────────────────────────────

    public static TitleDetail getDetail(Title t) throws IOException {
        Document doc = Jsoup.parse(fetchPage(t.url), t.url);

        TitleDetail d = new TitleDetail();
        d.id = t.id;
        d.title = t.title;
        d.url = t.url;
        d.category = t.category;
        d.image = t.imageUrl;

        if (d.image == null || d.image.isEmpty()) {
            Element og = doc.selectFirst("meta[property=og:image]");
            if (og != null) d.image = og.attr("content").replace("http://", "https://");
        }
        if (d.image == null || d.image.isEmpty()) {
            Element img = doc.selectFirst("div.entry-content img, article img");
            if (img != null) d.image = img.absUrl("src").replace("http://", "https://");
        }
        // salva in cache copertine per la griglia
        if (d.image != null && !d.image.isEmpty()) {
            loadCoverCache();
            coverCache.put(d.id, d.image);
            saveCoverCache();
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

    private static List<Season> splitByAnchors(Element content, String html) {
        Matcher m = ANCHOR.matcher(html);
        List<int[]> spans = new ArrayList<>();
        List<String> anchors = new ArrayList<>();
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
                if (sib.tagName().equals("h2") || sib.tagName().equals("h3")) {
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

    private static List<Episode> parseEpisodeLines(String blockHtml) {
        String[] lines = blockHtml.split("(?i)<br\\s*/?>|</p>\\s*|<p[^>]*>|</li>\\s*<li[^>]*>|<li[^>]*>");

        Map<Integer, Episode> byNum = new LinkedHashMap<>();
        for (String line : lines) {
            if (!line.contains("<a") && !line.contains("–") && !line.contains("—")) continue;
            Document lineDoc = Jsoup.parse(line);
            String lineText = lineDoc.text().trim();

            // Prima formato SxE ("1x01 – Titolo"), poi formato piano ("001 – Titolo")
            int num;
            String rest;
            String label = null;
            Matcher mSxe = EP_LINE_SXE.matcher(lineText);
            if (mSxe.find()) {
                try {
                    int s = Integer.parseInt(mSxe.group(1));
                    int e = Integer.parseInt(mSxe.group(2));
                    num = s * 1000 + e; // univoco anche tra stagioni diverse
                    label = s + "x" + String.format(java.util.Locale.US, "%02d", e);
                } catch (NumberFormatException e) {
                    continue;
                }
                rest = mSxe.group(3);
            } else {
                Matcher m = EP_LINE.matcher(lineText);
                if (!m.find()) continue;
                try {
                    num = Integer.parseInt(m.group(1));
                } catch (NumberFormatException e) {
                    continue;
                }
                rest = m.group(2);
            }

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
            ep.label = label;
            ep.players = players;
            Episode prev = byNum.get(num);
            if (prev == null || ep.players.size() > prev.players.size()) byNum.put(num, ep);
        }
        return new ArrayList<>(byNum.values());
    }
}
