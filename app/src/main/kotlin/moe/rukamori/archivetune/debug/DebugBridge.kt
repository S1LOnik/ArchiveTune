package moe.rukamori.archivetune.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.EntryPointAccessors
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.di.SpotifyEntryPoint
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.SpotifyPlaylistQueue
import moe.rukamori.archivetune.spotify.session.SpotifySessionManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object DebugBridge {
    private const val TAG = "DebugBridge"
    const val PORT = 8765

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var receiverRegistered = false

    var currentActivity: MainActivity? = null
    var navController: NavHostController? = null
    var playerConnection: PlayerConnection? = null

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.getStringExtra("action") ?: intent.action ?: return
            Log.i(TAG, "Broadcast received: action=$action")
            handleCommand(action, intent.extras?.let { bundle ->
                bundle.keySet().associateWith { bundle.get(it)?.toString().orEmpty() }
            } ?: emptyMap())
        }
    }

    fun attach(activity: MainActivity, nav: NavHostController, connection: PlayerConnection?) {
        currentActivity = activity
        navController = nav
        playerConnection = connection
        Log.i(TAG, "Attached to MainActivity, navController, playerConnection: ${connection != null}")

        if (!receiverRegistered) {
            try {
                val filter = IntentFilter("moe.rukamori.archivetune.DEBUG")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.registerReceiver(debugReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    activity.registerReceiver(debugReceiver, filter)
                }
                receiverRegistered = true
                Log.i(TAG, "Registered debug BroadcastReceiver: moe.rukamori.archivetune.DEBUG")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register broadcast receiver: ${e.message}")
            }
        }

        startServer()
    }

    fun updatePlayerConnection(connection: PlayerConnection?) {
        playerConnection = connection
        Log.i(TAG, "Updated playerConnection: ${connection != null}")
    }

    private fun startServer() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.i(TAG, "Debug HTTP server started on port $PORT")
                while (isActive && isRunning) {
                    val client = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        break
                    }
                    scope.launch { handleClient(client) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start debug HTTP server: ${e.message}", e)
            } finally {
                isRunning = false
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                val writer = PrintWriter(s.getOutputStream(), true)

                val requestLine = reader.readLine() ?: return@use
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@use

                val rawUri = parts[1]
                val uriParts = rawUri.split("?", limit = 2)
                val path = uriParts[0]
                val queryParams = if (uriParts.size > 1) parseQuery(uriParts[1]) else emptyMap()

                Log.d(TAG, "HTTP ${parts[0]} $path $queryParams")

                val responseJson = when (path) {
                    "/", "/status", "/state" -> getStatusJson()
                    "/nav", "/navigate" -> {
                        val route = queryParams["route"]
                        if (route.isNullOrBlank()) {
                            JSONObject().put("status", "error").put("message", "Missing 'route' param")
                        } else {
                            navigate(route)
                            JSONObject().put("status", "ok").put("navigatedTo", route)
                        }
                    }
                    "/action" -> {
                        val name = queryParams["name"] ?: "unknown"
                        val result = executeAction(name, queryParams)
                        JSONObject().put("status", if (result) "ok" else "error").put("action", name)
                    }
                    "/oauth_url" -> {
                        val mgr = SpotifySessionManager.get()
                        val act = currentActivity
                        if (mgr != null && act != null) {
                            mgr.startOAuthBrowserLogin(act)
                            JSONObject().put("status", "ok").put("message", "OAuth loopback server started and browser launched")
                        } else {
                            JSONObject().put("status", "error").put("message", "Manager or Activity null")
                        }
                    }
                    "/spotify_search" -> {
                        val query = queryParams["q"] ?: ""
                        val future = java.util.concurrent.CompletableFuture<JSONObject>()
                        scope.launch {
                            try {
                                val act = currentActivity
                                if (act != null) {
                                    val entryPoint = EntryPointAccessors.fromApplication(
                                        act.applicationContext,
                                        SpotifyEntryPoint::class.java,
                                    )
                                    val repo = entryPoint.spotifyLibraryRepository()
                                    val results = repo.searchTracks(query = query, limit = 10)
                                    val resJson = JSONObject().put("status", "ok").put("count", results.size)
                                    val arr = JSONArray()
                                    results.forEach { t ->
                                        arr.put(
                                            JSONObject()
                                                .put("id", t.id)
                                                .put("name", t.name)
                                                .put("artists", t.artists.joinToString { it.name })
                                                .put("album", t.album?.name),
                                        )
                                    }
                                    resJson.put("tracks", arr)
                                    future.complete(resJson)
                                } else {
                                    future.complete(JSONObject().put("status", "error").put("message", "Activity null"))
                                }
                            } catch (e: Exception) {
                                future.complete(JSONObject().put("status", "error").put("message", e.message))
                            }
                        }
                        try {
                            future.get(10, java.util.concurrent.TimeUnit.SECONDS)
                        } catch (e: Exception) {
                            JSONObject().put("status", "error").put("message", e.message)
                        }
                    }
                    "/spotify_playlists" -> {
                        val future = java.util.concurrent.CompletableFuture<JSONObject>()
                        scope.launch {
                            try {
                                val act = currentActivity
                                if (act != null) {
                                    val entryPoint = EntryPointAccessors.fromApplication(
                                        act.applicationContext,
                                        SpotifyEntryPoint::class.java,
                                    )
                                    val repo = entryPoint.spotifyLibraryRepository()
                                    val playlists = repo.getUserPlaylists()
                                    val resJson = JSONObject().put("status", "ok").put("count", playlists.size)
                                    val arr = JSONArray()
                                    playlists.forEach { p ->
                                        arr.put(
                                            JSONObject()
                                                .put("id", p.id)
                                                .put("name", p.name)
                                                .put("itemCount", p.tracks?.total ?: 0)
                                                .put("owner", p.owner?.displayName ?: p.owner?.id),
                                        )
                                    }
                                    resJson.put("playlists", arr)
                                    future.complete(resJson)
                                } else {
                                    future.complete(JSONObject().put("status", "error").put("message", "Activity null"))
                                }
                            } catch (e: Exception) {
                                future.complete(JSONObject().put("status", "error").put("message", e.message))
                            }
                        }
                        try {
                            future.get(10, java.util.concurrent.TimeUnit.SECONDS)
                        } catch (e: Exception) {
                            JSONObject().put("status", "error").put("message", e.message)
                        }
                    }
                    "/spotify_add" -> {
                        val playlistId = queryParams["playlistId"]
                        val trackUri = queryParams["trackUri"]
                        val future = java.util.concurrent.CompletableFuture<JSONObject>()
                        scope.launch {
                            try {
                                val act = currentActivity
                                if (act != null && !trackUri.isNullOrBlank()) {
                                    val entryPoint = EntryPointAccessors.fromApplication(
                                        act.applicationContext,
                                        SpotifyEntryPoint::class.java,
                                    )
                                    val repo = entryPoint.spotifyLibraryRepository()
                                    if (playlistId == "liked" || playlistId.isNullOrBlank()) {
                                        val res = repo.addToLibrary(listOf(trackUri))
                                        future.complete(JSONObject().put("status", if (res.isSuccess) "ok" else "failed").put("target", "liked"))
                                    } else {
                                        val res = repo.addTracksToPlaylist(playlistId, listOf(trackUri))
                                        future.complete(JSONObject().put("status", if (res.isSuccess) "ok" else "failed").put("target", playlistId))
                                    }
                                } else {
                                    future.complete(JSONObject().put("status", "error").put("message", "Missing params or activity"))
                                }
                            } catch (e: Exception) {
                                future.complete(JSONObject().put("status", "error").put("message", e.message))
                            }
                        }
                        try {
                            future.get(10, java.util.concurrent.TimeUnit.SECONDS)
                        } catch (e: Exception) {
                            JSONObject().put("status", "error").put("message", e.message)
                        }
                    }
                    "/play_spotify_search" -> {
                        val query = queryParams["q"] ?: "Nevermind"
                        val idx = queryParams["index"]?.toIntOrNull() ?: 0
                        val future = java.util.concurrent.CompletableFuture<JSONObject>()
                        scope.launch {
                            try {
                                val act = currentActivity
                                val pc = playerConnection
                                if (act != null && pc != null) {
                                    val entryPoint = EntryPointAccessors.fromApplication(
                                        act.applicationContext,
                                        SpotifyEntryPoint::class.java,
                                    )
                                    val repo = entryPoint.spotifyLibraryRepository()
                                    val results = repo.searchTracks(query = query, limit = 10)
                                    if (results.isNotEmpty()) {
                                        val target = results.getOrNull(idx) ?: results[0]
                                        val preload = SpotifyPlaybackResolver.resolveToMetadata(target)
                                        withContext(Dispatchers.Main) {
                                            pc.playQueue(
                                                SpotifyPlaylistQueue(
                                                    title = "Spotify: $query",
                                                    initialTracks = results,
                                                    startIndex = idx,
                                                    preloadItem = preload,
                                                ),
                                            )
                                        }
                                        future.complete(JSONObject().put("status", "ok").put("playing", target.name))
                                    } else {
                                        future.complete(JSONObject().put("status", "error").put("message", "No tracks found"))
                                    }
                                } else {
                                    future.complete(JSONObject().put("status", "error").put("message", "Null activity or playerConnection"))
                                }
                            } catch (e: Exception) {
                                future.complete(JSONObject().put("status", "error").put("message", e.message))
                            }
                        }
                        try {
                            future.get(10, java.util.concurrent.TimeUnit.SECONDS)
                        } catch (e: Exception) {
                            JSONObject().put("status", "error").put("message", e.message)
                        }
                    }
                    "/ping" -> JSONObject().put("status", "ok").put("message", "pong")
                    else -> JSONObject().put("status", "error").put("message", "Unknown endpoint: $path")
                }

                val body = responseJson.toString(2)
                val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)

                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: application/json; charset=utf-8\r\n")
                writer.print("Content-Length: ${bodyBytes.size}\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("Connection: close\r\n\r\n")
                writer.flush()
                s.getOutputStream().write(bodyBytes)
                s.getOutputStream().flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client request: ${e.message}", e)
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        return query.split("&").mapNotNull {
            val idx = it.indexOf('=')
            if (idx > 0) {
                val key = URLDecoder.decode(it.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(it.substring(idx + 1), "UTF-8")
                key to value
            } else null
        }.toMap()
    }

    private fun handleCommand(action: String, params: Map<String, String>) {
        when (action) {
            "nav", "navigate" -> params["route"]?.let { navigate(it) }
            "play" -> executeAction("play", params)
            "pause" -> executeAction("pause", params)
            "next" -> executeAction("next", params)
            "prev", "previous" -> executeAction("previous", params)
            "reconnect_spotify" -> executeAction("reconnect_spotify", params)
            "oauth_login" -> executeAction("oauth_login", params)
            "set_token" -> executeAction("set_token", params)
            "status" -> Log.i(TAG, "Current Status: ${getStatusJson()}")
            else -> Log.w(TAG, "Unknown debug command: $action")
        }
    }

    fun navigate(route: String) {
        mainHandler.post {
            try {
                navController?.navigate(route) {
                    launchSingleTop = true
                }
                Log.i(TAG, "Successfully navigated to $route")
            } catch (e: Exception) {
                Log.e(TAG, "Navigation failed to $route: ${e.message}", e)
            }
        }
    }

    fun executeAction(name: String, params: Map<String, String> = emptyMap()): Boolean {
        var success = false
        mainHandler.post {
            try {
                val player = playerConnection?.player
                when (name.lowercase()) {
                    "play" -> {
                        player?.play()
                        success = true
                    }
                    "pause" -> {
                        player?.pause()
                        success = true
                    }
                    "next" -> {
                        player?.seekToNextMediaItem()
                        success = true
                    }
                    "prev", "previous" -> {
                        player?.seekToPreviousMediaItem()
                        success = true
                    }
                    "play_youtube_id" -> {
                        val vid = params["id"] ?: "eRwUvMn6k3Q"
                        mainHandler.post {
                            try {
                                val pc = playerConnection
                                if (pc != null) {
                                    pc.playQueue(
                                        moe.rukamori.archivetune.playback.queues.YouTubeQueue(
                                            endpoint = moe.rukamori.archivetune.innertube.models.WatchEndpoint(videoId = vid),
                                            preloadItem = null,
                                        ),
                                    )
                                    Log.i(TAG, "Triggered YouTubeQueue playback for videoId: $vid")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed play_youtube_id: ${e.message}", e)
                            }
                        }
                        success = true
                    }
                    "reconnect_spotify" -> {
                        scope.launch {
                            try {
                                SpotifySessionManager.get()?.tryRestoreSavedSession()
                                Log.i(TAG, "Triggered SpotifySessionManager reconnect")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed reconnect: ${e.message}")
                            }
                        }
                        success = true
                    }
                    "oauth_login" -> {
                        mainHandler.post {
                            try {
                                val act = currentActivity
                                if (act != null) {
                                    SpotifySessionManager.get()?.startOAuthBrowserLogin(act)
                                    Log.i(TAG, "Triggered SpotifySessionManager startOAuthBrowserLogin")
                                } else {
                                    Log.w(TAG, "currentActivity is null for startOAuthBrowserLogin")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed oauth_login: ${e.message}", e)
                            }
                        }
                        success = true
                    }
                    "set_token" -> {
                        val token = params["token"]
                        val refresh = params["refresh"] ?: ""
                        val expires = params["expires"]?.toLongOrNull() ?: 3600L
                        if (!token.isNullOrBlank()) {
                            SpotifySessionManager.get()?.credentialsStore?.saveTokens(token, refresh, expires)
                            moe.rukamori.archivetune.spotify.Spotify.accessToken = token
                            Log.i(TAG, "Saved token manually: ${token.take(10)}...")
                            success = true
                        }
                    }
                    else -> Log.w(TAG, "Unknown action: $name")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute action $name: ${e.message}", e)
            }
        }
        return true
    }

    fun getStatusJson(): JSONObject {
        val root = JSONObject()
        try {
            // Screen & Route
            val currentDestination = navController?.currentDestination?.route
            root.put("currentRoute", currentDestination ?: "unknown")

            // Spotify Librespot status
            val spotifyJson = JSONObject()
            val sessionManager = runCatching { SpotifySessionManager.get() }.getOrNull()
            if (sessionManager != null) {
                val state = sessionManager.sessionState.value
                val isConnected = state is moe.rukamori.archivetune.spotify.session.SpotifySessionState.Connected
                spotifyJson.put("isConnected", isConnected)
                spotifyJson.put("hasActiveSession", sessionManager.hasActiveSession())
                spotifyJson.put("hasCredentials", sessionManager.credentialsStore.hasCredentials())
                if (state is moe.rukamori.archivetune.spotify.session.SpotifySessionState.Connected) {
                    spotifyJson.put("username", state.username)
                    spotifyJson.put("country", state.country)
                    spotifyJson.put("deviceId", state.deviceId)
                    spotifyJson.put("authMethod", state.authMethod)
                }
            } else {
                spotifyJson.put("available", false)
            }
            root.put("spotify", spotifyJson)

            // Player state
            // Player state (must query ExoPlayer on main thread)
            val playerJson = JSONObject()
            val pc = playerConnection
            val player = pc?.player
            if (player != null) {
                val future = java.util.concurrent.CompletableFuture<JSONObject>()
                mainHandler.post {
                    try {
                        val pj = JSONObject()
                        pj.put("isPlaying", player.isPlaying)
                        pj.put("playbackState", when (player.playbackState) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN"
                        })
                        pj.put("positionMs", player.currentPosition)
                        pj.put("durationMs", player.duration)
                        pj.put("hasError", player.playerError != null)
                        if (player.playerError != null) {
                            pj.put("error", player.playerError?.message ?: player.playerError.toString())
                        }

                        val meta = player.mediaMetadata
                        val trackJson = JSONObject()
                        trackJson.put("title", meta.title?.toString())
                        trackJson.put("artist", meta.artist?.toString())
                        trackJson.put("album", meta.albumTitle?.toString())
                        pj.put("currentTrack", trackJson)
                        future.complete(pj)
                    } catch (e: Exception) {
                        future.complete(JSONObject().put("error", e.message))
                    }
                }
                try {
                    val res = future.get(1, java.util.concurrent.TimeUnit.SECONDS)
                    root.put("player", res)
                } catch (e: Exception) {
                    playerJson.put("timeout", true)
                    root.put("player", playerJson)
                }
            } else {
                playerJson.put("connected", false)
                root.put("player", playerJson)
            }

        } catch (e: Exception) {
            root.put("error", e.message)
        }
        return root
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        currentActivity?.let {
            if (receiverRegistered) {
                runCatching { it.unregisterReceiver(debugReceiver) }
                receiverRegistered = false
            }
        }
        currentActivity = null
        navController = null
        playerConnection = null
    }
}
