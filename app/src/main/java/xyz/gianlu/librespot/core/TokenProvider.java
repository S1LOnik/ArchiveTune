package xyz.gianlu.librespot.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import com.spotify.login5v3.ClientInfoOuterClass;
import com.spotify.login5v3.Credentials;
import com.spotify.login5v3.Login5;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.gianlu.librespot.common.Utils;
import xyz.gianlu.librespot.mercury.MercuryClient;
import xyz.gianlu.librespot.mercury.RawMercuryRequest;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Local replacement for librespot-lib 1.6.5's token provider.
 *
 * Spotify no longer consistently authorizes the legacy Mercury Keymaster endpoint.
 * Request the session access token through Login5 instead.
 */
public final class TokenProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenProvider.class);
    private static final int TOKEN_EXPIRE_THRESHOLD = 10;

    private final Session session;
    private final List<StoredToken> tokens = new ArrayList<>();

    TokenProvider(@NotNull Session session) {
        this.session = session;
    }

    public synchronized void invalidateTokens() {
        tokens.clear();
        LOGGER.info("Invalidated all cached Spotify tokens");
    }

    @Nullable
    private StoredToken findTokenWithAllScopes(@NotNull String[] scopes) {
        for (StoredToken token : tokens) {
            if (token.hasScopes(scopes)) return token;
        }

        return null;
    }

    @NotNull
    public synchronized StoredToken getToken(@NotNull String... scopes) throws IOException, MercuryClient.MercuryException {
        if (scopes.length == 0) throw new IllegalArgumentException();

        StoredToken token = findTokenWithAllScopes(scopes);
        if (token != null) {
            if (token.expired()) tokens.remove(token);
            else return token;
        }

        android.util.Log.e("SPOTIFY_AUTH", "getToken() requested for scopes: " + Arrays.toString(scopes));

        // 1. Try to use saved OAuth token from SpotifySessionManager (Web API / Android client token)
        try {
            String savedAccessToken = getSavedAccessTokenFromSessionManager();
            if (savedAccessToken != null && !savedAccessToken.isEmpty()) {
                android.util.Log.e("SPOTIFY_AUTH", "Using OAuth access token from SpotifySessionManager: " + savedAccessToken.substring(0, Math.min(15, savedAccessToken.length())) + "...");
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("expiresIn", 3600); // Default 1 hour expiry
                responseJson.addProperty("accessToken", savedAccessToken);
                responseJson.addProperty("tokenType", "Bearer");

                JsonArray scopeArray = new JsonArray();
                for (String scope : scopes) scopeArray.add(scope);
                responseJson.add("scope", scopeArray);
                token = new StoredToken(responseJson);

                // [FIX-ZCODE] C1 (P0 из BOT_REPORTS): не кешируем OAuth-токен из SessionManager с
                // фиктивным expiresIn=3600 — реальный срок жизни может быть меньше, и закешированный
                // токен затем отдавался уже протухшим (HTTP 401 -> ExoPlayer 3003 -> ложный фоллбэк).
                // Берем актуальный токен при каждом вызове: внутри SessionManager свой кеш и refresh.
                return token;
            }
        } catch (Exception ex) {
            android.util.Log.e("SPOTIFY_AUTH", "Could not retrieve OAuth token: " + ex.getMessage(), ex);
        }

        // 2. Try Mercury Keymaster (native AP session token generation)
        try {
            String uri = String.format("hm://keymaster/token/authenticated?scope=%s&client_id=%s&device_id=%s",
                    String.join(",", scopes),
                    "65b708073fc0480ea92a077233ca87bd",
                    session.deviceId());
            android.util.Log.e("SPOTIFY_AUTH", "Requesting Mercury Keymaster: " + uri);
            MercuryClient.Response resp = session.mercury().sendSync(RawMercuryRequest.get(uri));
            android.util.Log.e("SPOTIFY_AUTH", "Mercury Keymaster statusCode: " + resp.statusCode);
            if (resp.statusCode == 200) {
                byte[] bytes = resp.payload.stream().readAllBytes();
                String jsonStr = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                android.util.Log.e("SPOTIFY_AUTH", "Mercury Keymaster SUCCESS payload: " + jsonStr);
                JsonObject obj = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                token = new StoredToken(obj);
                tokens.add(token);
                return token;
            }
        } catch (Exception ex) {
            android.util.Log.e("SPOTIFY_AUTH", "Mercury Keymaster error: " + ex.getMessage(), ex);
        }

        // 3. Fallback to Login5 if OAuth and Mercury Keymaster are unavailable
        try {
            if (session.apWelcome() != null && session.apWelcome().hasReusableAuthCredentials()) {
                Credentials.StoredCredential credentials = Credentials.StoredCredential.newBuilder()
                        .setUsername(session.username())
                        .setData(ByteString.copyFrom(session.apWelcome().getReusableAuthCredentials().toByteArray()))
                        .build();

                Login5.LoginRequest.Builder requestBuilder = Login5.LoginRequest.newBuilder()
                        .setStoredCredential(credentials)
                        .setClientInfo(ClientInfoOuterClass.ClientInfo.newBuilder()
                                .setClientId("65b708073fc0480ea92a077233ca87bd")
                                .setDeviceId(session.deviceId())
                                .build());

                android.util.Log.e("SPOTIFY_AUTH", "Requesting Login5 token with client_id 65b708073fc0480ea92a077233ca87bd, deviceId " + session.deviceId());
                Login5.LoginResponse response = new Login5Api(session).login5(requestBuilder.build());

                if (response.hasOk()) {
                    Login5.LoginOk ok = response.getOk();
                    JsonObject responseJson = new JsonObject();
                    responseJson.addProperty("expiresIn", ok.getAccessTokenExpiresIn());
                    responseJson.addProperty("accessToken", ok.getAccessToken());
                    responseJson.addProperty("tokenType", "Bearer");

                    JsonArray scopeArray = new JsonArray();
                    for (String scope : scopes) scopeArray.add(scope);
                    responseJson.add("scope", scopeArray);
                    token = new StoredToken(responseJson);

                    android.util.Log.e("SPOTIFY_AUTH", "Login5 SUCCESS! Token: " + ok.getAccessToken().substring(0, Math.min(15, ok.getAccessToken().length())) + "..., valid for " + ok.getAccessTokenExpiresIn() + "s");
                    tokens.add(token);
                    return token;
                } else {
                    android.util.Log.e("SPOTIFY_AUTH", "Login5 REJECTED: " + response.getError());
                }
            } else {
                android.util.Log.e("SPOTIFY_AUTH", "AP welcome is null or has no reusable credentials! apWelcome=" + session.apWelcome());
            }
        } catch (Exception ex) {
            android.util.Log.e("SPOTIFY_AUTH", "Login5 FAILED with exception: " + ex.getMessage(), ex);
        }

        throw new IOException("Failed to obtain Spotify access token via both OAuth and Login5");
    }

    @Nullable
    private String getSavedAccessTokenFromSessionManager() {
        try {
            Class<?> managerClass = Class.forName("moe.rukamori.archivetune.spotify.session.SpotifySessionManager");
            Object instance = null;
            try {
                instance = managerClass.getMethod("get").invoke(null);
            } catch (Exception ignored) {}
            if (instance == null) {
                try {
                    java.lang.reflect.Field instField = managerClass.getDeclaredField("instance");
                    instField.setAccessible(true);
                    instance = instField.get(null);
                } catch (Exception ignored) {}
            }
            if (instance == null) {
                android.util.Log.e("SPOTIFY_AUTH", "SpotifySessionManager instance is null");
                return null;
            }

            Object credentialsStore = null;
            try {
                credentialsStore = managerClass.getMethod("getCredentialsStore").invoke(instance);
            } catch (Exception e) {
                try {
                    java.lang.reflect.Field csField = managerClass.getDeclaredField("credentialsStore");
                    csField.setAccessible(true);
                    credentialsStore = csField.get(instance);
                } catch (Exception ignored) {}
            }
            if (credentialsStore == null) {
                android.util.Log.e("SPOTIFY_AUTH", "credentialsStore is null");
                return null;
            }

            Class<?> storeClass = credentialsStore.getClass();
            String accessToken = (String) storeClass.getMethod("getSavedAccessToken").invoke(credentialsStore);
            Long expiresAt = (Long) storeClass.getMethod("getSavedTokenExpiresAt").invoke(credentialsStore);
            String refreshToken = (String) storeClass.getMethod("getSavedRefreshToken").invoke(credentialsStore);

            android.util.Log.e("SPOTIFY_AUTH", "Store check: token=" + (accessToken != null ? accessToken.substring(0, Math.min(10, accessToken.length())) + "..." : "null") + ", expiresAt=" + expiresAt + ", now=" + System.currentTimeMillis() + ", refreshToken=" + (refreshToken != null ? "present" : "null"));

            // Check if token is valid
            if (accessToken != null && !accessToken.isEmpty() && expiresAt != null && expiresAt > 0L) {
                if (System.currentTimeMillis() < (expiresAt - 60_000L)) {
                    return accessToken;
                }
                android.util.Log.e("SPOTIFY_AUTH", "Token expired (expiresAt=" + expiresAt + ", now=" + System.currentTimeMillis() + "), attempting refresh...");
            }

            // 1. Try forceRefreshTokenBlocking from SpotifySessionManager
            try {
                java.lang.reflect.Method forceMethod = managerClass.getMethod("forceRefreshTokenBlocking");
                String refreshed = (String) forceMethod.invoke(instance);
                if (refreshed != null && !refreshed.isEmpty()) {
                    android.util.Log.e("SPOTIFY_AUTH", "forceRefreshTokenBlocking returned fresh token: " + refreshed.substring(0, Math.min(10, refreshed.length())) + "...");
                    return refreshed;
                }
            } catch (Throwable t) {
                android.util.Log.w("SPOTIFY_AUTH", "forceRefreshTokenBlocking call failed: " + t.getMessage());
            }

            // 2. Fallback to direct OAuth refresh if refreshToken exists
            if (refreshToken != null && !refreshToken.isEmpty()) {
                try {
                    Class<?> oauthClass = Class.forName("moe.rukamori.archivetune.spotify.session.SpotifyOAuthLoopbackServer");
                    Object tokenResponse = oauthClass.getMethod("refreshAccessTokenBlocking", String.class).invoke(null, refreshToken);
                    if (tokenResponse != null) {
                        String newAccess = (String) tokenResponse.getClass().getMethod("getAccessToken").invoke(tokenResponse);
                        Long expiresIn = (Long) tokenResponse.getClass().getMethod("getExpiresIn").invoke(tokenResponse);
                        String newRefresh = (String) tokenResponse.getClass().getMethod("getRefreshToken").invoke(tokenResponse);
                        if (newRefresh == null) newRefresh = refreshToken;

                        storeClass.getMethod("saveTokens", String.class, String.class, long.class)
                                .invoke(credentialsStore, newAccess, newRefresh, expiresIn != null ? expiresIn : 3600L);
                        android.util.Log.e("SPOTIFY_AUTH", "Token refreshed successfully: " + newAccess.substring(0, Math.min(10, newAccess.length())) + "...");
                        return newAccess;
                    }
                } catch (Exception re) {
                    android.util.Log.e("SPOTIFY_AUTH", "Failed to refresh token: " + re.getMessage(), re);
                }
            }

            // 3. Try to sync token from saved sp_dc cookie via syncTokenFromCookiesBlocking
            try {
                java.lang.reflect.Method syncMethod = managerClass.getMethod("syncTokenFromCookiesBlocking");
                String syncedToken = (String) syncMethod.invoke(instance);
                if (syncedToken != null && !syncedToken.isEmpty()) {
                    android.util.Log.e("SPOTIFY_AUTH", "syncTokenFromCookiesBlocking returned token: " + syncedToken.substring(0, Math.min(10, syncedToken.length())) + "...");
                    return syncedToken;
                }
            } catch (Throwable t) {
                android.util.Log.e("SPOTIFY_AUTH", "syncTokenFromCookiesBlocking call failed: " + t.getMessage());
            }

            return null;
        } catch (Exception e) {
            android.util.Log.e("SPOTIFY_AUTH", "Failed to access SpotifySessionManager: " + e.getMessage(), e);
        }
        return null;
    }

    @NotNull
    public String get(@NotNull String scope) throws IOException, MercuryClient.MercuryException {
        return getToken(scope).accessToken;
    }

    public static class StoredToken {
        public final int expiresIn;
        public final String accessToken;
        public final String[] scopes;
        public final long timestamp;

        private StoredToken(@NotNull JsonObject obj) {
            timestamp = TimeProvider.currentTimeMillis();
            expiresIn = obj.get("expiresIn").getAsInt();
            accessToken = obj.get("accessToken").getAsString();

            JsonArray array = obj.getAsJsonArray("scope");
            scopes = new String[array.size()];
            for (int i = 0; i < array.size(); i++) scopes[i] = array.get(i).getAsString();
        }

        public boolean expired() {
            return timestamp + (expiresIn - TOKEN_EXPIRE_THRESHOLD) * 1000L < TimeProvider.currentTimeMillis();
        }

        @Override
        public String toString() {
            return "StoredToken{" +
                    "expiresIn=" + expiresIn +
                    ", accessToken='" + Utils.truncateMiddle(accessToken, 12) + '\'' +
                    ", scopes=" + Arrays.toString(scopes) +
                    ", timestamp=" + timestamp +
                    '}';
        }

        public boolean hasScope(@NotNull String scope) {
            for (String item : scopes) {
                if (Objects.equals(item, scope)) return true;
            }

            return false;
        }

        public boolean hasScopes(@NotNull String[] scopes) {
            for (String scope : scopes) {
                if (!hasScope(scope)) return false;
            }

            return true;
        }
    }
}
