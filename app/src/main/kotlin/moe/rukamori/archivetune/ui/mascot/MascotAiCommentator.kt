package moe.rukamori.archivetune.ui.mascot

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.ai.AiModelOption
import moe.rukamori.archivetune.ai.AiServiceConfig
import moe.rukamori.archivetune.ai.AiTextService
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.constants.MascotAiApiKeyKey
import moe.rukamori.archivetune.constants.MascotAiCustomEndpointKey
import moe.rukamori.archivetune.constants.MascotAiEnabledKey
import moe.rukamori.archivetune.constants.MascotAiModelKey
import moe.rukamori.archivetune.constants.MascotAiProviderKey
import moe.rukamori.archivetune.constants.MascotAiSeparateApiKey
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.dataStore

/**
 * Generates live, context-aware remarks for Claudie (Клодик) via LLM API
 * while strictly yielding bandwidth and priority to audio streaming.
 */
object MascotAiCommentator {
    private const val MIN_DOWNSTREAM_KBPS = 800
    private const val MIN_AUDIO_BUFFER_AHEAD_MS = 8_000L
    private const val MIN_CALL_COOLDOWN_MS = 18_000L
    private const val AI_TIMEOUT_MS = 4_500L
    private const val MAX_CACHE_ENTRIES = 64

    @Volatile
    private var lastCallElapsedMs: Long = 0L

