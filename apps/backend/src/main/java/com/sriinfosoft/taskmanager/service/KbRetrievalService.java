package com.sriinfosoft.taskmanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Knowledge-base retrieval for the Learning Circle (the RAG core).
 *
 * The corpus is the mentor's own investing pages on the live site. This
 * service fetches those pages, splits them into passages, caches the parsed
 * passages in memory (TTL-refreshed), and scores them lexically against a
 * question. It is the ONE net-new technique in the feature and is fully
 * deterministic — no model call happens here, so it is unit-testable offline.
 *
 * Design choices (deliberate, documented):
 *  - Lexical scoring, not embeddings: the corpus is a handful of documents;
 *    keyword/passage overlap is accurate and adds zero infrastructure. Swapping
 *    in an embedding search later is a change to score()/search() alone; nothing
 *    that calls this service changes.
 *  - Fetch-and-cache with graceful degradation: a fetch failure keeps the last
 *    good cache; a cold cache with the site unreachable yields NO passages, which
 *    the caller treats as "escalate to the mentor", never a fabricated answer.
 *  - The URL list is configuration (kb.invest.urls), so the corpus is edited
 *    without code changes.
 */
@Service
public class KbRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(KbRetrievalService.class);

    /** One scored passage returned to the caller. */
    public record ScoredPassage(String sourceTitle, String sourceUrl, String text, double score) {}

    /** Retrieval outcome: the best score seen and the top passages. */
    public record RetrievalResult(double bestScore, List<ScoredPassage> passages) {
        public boolean isEmpty() { return passages.isEmpty(); }
    }

    /** A parsed passage in the cache (unscored). */
    private record Passage(String sourceTitle, String sourceUrl, String text) {}

    @Value("${kb.invest.urls:}")
    private String urlsCsv;

    // Catalog-driven discovery: read the invest catalog and ground against every
    // page it lists, so adding a page to the site needs no config change. The
    // catalog holds relative paths (e.g. "investing-basics.html") resolved
    // against kb.invest.base. If kb.invest.urls is set it OVERRIDES the catalog
    // (explicit list wins), so the corpus is still pinnable when needed.
    @Value("${kb.invest.catalog}")
    private String catalogUrl;

    @Value("${kb.invest.base}")
    private String investBase;

    @Value("${kb.cache.ttl.minutes}")
    private long ttlMinutes;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<String, List<Passage>> cache = new ConcurrentHashMap<>();
    private volatile long cacheLoadedAt = 0L;

    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SCRIPT_STYLE =
            Pattern.compile("<(script|style)[^>]*>.*?</\\1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the","a","an","and","or","but","of","to","in","on","for","is","are","was","were",
            "be","been","with","as","at","by","it","this","that","these","those","i","you","my",
            "how","what","when","which","do","does","should","can","could","would","if","about"));

    /**
     * The invest URLs to ground against. An explicit kb.invest.urls list wins
     * (pin the corpus when you want to); otherwise the list is discovered from
     * the invest catalog — every page it lists, resolved against kb.invest.base.
     * A catalog fetch/parse failure returns an empty list, which the caller
     * treats as "no corpus" → escalate, never fabricate.
     */
    public List<String> configuredUrls() {
        List<String> explicit = explicitUrls();
        if (!explicit.isEmpty()) return explicit;
        return catalogUrls();
    }

    /** Explicit override list from kb.invest.urls (comma-separated). */
    private List<String> explicitUrls() {
        List<String> out = new ArrayList<>();
        if (urlsCsv != null) {
            for (String u : urlsCsv.split(",")) {
                if (!u.isBlank()) out.add(u.trim());
            }
        }
        return out;
    }

    /** Discover invest page URLs from the catalog's "path" entries. */
    private List<String> catalogUrls() {
        List<String> out = new ArrayList<>();
        if (catalogUrl == null || catalogUrl.isBlank()) return out;
        try {
            String json = RestClient.create().get().uri(catalogUrl).retrieve().body(String.class);
            JsonNode root = MAPPER.readTree(json);
            if (root.isArray()) {
                String base = investBase.endsWith("/") ? investBase : investBase + "/";
                for (JsonNode entry : root) {
                    JsonNode path = entry.get("path");
                    if (path != null && !path.asText().isBlank()) {
                        String p = path.asText().trim();
                        out.add(p.startsWith("http") ? p : base + p);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("[KB] invest catalog fetch/parse failed ({}): {} — grounding will escalate until reachable",
                    catalogUrl, e.getMessage());
        }
        return out;
    }

    /** Force a re-fetch on the next search (the mentor's "refresh KB"). */
    public void refresh() {
        cache.clear();
        cacheLoadedAt = 0L;
    }

    /**
     * Retrieve the top passages for a question. Ensures the cache is warm,
     * scores every cached passage, and returns the best few above zero.
     * Returns an empty result (bestScore 0) if there is no corpus or the
     * site could not be reached with a cold cache.
     */
    public RetrievalResult search(String question, int topN) {
        ensureCache();
        Set<String> queryTerms = terms(question);
        if (queryTerms.isEmpty()) return new RetrievalResult(0.0, List.of());

        List<ScoredPassage> scored = new ArrayList<>();
        double best = 0.0;
        for (List<Passage> passages : cache.values()) {
            for (Passage p : passages) {
                double s = score(queryTerms, p.text());
                if (s > 0) {
                    scored.add(new ScoredPassage(p.sourceTitle(), p.sourceUrl(), p.text(), s));
                    if (s > best) best = s;
                }
            }
        }
        scored.sort((x, y) -> Double.compare(y.score(), x.score()));
        List<ScoredPassage> top = scored.size() > topN ? scored.subList(0, topN) : scored;
        return new RetrievalResult(best, new ArrayList<>(top));
    }

    // ---------------------------------------------------------------- cache

    private void ensureCache() {
        long ageMs = System.currentTimeMillis() - cacheLoadedAt;
        boolean stale = cache.isEmpty() || ageMs > ttlMinutes * 60_000L;
        if (!stale) return;
        synchronized (this) {
            ageMs = System.currentTimeMillis() - cacheLoadedAt;
            if (!cache.isEmpty() && ageMs <= ttlMinutes * 60_000L) return;
            List<String> urls = configuredUrls();
            logger.debug("[KB] cache refresh: {} configured url(s)", urls.size());
            for (String url : urls) {
                try {
                    List<Passage> passages = fetchAndSplit(url);
                    logger.debug("[KB] fetched {} -> {} passage(s)", url, passages.size());
                    if (!passages.isEmpty()) cache.put(url, passages);   // keep last-good on empty
                } catch (Exception e) {
                    // Keep whatever we already had for this url; degrade, never fabricate.
                    logger.warn("[KB] fetch failed for {}: {}", url, e.getMessage());
                }
            }
            logger.debug("[KB] cache now holds {} url(s)", cache.size());
            if (!cache.isEmpty()) cacheLoadedAt = System.currentTimeMillis();
        }
    }

    private List<Passage> fetchAndSplit(String url) {
        String html = RestClient.create().get().uri(url).retrieve().body(String.class);
        if (html == null || html.isBlank()) return List.of();

        String title = url;
        Matcher tm = TITLE.matcher(html);
        if (tm.find()) title = stripTags(tm.group(1)).trim();

        String body = SCRIPT_STYLE.matcher(html).replaceAll(" ");
        // Split on block boundaries BEFORE stripping tags, so paragraphs survive.
        String[] blocks = body.split("(?i)</p>|<br\\s*/?>|</h[1-6]>|</li>|</div>");
        List<Passage> out = new ArrayList<>();
        for (String block : blocks) {
            String text = stripTags(block).replaceAll("\\s+", " ").trim();
            if (text.length() >= 40) {                 // ignore trivial fragments
                out.add(new Passage(title, url, text));
            }
        }
        return out;
    }

    private static String stripTags(String s) {
        return decode(TAG.matcher(s).replaceAll(" "));
    }

    private static String decode(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
    }

    // -------------------------------------------------------------- scoring

    /**
     * Lexical score = fraction of the question's distinct content terms that
     * appear in the passage. Simple, tunable, and good for a small corpus.
     */
    private static double score(Set<String> queryTerms, String passage) {
        Set<String> passageTerms = terms(passage);
        if (passageTerms.isEmpty()) return 0.0;
        int hits = 0;
        for (String q : queryTerms) {
            if (passageTerms.contains(q)) hits++;
        }
        return (double) hits / queryTerms.size();
    }

    private static Set<String> terms(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        for (String raw : text.toLowerCase().split("[^a-z0-9]+")) {
            if (raw.length() >= 3 && !STOPWORDS.contains(raw)) out.add(raw);
        }
        return out;
    }
}
