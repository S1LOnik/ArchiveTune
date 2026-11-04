package xyz.gianlu.librespot.dealer;

import com.google.gson.JsonObject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.spotify.canvaz.CanvazOuterClass;
import com.spotify.clienttoken.http.v0.ClientToken;
import com.spotify.connectstate.Connect;
import com.spotify.extendedmetadata.ExtendedMetadata;
import com.spotify.metadata.Metadata;
import com.spotify.playlist4.Playlist4ApiProto;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.gianlu.librespot.core.Session;
import xyz.gianlu.librespot.json.StationsWrapper;
import xyz.gianlu.librespot.mercury.MercuryClient;
import xyz.gianlu.librespot.mercury.RawMercuryRequest;
import xyz.gianlu.librespot.metadata.*;

import java.io.IOException;
import java.util.List;

/**
 * Override for librespot-lib 1.6.5's ApiClient to use Mercury instead of HTTP/spclient.
 *
 * The original ApiClient uses HTTP requests to spclient.wg.spotify.com with Bearer tokens,
 * which triggers Login5 token requests that fail in some authentication scenarios.
 *
 * This override fetches metadata directly over the authenticated Mercury (Shannon) connection
 * using hm://metadata/4/* URIs, bypassing the need for HTTP Bearer tokens entirely.
 *
 * Mercury protocol benefits:
 * - Already authenticated via Shannon connection during Session.authenticate()
 * - No additional token requests needed
 * - Returns identical protobuf responses
 * - More reliable for metadata-only operations
 */