    private val lineCache =
        object : LinkedHashMap<String, String>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
                size > MAX_CACHE_ENTRIES
        }

    fun defaultFastModelFor(provider: AiProvider): String =
        when (provider) {
            AiProvider.CHATGPT -> "gpt-4o-mini"
            AiProvider.GEMINI -> "gemini-2.0-flash-lite"
            AiProvider.MISTRAL -> "mistral-small-latest"
            AiProvider.OPENROUTER -> "google/gemini-2.0-flash-lite-001"
            else -> ""
        }

    suspend fun resolveMascotConfig(context: Context): AiServiceConfig? {
        val prefs = context.dataStore.data.first()
        if (prefs[MascotAiEnabledKey] != true) return null

        val useSeparate = prefs[MascotAiSeparateApiKey] == true
        val provider =
            if (useSeparate) {
                prefs[MascotAiProviderKey].toEnum(AiProvider.NONE)
            } else {
                prefs[AiProviderKey].toEnum(AiProvider.NONE)
            }
        if (provider == AiProvider.NONE || provider == AiProvider.DEEPL) return null

        val apiKey =
            if (useSeparate) {
                prefs[MascotAiApiKeyKey].orEmpty()
            } else {
                prefs[AiApiKeyKey].orEmpty()
            }
        val endpoint =
            if (useSeparate) {
                prefs[MascotAiCustomEndpointKey].orEmpty()
            } else {
                prefs[AiCustomEndpointKey].orEmpty()
            }
        val explicitMascotModel = prefs[MascotAiModelKey].orEmpty().trim()
        val fallbackMainModel =
            if (provider == AiProvider.CUSTOM) {
                prefs[AiCustomModelKey].orEmpty()
            } else {
                prefs[AiSelectedModelKey].orEmpty()
            }
        val chosenModel =
            explicitMascotModel
                .ifBlank { defaultFastModelFor(provider) }
                .ifBlank { fallbackMainModel }

        val config =
            AiServiceConfig(
                provider = provider,
                apiKey = apiKey,
                customEndpoint = endpoint,
                model = chosenModel,
            )
        return config.takeIf { it.canCallApi }
    }

    suspend fun fetchAvailableModels(context: Context): List<AiModelOption> {
        val prefs = context.dataStore.data.first()
        val useSeparate = prefs[MascotAiSeparateApiKey] == true
        val provider =
            if (useSeparate) {
                prefs[MascotAiProviderKey].toEnum(AiProvider.NONE)
            } else {
                prefs[AiProviderKey].toEnum(AiProvider.NONE)
            }
        val apiKey =
            if (useSeparate) {
                prefs[MascotAiApiKeyKey].orEmpty()
            } else {
                prefs[AiApiKeyKey].orEmpty()
            }
        val endpoint =
            if (useSeparate) {
                prefs[MascotAiCustomEndpointKey].orEmpty()
            } else {
                prefs[AiCustomEndpointKey].orEmpty()
            }
        val config = AiServiceConfig(provider = provider, apiKey = apiKey, customEndpoint = endpoint, model = "")
        if (!config.canCallApi) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { AiTextService.fetchModels(config) }.getOrDefault(emptyList())
        }
    }

    /**
     * Waits briefly for active music playback to build a healthy audio buffer,
     * and aborts immediately if the network is weak or the player is buffering.
     */
    private suspend fun awaitMusicSafeWindow(
        context: Context,
        playerConnection: PlayerConnection?,
    ): Boolean {
        if (!hasHealthyNetwork(context)) return false
        if (playerConnection == null) return true

        // Wait up to 3.5s for the audio player to finish initial buffering and build a safe cushion
        repeat(7) {
            val state = playerConnection.playbackState.value
            if (state == Player.STATE_BUFFERING) {
                delay(500L)
            } else if (state == Player.STATE_READY) {
                val bufferedAhead =
                    withContext(Dispatchers.Main) {
                        val pos = playerConnection.player.currentPosition
                        val buf = playerConnection.player.bufferedPosition
                        val pct = playerConnection.player.bufferedPercentage
                        (buf - pos >= MIN_AUDIO_BUFFER_AHEAD_MS) || pct >= 20 || !playerConnection.isPlaying.value
                    }
                if (bufferedAhead) return true
                delay(500L)
            } else {
                return true
            }
        }
        return false
    }

    private fun hasHealthyNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        val downKbps = caps.linkDownstreamBandwidthKbps
        return downKbps <= 0 || downKbps >= MIN_DOWNSTREAM_KBPS
    }

    suspend fun generateLiveLineOrNull(
        context: Context,
        playerConnection: PlayerConnection?,
        event: MascotEvent,
        currentTrackTitle: String?,
        currentTrackArtist: String?,
        bypassCooldown: Boolean = false,
    ): String? {
        val config = resolveMascotConfig(context) ?: return null

        val cacheKey = buildCacheKey(event, currentTrackTitle, currentTrackArtist)
        if (!bypassCooldown && cacheKey != null) {
            synchronized(lineCache) {
                lineCache[cacheKey]?.let { return it }
            }
        }

        val now = SystemClock.elapsedRealtime()
        if (!bypassCooldown && now - lastCallElapsedMs < MIN_CALL_COOLDOWN_MS) {
            return null
        }

        if (!awaitMusicSafeWindow(context, playerConnection)) {
            return null
        }

        lastCallElapsedMs = SystemClock.elapsedRealtime()

        val userPrompt = buildEventPrompt(event, currentTrackTitle, currentTrackArtist)
        val generated =
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(AI_TIMEOUT_MS) {
                    runCatching {
                        AiTextService.complete(
                            config = config,
                            systemPrompt =
                                """
                                Ты — Клодик, живой, остроумный и музыкально подкованный маскот плеера ArchiveTune в оранжевом свитере.
                                Отвечай ТОЛЬКО одной короткой репликой на русском языке (максимум 85 символов, 1 эмодзи, без кавычек и пояснений).
                                Пиши живо, с характером, упоминай жанр/атмосферу/исполнителя или трек, если они известны, без шаблонных клише.
                                """.trimIndent(),
                            userPrompt = userPrompt,
                            temperature = 0.85,
                            maxTokens = 64,
                        )
                    }.getOrNull()
                }
            }?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                ?.trim('"', '«', '»', '\'')
                ?.take(110)
                ?.takeIf { it.length >= 4 }

        if (generated != null && cacheKey != null) {
            synchronized(lineCache) {
                lineCache[cacheKey] = generated
            }
        }
        return generated
    }

    private fun buildCacheKey(
        event: MascotEvent,
        title: String?,
        artist: String?,
    ): String? =
        when (event) {
            is MascotEvent.TrackStarted -> "track:${event.title.lowercase()}|${event.artist.lowercase()}"
            is MascotEvent.TrackLiked -> if (!title.isNullOrBlank()) "like:${title.lowercase()}|${artist.orEmpty().lowercase()}" else null
            else -> null
        }

    private fun buildEventPrompt(
        event: MascotEvent,
        currentTrackTitle: String?,
        currentTrackArtist: String?,
    ): String {
        val trackContext =
            if (!currentTrackTitle.isNullOrBlank()) {
                "Сейчас играет: \"$currentTrackTitle\" — ${currentTrackArtist.orEmpty().ifBlank { "неизвестный артист" }}."
            } else {
                "Сейчас ничего не играет."
            }
        return when (event) {
            is MascotEvent.TrackStarted ->
                "Включился трек \"${event.title}\" исполнителя \"${event.artist.ifBlank { "неизвестен" }}\". Прокомментируй этот трек, вайб или артиста!"
            is MascotEvent.TrackLiked ->
                "$trackContext Пользователь только что поставил лайк этому треку. Отреагируй!"
            is MascotEvent.TrackSkipped ->
                "$trackContext Пользователь скипнул трек. Подшути или поддержи поиск лучшего вайба!"
            is MascotEvent.TapMascot, is MascotEvent.DoubleTapMascot ->
                "$trackContext Пользователь кликнул по тебе (Клодику). Скажи что-нибудь забавное или про текущий трек!"
            is MascotEvent.RapidTapMascot ->
                "$trackContext Пользователь быстро натыкал по тебе много раз подряд. Возмутись с юмором!"
            is MascotEvent.PetLongPress, is MascotEvent.LongPressMascot ->
                "$trackContext Пользователь погладил тебя. Ответь уютно!"
            else ->
                "$trackContext Событие: ${event.javaClass.simpleName}. Дай короткую живую реплику."
        }
    }
}
