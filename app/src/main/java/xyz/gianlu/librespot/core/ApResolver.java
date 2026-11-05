package xyz.gianlu.librespot.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom ApResolver for ArchiveTune.
 * Preserves native Spotify AP ports (4070, 443) to avoid mobile carrier DPI blocking.
 * Mobile networks detect non-TLS Shannon protocol on port 443 and drop connections (TCP RST).
 * Provides robust fallback access points if apresolve.spotify.com is unreachable.
 * Note: Port 4070 is the native AP port; port 443 may trigger DPI on cellular networks.
 */
public final class ApResolver {
    private static final String HTTPS_URL = "https://apresolve.spotify.com/?";
    private static final String HTTP_URL = "http://apresolve.spotify.com/?";
    private static final Logger LOGGER = LoggerFactory.getLogger(ApResolver.class);

    private static final List<String> FALLBACK_ACCESSPOINTS = Collections.unmodifiableList(Arrays.asList(
            "ap-gew4.spotify.com:443",
            "ap-guc3.spotify.com:443",
            "ap-gue1.spotify.com:443",
            "ap-gew4.spotify.com:4070",
            "ap-guc3.spotify.com:4070",
            "ap-gue1.spotify.com:4070"
    ));

    private static final List<String> FALLBACK_DEALERS = Collections.unmodifiableList(Arrays.asList(
            "gew4-dealer.spotify.com:443",
            "guc3-dealer.spotify.com:443",
            "gue1-dealer.spotify.com:443",
            "gew1-dealer.spotify.com:443"
    ));

    private static final List<String> FALLBACK_SPCLIENTS = Collections.unmodifiableList(Arrays.asList(
            "gew4-spclient.spotify.com:443",
            "guc3-spclient.spotify.com:443",
            "gue1-spclient.spotify.com:443",
            "gew1-spclient.spotify.com:443"
    ));

    private final OkHttpClient client;
    private final Map<String, List<String>> pool = new HashMap<>(3);
    private volatile boolean poolReady = false;

    public ApResolver(OkHttpClient client) throws IOException {
        this.client = client;
        initializeFallbackPool();
        fillPool();
    }

    private void initializeFallbackPool() {
        synchronized (pool) {
            pool.put("accesspoint", new ArrayList<>(FALLBACK_ACCESSPOINTS));
            pool.put("dealer", new ArrayList<>(FALLBACK_DEALERS));
            pool.put("spclient", new ArrayList<>(FALLBACK_SPCLIENTS));
            poolReady = true;
            pool.notifyAll();
        }
    }

    private void fillPool() throws IOException {
        try {
            request("accesspoint", "dealer", "spclient");
        } catch (Exception e) {
            LOGGER.warn("Failed resolving Spotify APs from apresolve, falling back to static pool", e);
            initializeFallbackPool();
        }
    }

    public void refreshPool() throws IOException {
        fillPool();
    }

    private static List<String> getUrls(JsonObject obj, String type) {
        JsonArray array = obj.getAsJsonArray(type);
        if (array == null || array.size() == 0) return Collections.emptyList();

        List<String> list = new ArrayList<>(array.size());
        for (JsonElement el : array) {
            String val = el.getAsString();
            if (val != null && !val.isEmpty()) {
                if (val.endsWith(":4070")) {
                    list.add(val.substring(0, val.length() - 5) + ":443");
                }
                list.add(val);
            }
        }

        // If for some reason list is empty, return fallbacks
        if (list.isEmpty()) {
            if ("accesspoint".equals(type)) return new ArrayList<>(FALLBACK_ACCESSPOINTS);
            if ("dealer".equals(type)) return new ArrayList<>(FALLBACK_DEALERS);
            if ("spclient".equals(type)) return new ArrayList<>(FALLBACK_SPCLIENTS);
        }

        return list;
    }

    private void request(String... types) throws IOException {
        if (types.length == 0) throw new IllegalArgumentException("No types specified");

        // Try HTTPS first, fallback to HTTP
        Exception lastException = null;
        for (String base : new String[]{HTTPS_URL, HTTP_URL}) {
            try {
                StringBuilder builder = new StringBuilder(base);
                for (int i = 0; i < types.length; i++) {
                    if (i > 0) builder.append('&');
                    builder.append("type=").append(types[i]);
                }

                Request req = new Request.Builder()
                        .url(builder.toString())
                        .build();

                try (Response resp = client.newCall(req).execute()) {
                    if (!resp.isSuccessful()) {
                        throw new IOException("HTTP error from apresolve: " + resp.code());
                    }

                    String bodyStr = resp.body() != null ? resp.body().string() : null;
                    if (bodyStr == null || bodyStr.isEmpty()) {
                        throw new IOException("Empty response from apresolve");
                    }

                    JsonObject json = JsonParser.parseString(bodyStr).getAsJsonObject();
                    Map<String, List<String>> newMap = new HashMap<>(types.length);
                    for (String type : types) {
                        newMap.put(type, getUrls(json, type));
                    }

                    synchronized (pool) {
                        pool.putAll(newMap);
                        poolReady = true;
                        pool.notifyAll();
                    }

                    LOGGER.info("Loaded Spotify APs into pool (rewritten to 443): {}", pool);
                    return;
                }
            } catch (Exception e) {
                lastException = e;
            }
        }

        if (lastException instanceof IOException) {
            throw (IOException) lastException;
        } else if (lastException != null) {
            throw new IOException("Failed apresolve query", lastException);
        }
    }

    private void waitForPool() {
        if (!poolReady) {
            synchronized (pool) {
                while (!poolReady) {
                    try {
                        pool.wait();
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
    }

    private String getRandomOf(String type) {
        waitForPool();
        List<String> list;
        synchronized (pool) {
            list = pool.get(type);
        }

        if (list == null || list.isEmpty()) {
            if ("accesspoint".equals(type)) return FALLBACK_ACCESSPOINTS.get(ThreadLocalRandom.current().nextInt(FALLBACK_ACCESSPOINTS.size()));
            if ("dealer".equals(type)) return FALLBACK_DEALERS.get(ThreadLocalRandom.current().nextInt(FALLBACK_DEALERS.size()));
            if ("spclient".equals(type)) return FALLBACK_SPCLIENTS.get(ThreadLocalRandom.current().nextInt(FALLBACK_SPCLIENTS.size()));
            throw new IllegalStateException("No URLs in pool for type: " + type);
        }

        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    public String getRandomDealer() {
        return getRandomOf("dealer");
    }

    public String getRandomSpclient() {
        return getRandomOf("spclient");
    }

    public String getRandomAccesspoint() {
        return getRandomOf("accesspoint");
    }
}