public final class ApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiClient.class);
    private final Session session;
    private String clientToken;

    public ApiClient(@NotNull Session session) {
        this.session = session;
        LOGGER.info("ApiClient initialized with Mercury metadata + HTTP storage-resolve");
    }

    /**
     * Fetch track metadata via Mercury protocol.
     * Uses hm://metadata/4/track/<hexId> instead of HTTP spclient request.
     *
     * @param trackId The track identifier
     * @return Track metadata protobuf
     * @throws IOException if Mercury request fails
     * @throws MercuryClient.MercuryException if Mercury returns an error response
     */
    @NotNull
    public Metadata.Track getMetadata4Track(@NotNull TrackId trackId) throws IOException, MercuryClient.MercuryException {
        String uri = "hm://metadata/4/track/" + trackId.hexId();
        LOGGER.debug("Fetching track metadata via Mercury: {}", uri);

        RawMercuryRequest request = RawMercuryRequest.get(uri);
        MercuryClient.Response resp = session.mercury().sendSync(request);

        if (resp.statusCode != 200) {
            LOGGER.warn("Mercury returned status {} for track {}, attempting HTTP fallback...", resp.statusCode, trackId.hexId());
            try {
                Response httpResp = send("GET", "/metadata/4/track/" + trackId.hexId(), null, null);
                if (httpResp.code() == 200 && httpResp.body() != null) {
                    return Metadata.Track.parseFrom(httpResp.body().byteStream());
                }
            } catch (Exception ex) {
                LOGGER.warn("HTTP metadata fallback failed for track {}: {}", trackId.hexId(), ex.getMessage());
            }
            throw new IOException("Mercury returned status " + resp.statusCode + " for track " + trackId.hexId());
        }

        try {
            return Metadata.Track.parseFrom(resp.payload.stream());
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse Track protobuf from Mercury response", e);
        }
    }

    /**
     * Fetch episode metadata via Mercury protocol.
     * Uses hm://metadata/4/episode/<hexId> instead of HTTP spclient request.
     *
     * @param episodeId The episode identifier
     * @return Episode metadata protobuf
     * @throws IOException if Mercury request fails
     * @throws MercuryClient.MercuryException if Mercury returns an error response
     */
    @NotNull
    public Metadata.Episode getMetadata4Episode(@NotNull EpisodeId episodeId) throws IOException, MercuryClient.MercuryException {
        String uri = "hm://metadata/4/episode/" + episodeId.hexId();
        LOGGER.debug("Fetching episode metadata via Mercury: {}", uri);

        RawMercuryRequest request = RawMercuryRequest.get(uri);
        MercuryClient.Response resp = session.mercury().sendSync(request);

        if (resp.statusCode != 200) {
            LOGGER.warn("Mercury returned status {} for episode {}, attempting HTTP fallback...", resp.statusCode, episodeId.hexId());
            try {
                Response httpResp = send("GET", "/metadata/4/episode/" + episodeId.hexId(), null, null);
                if (httpResp.code() == 200 && httpResp.body() != null) {
                    return Metadata.Episode.parseFrom(httpResp.body().byteStream());
                }
            } catch (Exception ex) {
                LOGGER.warn("HTTP metadata fallback failed for episode {}: {}", episodeId.hexId(), ex.getMessage());
            }
            throw new IOException("Mercury returned status " + resp.statusCode + " for episode " + episodeId.hexId());
        }

        try {
            return Metadata.Episode.parseFrom(resp.payload.stream());
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse Episode protobuf from Mercury response", e);
        }
    }

    /**
     * Fetch album metadata via Mercury protocol.
     * Uses hm://metadata/4/album/<hexId> instead of HTTP spclient request.
     *
     * @param albumId The album identifier
     * @return Album metadata protobuf
     * @throws IOException if Mercury request fails
     * @throws MercuryClient.MercuryException if Mercury returns an error response
     */
    @NotNull
    public Metadata.Album getMetadata4Album(@NotNull AlbumId albumId) throws IOException, MercuryClient.MercuryException {
        String uri = "hm://metadata/4/album/" + albumId.hexId();
        LOGGER.debug("Fetching album metadata via Mercury: {}", uri);

        RawMercuryRequest request = RawMercuryRequest.get(uri);
        MercuryClient.Response resp = session.mercury().sendSync(request);

        if (resp.statusCode != 200) {
            LOGGER.warn("Mercury returned status {} for album {}, attempting HTTP fallback...", resp.statusCode, albumId.hexId());
            try {
                Response httpResp = send("GET", "/metadata/4/album/" + albumId.hexId(), null, null);
                if (httpResp.code() == 200 && httpResp.body() != null) {
                    return Metadata.Album.parseFrom(httpResp.body().byteStream());
                }
            } catch (Exception ex) {
                LOGGER.warn("HTTP metadata fallback failed for album {}: {}", albumId.hexId(), ex.getMessage());
            }
            throw new IOException("Mercury returned status " + resp.statusCode + " for album " + albumId.hexId());
        }

        try {
            return Metadata.Album.parseFrom(resp.payload.stream());
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse Album protobuf from Mercury response", e);
        }
    }

    /**
     * Fetch artist metadata via Mercury protocol.
     * Uses hm://metadata/4/artist/<hexId> instead of HTTP spclient request.
     *
     * @param artistId The artist identifier
     * @return Artist metadata protobuf
     * @throws IOException if Mercury request fails
     * @throws MercuryClient.MercuryException if Mercury returns an error response
     */
    @NotNull
    public Metadata.Artist getMetadata4Artist(@NotNull ArtistId artistId) throws IOException, MercuryClient.MercuryException {
        String uri = "hm://metadata/4/artist/" + artistId.hexId();
        LOGGER.debug("Fetching artist metadata via Mercury: {}", uri);

        RawMercuryRequest request = RawMercuryRequest.get(uri);
        MercuryClient.Response resp = session.mercury().sendSync(request);

        if (resp.statusCode != 200) {
            LOGGER.warn("Mercury returned status {} for artist {}, attempting HTTP fallback...", resp.statusCode, artistId.hexId());
            try {
                Response httpResp = send("GET", "/metadata/4/artist/" + artistId.hexId(), null, null);
                if (httpResp.code() == 200 && httpResp.body() != null) {
                    return Metadata.Artist.parseFrom(httpResp.body().byteStream());
                }
            } catch (Exception ex) {
                LOGGER.warn("HTTP metadata fallback failed for artist {}: {}", artistId.hexId(), ex.getMessage());
            }
            throw new IOException("Mercury returned status " + resp.statusCode + " for artist " + artistId.hexId());
        }

        try {
            return Metadata.Artist.parseFrom(resp.payload.stream());
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse Artist protobuf from Mercury response", e);
        }
    }

    /**
     * Fetch show metadata via Mercury protocol.
     * Uses hm://metadata/4/show/<hexId> instead of HTTP spclient request.
     *
     * @param showId The show identifier
     * @return Show metadata protobuf
     * @throws IOException if Mercury request fails
     * @throws MercuryClient.MercuryException if Mercury returns an error response
     */
    @NotNull
    public Metadata.Show getMetadata4Show(@NotNull ShowId showId) throws IOException, MercuryClient.MercuryException {
        String uri = "hm://metadata/4/show/" + showId.hexId();
        LOGGER.debug("Fetching show metadata via Mercury: {}", uri);

        RawMercuryRequest request = RawMercuryRequest.get(uri);
        MercuryClient.Response resp = session.mercury().sendSync(request);

        if (resp.statusCode != 200) {
            throw new IOException("Mercury returned status " + resp.statusCode + " for show " + showId.hexId());
        }

        try {
            return Metadata.Show.parseFrom(resp.payload.stream());
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse Show protobuf from Mercury response", e);
        }
    }

    /**
     * Resolve storage locations for audio files via Mercury protocol.
     * Uses hm://storage-resolve/files/audio/interactive/<fileId> instead of HTTP spclient request.
     *
     * This is called by PlayableContentFeeder.resolveStorageInteractive() to get CDN URLs
     * for streaming audio content.
     *
     * @param fileId The hex-encoded file identifier
     * @return StorageResolveResponse containing CDN URLs
     * @throws IOException if Mercury request fails
     * @throws MercuryClient.MercuryException if Mercury returns an error response
     */
    @NotNull
    public com.spotify.storage.StorageResolve.StorageResolveResponse resolveStorageInteractive(@NotNull String fileId)
            throws IOException, MercuryClient.MercuryException {
        String path = "/storage-resolve/files/audio/interactive/" + fileId;
        LOGGER.debug("Resolving storage via HTTP: {}", path);

        Response resp = send("GET", path, null, null);
        if (resp.code() != 200) {
            throw new IOException("HTTP returned status " + resp.code() + " for storage resolve " + fileId);
        }

        okhttp3.ResponseBody body = resp.body();
        if (body == null) {
            throw new IOException("Empty response body for storage resolve " + fileId);
        }

        try {
            return com.spotify.storage.StorageResolve.StorageResolveResponse.parseFrom(body.byteStream());
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse StorageResolveResponse protobuf from HTTP response", e);
        }
    }

    /**
     * Resolve storage locations for audio files (generic version).
     * Uses hm://storage-resolve/files/audio/interactive/<fileId> instead of HTTP spclient request.
     *
     * @param fileId The hex-encoded file identifier
     * @return StorageResolveResponse containing CDN URLs
     * @throws IOException if Mercury request fails
     * @throws MercuryClient.MercuryException if Mercury returns an error response
     */
    @NotNull
    public com.spotify.storage.StorageResolve.StorageResolveResponse resolveStorage(@NotNull String fileId)
            throws IOException, MercuryClient.MercuryException {
        // Use the same interactive endpoint as resolveStorageInteractive
        return resolveStorageInteractive(fileId);
    }

    // ============================================================================
    // Methods below are NOT reimplemented - they require HTTP/spclient access
    // If called, they will throw UnsupportedOperationException with guidance
    // ============================================================================

    /**
     * Canvas fetching requires HTTP spclient access - not available via Mercury.
     * This feature is non-critical for audio playback.
     */
    @NotNull
    public CanvazOuterClass.EntityCanvazResponse getCanvases(@NotNull CanvazOuterClass.EntityCanvazRequest request)
            throws IOException, MercuryClient.MercuryException {
        throw new UnsupportedOperationException(
                "getCanvases() requires HTTP spclient access and is not available in Mercury-only mode. " +
                "Canvas animations are a non-critical visual feature and can be safely skipped."
        );
    }

    /**
     * Extended metadata fetching requires HTTP spclient access - not available via Mercury.
     * This feature is non-critical for audio playback.
     */
    @NotNull
    public ExtendedMetadata.BatchedExtensionResponse getExtendedMetadata(@NotNull ExtendedMetadata.BatchedEntityRequest request)
            throws IOException, MercuryClient.MercuryException {
        throw new UnsupportedOperationException(
                "getExtendedMetadata() requires HTTP spclient access and is not available in Mercury-only mode. " +
                "Extended metadata is non-critical and basic metadata via Mercury is sufficient for playback."
        );
    }

    /**
     * Playlist fetching via HTTP spclient - Mercury may have an alternative endpoint.
     * TODO: Research if hm://playlist/* endpoints exist in Mercury protocol.
     */
    @NotNull
    public Playlist4ApiProto.SelectedListContent getPlaylist(@NotNull PlaylistId playlistId)
            throws IOException, MercuryClient.MercuryException {
        throw new UnsupportedOperationException(
                "getPlaylist() is not yet implemented in Mercury-only mode. " +
                "Mercury may support hm://playlist/* endpoints - needs research."
        );
    }

    /**
     * User profile fetching requires HTTP spclient JSON API - not available via Mercury.
     */
    @NotNull
    public JsonObject getUserProfile(@NotNull String username, Integer playlistLimit, Integer artistLimit)
            throws IOException, MercuryClient.MercuryException {
        throw new UnsupportedOperationException(
                "getUserProfile() requires HTTP spclient JSON API and is not available in Mercury-only mode."
        );
    }

    /**
     * User followers fetching requires HTTP spclient JSON API - not available via Mercury.
     */
    @NotNull
    public JsonObject getUserFollowers(@NotNull String username) throws IOException, MercuryClient.MercuryException {
        throw new UnsupportedOperationException(
                "getUserFollowers() requires HTTP spclient JSON API and is not available in Mercury-only mode."
        );
    }

    /**
     * User following fetching requires HTTP spclient JSON API - not available via Mercury.
     */
    @NotNull
    public JsonObject getUserFollowing(@NotNull String username) throws IOException, MercuryClient.MercuryException {
        throw new UnsupportedOperationException(
                "getUserFollowing() requires HTTP spclient JSON API and is not available in Mercury-only mode."
        );
    }

    /**
     * Radio generation requires HTTP spclient access - not available via Mercury.
     */
    @NotNull
    public JsonObject getRadioForTrack(@NotNull PlayableId playableId) throws IOException, MercuryClient.MercuryException {
        throw new UnsupportedOperationException(
                "getRadioForTrack() requires HTTP spclient JSON API and is not available in Mercury-only mode."
        );
    }

    /**
     * Apollo station fetching requires HTTP spclient access - not available via Mercury.
     */
    @NotNull
    public StationsWrapper getApolloStation(@NotNull String context, @NotNull List<String> previousTracks,
                                            int count, boolean autoplay)
            throws IOException, MercuryClient.MercuryException {
        throw new UnsupportedOperationException(
                "getApolloStation() requires HTTP spclient JSON API and is not available in Mercury-only mode."
        );
    }

    /**
     * Connect state updates require HTTP spclient access - not available via Mercury.
     * Spotify Connect functionality is handled by DealerClient instead.
     */
    public void putConnectState(@NotNull String connectionId, @NotNull Connect.PutStateRequest request)
            throws IOException, MercuryClient.MercuryException {
        throw new UnsupportedOperationException(
                "putConnectState() requires HTTP spclient access and is not available in Mercury-only mode. " +
                "Spotify Connect state is managed via DealerClient WebSocket connection instead."
        );
    }

    // ============================================================================
    // Low-level HTTP methods - implemented via Mercury protocol
    // ============================================================================

    /**
     * Async send is not implemented - use synchronous Mercury requests instead.
     */
    public void sendAsync(@NotNull String method, @NotNull String path, Headers headers,
                         RequestBody body, @NotNull Callback callback)
            throws IOException, MercuryClient.MercuryException {
        throw new UnsupportedOperationException(
                "HTTP sendAsync() not available in Mercury-only mode. Use synchronous send() instead."
        );
    }

    /**
     * Translate HTTP-style requests to Mercury protocol requests.
     *
     * This is called by PlayableContentFeeder.resolveStorageInteractive() to fetch CDN URLs.
     * We intercept HTTP GET requests and route them through Mercury instead.
     *
     * Supported paths:
     * - /storage-resolve/files/audio/interactive/<fileId> → hm://storage-resolve/files/audio/interactive/<fileId>
     *
     * @param method HTTP method (only GET is supported)
     * @param path The request path (e.g., "/storage-resolve/files/audio/interactive/...")
     * @param headers Request headers (unused in Mercury mode)
     * @param body Request body (unused for GET requests)
     * @return OkHttp Response containing the protobuf payload
     */
    @NotNull
    public Response send(@NotNull String method, @NotNull String path, Headers headers, RequestBody body)
            throws IOException, MercuryClient.MercuryException {
        return send(method, path, headers, body, 0);
    }

    /**
     * Translate HTTP-style requests to Mercury protocol requests (with timeout parameter).
     *
     * @param method HTTP method (only GET is supported)
     * @param path The request path (e.g., "/storage-resolve/files/audio/interactive/...")
     * @param headers Request headers (unused in Mercury mode)
     * @param body Request body (unused for GET requests)
     * @param timeout Request timeout in milliseconds (unused in Mercury mode)
     * @return OkHttp Response containing the protobuf payload
     */
    @NotNull
    private synchronized ClientToken.ClientTokenResponse clientToken() throws IOException {
        ClientToken.ClientTokenRequest request = ClientToken.ClientTokenRequest.newBuilder()
                .setClientData(ClientToken.ClientDataRequest.newBuilder()
                        .setClientId("65b708073fc0480ea92a077233ca87bd")
                        .setClientVersion(xyz.gianlu.librespot.Version.versionNumber())
                        .setConnectivitySdkData(com.spotify.clienttoken.data.v0.Connectivity.ConnectivitySdkData.newBuilder()
                                .setDeviceId(session.deviceId())
                                .setPlatformSpecificData(com.spotify.clienttoken.data.v0.Connectivity.PlatformSpecificData.newBuilder()
                                        .setWindows(com.spotify.clienttoken.data.v0.Connectivity.NativeWindowsData.newBuilder()
                                                .setSomething1(10)
                                                .setSomething3(21370)
                                                .setSomething4(2)
                                                .setSomething6(9)
                                                .setSomething7(332)
                                                .setSomething8(34404)
                                                .setSomething10(true)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        okhttp3.Request okHttpRequest = new okhttp3.Request.Builder()
                .url("https://clienttoken.spotify.com/v1/clienttoken")
                .header("Accept", "application/x-protobuf")
                .header("Content-Encoding", "")
                .post(protoBody(request))
                .build();

        LOGGER.info("Requesting Spotify client-token from https://clienttoken.spotify.com/v1/clienttoken...");
        Response response = session.client().newCall(okHttpRequest).execute();
        if (response.code() != 200) {
            String err = "";
            try {
                if (response.body() != null) err = response.peekBody(512).string();
            } catch (Exception ignored) {}
            android.util.Log.e("SPOTIFY_API", "clienttoken HTTP " + response.code() + " " + response.message() + " - " + err);
            throw new IOException("Failed to get client-token: HTTP " + response.code() + " " + response.message() + " - " + err);
        }

        okhttp3.ResponseBody body = response.body();
        if (body == null) throw new IOException("Empty response body from clienttoken");
        return ClientToken.ClientTokenResponse.parseFrom(body.byteStream());
    }

    @NotNull
    public Response send(@NotNull String method, @NotNull String path, Headers headers, RequestBody body, int timeout)
            throws IOException, MercuryClient.MercuryException {

        if (this.clientToken == null) {
            try {
                android.util.Log.e("SPOTIFY_API", "Requesting clientToken from clienttoken.spotify.com...");
                ClientToken.ClientTokenResponse tokenResp = clientToken();
                if (tokenResp.hasGrantedToken()) {
                    this.clientToken = tokenResp.getGrantedToken().getToken();
                    android.util.Log.e("SPOTIFY_API", "clientToken SUCCESS: " + this.clientToken.substring(0, Math.min(15, this.clientToken.length())) + "...");
                } else {
                    android.util.Log.e("SPOTIFY_API", "clientToken response has no granted token: " + tokenResp);
                }
            } catch (Exception e) {
                android.util.Log.e("SPOTIFY_API", "clientToken FAILED: " + e.getMessage(), e);
            }
        }

        String url = "https://spclient.wg.spotify.com" + (path.startsWith("/") ? path : "/" + path);
        okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(url);

        if (body != null) {
            builder.method(method, body);
        } else if ("GET".equalsIgnoreCase(method)) {
            builder.get();
        } else if ("POST".equalsIgnoreCase(method)) {
            builder.post(okhttp3.RequestBody.create(new byte[0], null));
        } else {
            builder.method(method, null);
        }

        if (headers != null) {
            builder.headers(headers);
        }

        // Add Authorization Bearer token from session
        String token = null;
        try {
            token = session.tokens().get("playlist-read");
        } catch (Exception ex) {
            android.util.Log.e("SPOTIFY_API", "Failed to get token for playlist-read: " + ex.getMessage(), ex);
        }

        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }

        if (clientToken != null && !clientToken.isEmpty()) {
            builder.header("client-token", clientToken);
        }

        builder.header("Accept", "application/x-protobuf");
        builder.header("User-Agent", "Spotify/8.8.84 Android/34");

        android.util.Log.e("SPOTIFY_API", "Sending HTTP " + method + " to " + url + " [Bearer=" + (token != null ? token.substring(0, Math.min(10, token.length())) + "..." : "none") + ", client-token=" + (clientToken != null ? clientToken.substring(0, Math.min(10, clientToken.length())) + "..." : "none") + "]");
        Response resp = session.client().newCall(builder.build()).execute();
        android.util.Log.e("SPOTIFY_API", "Response: " + resp.code() + " " + resp.message());
        if (resp.code() == 401) {
            android.util.Log.w("SPOTIFY_API", "Received 401 Unauthorized from Spotify. Invalidating tokens and retrying with fresh token...");
            session.tokens().invalidateTokens();

            // Force refresh token in SpotifySessionManager
            try {
                Class<?> managerClass = Class.forName("moe.rukamori.archivetune.spotify.session.SpotifySessionManager");
                Object managerInstance = null;
                try {
                    managerInstance = managerClass.getMethod("get").invoke(null);
                } catch (Exception ignored) {}
                if (managerInstance == null) {
                    try {
                        java.lang.reflect.Field instField = managerClass.getDeclaredField("instance");
                        instField.setAccessible(true);
                        managerInstance = instField.get(null);
                    } catch (Exception ignored) {}
                }
                if (managerInstance != null) {
                    java.lang.reflect.Method forceMethod = managerClass.getMethod("forceRefreshTokenBlocking");
                    forceMethod.invoke(managerInstance);
                }
            } catch (Throwable t) {
                android.util.Log.e("SPOTIFY_API", "Failed to force refresh token: " + t.getMessage(), t);
            }

            // Also refresh client-token
            try {
                ClientToken.ClientTokenResponse tokenResp = clientToken();
                if (tokenResp.hasGrantedToken()) {
                    this.clientToken = tokenResp.getGrantedToken().getToken();
                }
            } catch (Exception ignored) {}

            // Re-acquire fresh token
            token = null;
            try {
                token = session.tokens().get("playlist-read");
            } catch (Exception ex) {
                android.util.Log.e("SPOTIFY_API", "Failed to get fresh token after 401: " + ex.getMessage(), ex);
            }

            okhttp3.Request.Builder retryBuilder = new okhttp3.Request.Builder().url(url);
            if (body != null) {
                retryBuilder.method(method, body);
            } else if ("GET".equalsIgnoreCase(method)) {
                retryBuilder.get();
            } else if ("POST".equalsIgnoreCase(method)) {
                retryBuilder.post(okhttp3.RequestBody.create(new byte[0], null));
            } else {
                retryBuilder.method(method, null);
            }
            if (headers != null) {
                retryBuilder.headers(headers);
            }
            if (token != null && !token.isEmpty()) {
                retryBuilder.header("Authorization", "Bearer " + token);
            }
            if (clientToken != null && !clientToken.isEmpty()) {
                retryBuilder.header("client-token", clientToken);
            }
            retryBuilder.header("Accept", "application/x-protobuf");
            retryBuilder.header("User-Agent", "Spotify/8.8.84 Android/34");

            resp.close();
            resp = session.client().newCall(retryBuilder.build()).execute();
            android.util.Log.i("SPOTIFY_API", "Retry Response: " + resp.code() + " " + resp.message());
        }

        if (resp.code() != 200) {
            String peek = "";
            try {
                if (resp.body() != null) peek = resp.peekBody(2048).string();
            } catch (Exception ignored) {}
            android.util.Log.e("SPOTIFY_API", "Response headers: " + resp.headers() + " | Body: " + peek);
        }
        return resp;
    }

    public void setClientToken(@NotNull String token) {
        this.clientToken = token;
        LOGGER.debug("Updated clientToken: {}", token);
    }

    /**
     * Helper method for protobuf request bodies.
     */
    public static RequestBody protoBody(@NotNull com.google.protobuf.Message message) {
        return RequestBody.create(message.toByteArray(), okhttp3.MediaType.parse("application/x-protobuf"));
    }
}
