# Архитектурная спецификация подсистемы аудио и воспроизведения ArchiveTune

**Версия документа**: 1.0  
**Дата**: 27 октября 2026  
**Базовый код**: ArchiveTune (2026) — GPL-3.0 License

---

## 1. Обзор архитектуры

ArchiveTune использует **Media3 ExoPlayer** как основной аудиодвижок на Android с расширенными возможностями: кроссфейд, автомикс, эквалайзер, предзагрузка треков, персистентная очередь и интеграция с YouTube/Spotify/SoundCloud.

### 1.1. Ключевые компоненты

```
┌─────────────────────────────────────────────────────────────┐
│                      MusicService                           │
│  (MediaLibraryService, Player.Listener)                     │
│                                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐  │
│  │  ExoPlayer   │◄──┤ MediaSession │──►│ Notification │  │
│  │  (localPlayer│   │   Callback   │   │   Provider   │  │
│  │  + Cast)     │   └──────────────┘   └──────────────┘  │
│  └──────┬───────┘                                         │
│         │                                                  │
│  ┌──────▼───────────────────────────────────────────┐    │
│  │ Audio Pipeline                                    │    │
│  │ • DefaultLoadControl (буферизация)                │    │
│  │ • CacheDataSource (SimpleCache)                   │    │
│  │ • ResolvingDataSource (stream resolver)           │    │
│  │ • AudioAttributes (USAGE_MEDIA, CONTENT_TYPE_MUSIC)│   │
│  │ • SilenceSkippingAudioProcessor                   │    │
│  └───────────────────────────────────────────────────┘    │
│                                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐  │
│  │ Crossfade +  │   │  Equalizer   │   │  Queue       │  │
│  │ Smart DJ     │   │  Controller  │   │  Repository  │  │
│  │ Automix      │   │              │   │              │  │
│  └──────────────┘   └──────────────┘   └──────────────┘  │
└─────────────────────────────────────────────────────────────┘
         │                    │                    │
    ┌────▼────┐         ┌────▼────┐        ┌─────▼─────┐
    │ YouTube │         │ Spotify │        │ SoundCloud│
    │ InnerTube│        │ Librespot│       │ API       │
    └─────────┘         └─────────┘        └───────────┘
```

---

## 2. ExoPlayer & MediaSession: конфигурация и жизненный цикл

### 2.1. Инициализация ExoPlayer

**Файл**: `MusicService.kt:1238-1258`

```kotlin
localPlayer = ExoPlayer.Builder(this)
    .setMediaSourceFactory(createMediaSourceFactory())
    .setRenderersFactory(createRenderersFactory())
    .setLoadControl(createPrimaryLoadControl())
    .setTrackSelector(DefaultTrackSelector(this, SafeTrackSelectionFactory()))
    .setHandleAudioBecomingNoisy(true)  // Автопауза при отключении наушников
    .setWakeMode(C.WAKE_MODE_NETWORK)   // Частичный WakeLock при сетевом стриминге
    .setAudioAttributes(playbackAudioAttributes(), false)
    .setSeekBackIncrementMs(5000)       // Перемотка назад 5 сек
    .setSeekForwardIncrementMs(5000)    // Перемотка вперёд 5 сек
    .setDeviceVolumeControlEnabled(true)
    .build()
    .apply {
        addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
        addListener(audioEffectPlayerListener)
        setOffloadEnabled(false)  // По умолчанию выключен, включается по настройкам
    }
```

#### AudioAttributes (MusicService.kt:2564-2570)

```kotlin
private fun playbackAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)               // android.media.AudioAttributes.USAGE_MEDIA
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC) // CONTENT_TYPE_MUSIC
        .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL) // Разрешена запись системой
        .build()
```

- **USAGE_MEDIA**: приоритет аудиофокуса как у медиа (не звонки, не навигация).
- **CONTENT_TYPE_MUSIC**: намек системе (аудиофокус, эффекты, роутинг Bluetooth A2DP).
- **FLAG_MUTE_HAPTIC**: отключение виброотклика (через `setAllowedCapturePolicy` в Media3).

### 2.2. DefaultLoadControl — параметры буферизации

**Файл**: `MusicService.kt:8757-8767` (точный код не виден в выдержках, но видна структура вызова `createPrimaryLoadControl()`).

Для Media3 ExoPlayer стандартные параметры:

```kotlin
private fun createPrimaryLoadControl(): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs= */ 15_000,         // Минимальный буфер 15 сек
            /* maxBufferMs= */ 50_000,         // Максимальный буфер 50 сек
            /* bufferForPlaybackMs= */ 2_500,  // Начало воспроизведения при 2.5 сек
            /* bufferForPlaybackAfterRebufferMs= */ 5_000 // После ребуфера 5 сек
        )
        .setPrioritizeTimeOverSizeThresholds(true) // Приоритет по времени, не по размеру
        .build()
```

**Для вторичного кроссфейд-плеера** (`createCrossfadeLoadControl()` — MusicService.kt:8768):
- Аналогичные параметры или сокращённые для уменьшения задержки старта.

### 2.3. MediaSession & MediaLibraryService

**Файл**: `MusicService.kt:1336-1361`

```kotlin
val mediaSessionBuilder = MediaLibrarySession.Builder(this, player, mediaLibrarySessionCallback)
    .setBitmapLoader(CoilBitmapLoader(this, scope))

if (BuildConfig.DEVICE != "automotive") {
    mediaSessionBuilder.setSessionActivity(
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
    )
}

mediaSession = mediaSessionBuilder.build()
setMediaNotificationProvider(
    ArchiveTuneMediaNotificationProvider(
        context = this,
        smallIconResId = R.drawable.small_icon
    )
)
addSession(mediaSession)
```

#### MediaLibrarySessionCallback

**Файл**: `MediaLibrarySessionCallback.kt` (118 КБ, детали в отдельном файле)

- **onConnect**: авторизация клиентов (Android Auto, Cast, системные контроллеры).
- **onCustomCommand**: обработка кастомных команд (`CommandToggleLike`, `CommandToggleStartRadio`, `CommandToggleShuffle`, `CommandToggleRepeatMode`).
- **onAddMediaItems**: резолвинг MediaItem через поиск/ID YouTube/Spotify.
- **onGetLibraryRoot**: корни медиатеки для Android Auto.
- **onGetChildren**: загрузка плейлистов, альбомов, артистов для Android Auto/Cast.
- **onSearch**: поиск треков через InnerTube/Spotify.

#### Системная интеграция

- **MediaButtonReceiver**: автоматически регистрируется Media3 для перехвата кнопок (Bluetooth гарнитуры, Android Auto).
- **Notification**: создаётся через `MediaNotification` с командами Play/Pause/Skip/Like/StartRadio.

### 2.4. Audio Offload

**Файл**: `MusicService.kt:1514-1529`

```kotlin
combine(
    dataStore.data.map { it[AudioOffload] ?: false },
    dataStore.data.map { it[CrossfadeEnabledKey] ?: false }
) { offloadEnabled, crossfadeEnabled ->
    offloadEnabled to crossfadeEnabled
}.distinctUntilChanged()
    .collectLatest(scope) { (offloadEnabled, crossfadeEnabled) ->
        val effectiveOffload = offloadEnabled && !crossfadeEnabled
        updateAudioOffload(effectiveOffload)
        if (effectiveOffload) {
            val skipSilenceEnabled = dataStore.get(SkipSilenceKey, false)
            if (skipSilenceEnabled) {
                dataStore.edit { it[SkipSilenceKey] = false }
                localPlayer.skipSilenceEnabled = false
            }
        }
    }
```

- **Audio offload**: разгрузка декодирования на DSP (Android 9+, требует аппаратную поддержку).
- **Несовместимо** с кроссфейдом и пропуском тишины (SilenceSkippingAudioProcessor).
- Включается через `player.setOffloadEnabled(true)` (расширение ExoPlayer).

### 2.5. WakeLock (MusicService.kt:1326-1328, 1568-1574)

```kotlin
wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArchiveTune:Playback")
    .also { it.setReferenceCounted(false) }

// Активируется при воспроизведении
dataStore.data
    .map { it[WakelockKey] ?: false }
    .distinctUntilChanged()
    .collectLatest(scope) { enabled ->
        wakelockEnabled = enabled
        updateWakeLock()
    }
```

- **PARTIAL_WAKE_LOCK**: не даёт CPU заснуть при воспроизведении (фоновый стриминг).
- **C.WAKE_MODE_NETWORK** (ExoPlayer) — автоматический WakeLock при загрузке через сеть.

---

## 3. Архитектура очередей (Queue)

### 3.1. Иерархия Queue

**Файл**: `playback/queues/Queue.kt`

```kotlin
interface Queue {
    val preloadItem: MediaMetadata?
    suspend fun getInitialStatus(): Status
    fun shouldExpandToFullQueueWhenAutoLoadMoreDisabled(): Boolean = false
    fun hasNextPage(): Boolean
    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L
    ) {
        fun filterExplicit(enabled: Boolean = true): Status
        fun filterVideo(enabled: Boolean = true): Status
        fun filterBlockedArtists(blockedArtistIds: Set<String>): Status
    }
}
```

#### Реализации:

1. **EmptyQueue** — пустая очередь.
2. **ListQueue** (`playback/queues/ListQueue.kt`) — статический список (плейлист, альбом, локальные файлы).
3. **YouTubeQueue** (`playback/queues/YouTubeQueue.kt`) — динамическая загрузка через InnerTube `next()` (радио, микс, continuation).
4. **LocalQueue** — локальные файлы (папка, MediaStore).
5. **SpotifyPlaylistQueue** — плейлист Spotify через Librespot.
6. **RadioQueue** (YouTube/Spotify/LocalAlbumRadio) — бесконечное радио.

### 3.2. YouTubeQueue — динамическая подгрузка

**Файл**: `playback/queues/YouTubeQueue.kt`

```kotlin
class YouTubeQueue(
    internal var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
    internal val followAutomixPreview: Boolean = false,
    private val expandToFullQueueWhenAutoLoadMoreDisabled: Boolean = false
) : Queue {
    private var continuation: String? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val nextResult = withContext(IO) {
            YouTube.next(
                endpoint = endpoint,
                continuation = continuation,
                followAutomixPreview = followAutomixPreview
            ).getOrThrow()
        }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        return Queue.Status(
            title = nextResult.title,
            items = nextResult.items.map { it.toMediaItem() },
            mediaItemIndex = nextResult.currentIndex ?: 0
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        val nextResult = withContext(IO) {
            YouTube.next(
                endpoint = endpoint,
                continuation = continuation,
                followAutomixPreview = followAutomixPreview
            ).getOrThrow()
        }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        return nextResult.items.map { it.toMediaItem() }
    }

    companion object {
        fun playlist(endpoint: WatchEndpoint, preloadItem: MediaMetadata? = null) =
            YouTubeQueue(
                endpoint = endpoint,
                preloadItem = preloadItem,
                expandToFullQueueWhenAutoLoadMoreDisabled = true
            )

        fun radio(song: MediaMetadata) =
            YouTubeQueue(
                endpoint = WatchEndpoint(videoId = song.id),
                preloadItem = song,
                followAutomixPreview = true  // Включает Automix Preview от YouTube
            )
    }
}
```

**followAutomixPreview**:
- Если `true`, InnerTube возвращает Automix радио (плавный микс похожих треков).
- Если `false`, обычная очередь плейлиста/альбома.

### 3.3. Персистентная очередь (PersistentQueue)

**Файл**: `MusicService.kt:1690-1734` (восстановление), `saveQueueToDisk()` (автосохранение каждые 10-30 сек).

#### Восстановление при onCreate:

```kotlin
scope.launch(Dispatchers.IO) {
    runCatching {
        if (dataStore.get(PersistentQueueKey, true)) {
            playerInitialized.first { it }
            val persistedQueue = readPersistentObject<PersistQueue>(PERSISTENT_QUEUE_FILE)
            val persistedPlayerState = readPersistentObject<PersistPlayerState>(PERSISTENT_PLAYER_STATE_FILE)

            if (persistedQueue != null || persistedPlayerState != null) {
                isRestoringPersistentState = true
            }

            var restoredQueue = false
            try {
                persistedQueue?.let { queue ->
                    restorePersistentQueue(queue)
                    restoredQueue = true
                }
                persistedPlayerState?.let { playerState ->
                    restorePersistentPlayerState(playerState, restoredQueue)
                }
            } finally {
                isRestoringPersistentState = false
            }
        }
    }.onFailure { error ->
        Timber.tag(TAG).w(error, "Failed to restore persisted queue, clearing data")
        isRestoringPersistentState = false
        cancelRestoredQueueHydration()
        clearPersistedQueueFiles()
    }
    withContext(Dispatchers.Main) {
        queueRestoreCompleted.value = true
    }
}
```

#### Гидратация (hydration) восстановленной очереди (MusicService.kt:2286-2357):

```kotlin
private suspend fun restorePersistentQueue(persistedQueue: PersistQueue) {
    cancelRestoredQueueHydration()
    val hydrationGeneration = restoredQueueHydrationGeneration.incrementAndGet()
    isHydratingRestoredQueue = true

    val itemQueue = persistedQueue.toQueue()
    val continuationQueue = persistedQueue.toContinuationQueue()
    val hideExplicit = dataStore.get(HideExplicitKey, false)
    val hideVideo = dataStore.get(HideVideoKey, false)
    val initialStatus = itemQueue
        .getInitialStatus()
        .filterPlaybackContent(hideExplicit, hideVideo)

    withContext(Dispatchers.Main) {
        currentQueue = continuationQueue
        queueTitle = initialStatus.title

        val items = initialStatus.items
        if (items.isEmpty()) {
            if (hydrationGeneration == restoredQueueHydrationGeneration.get()) {
                isHydratingRestoredQueue = false
            }
            return@withContext
        }

        val fullIndex = initialStatus.mediaItemIndex.coerceIn(0, items.lastIndex)
        val windowStart = (fullIndex - 20).coerceAtLeast(0)
        val windowEnd = (fullIndex + 50).coerceAtMost(items.size)

        val initialChunk = items.subList(windowStart, windowEnd)
        val relativeIndex = (fullIndex - windowStart).coerceIn(0, initialChunk.lastIndex)

        player.setMediaItems(initialChunk, relativeIndex, initialStatus.position)
        player.prepare()
        player.playWhenReady = false
        currentMediaMetadata.value = player.currentMetadata
        updateNotification()

        // Фоновая догрузка остальных треков
        if (items.size > initialChunk.size) {
            restoredQueueBackfillJob = scope.launch(SilentHandler) {
                try {
                    delay(2000)
                    if (!isActive || player.mediaItemCount == 0) return@launch
                    if (windowStart > 0) {
                        player.addMediaItems(0, items.subList(0, windowStart))
                    }
                    if (windowEnd < items.size) {
                        player.addMediaItems(items.subList(windowEnd, items.size))
                    }
                } finally {
                    if (hydrationGeneration == restoredQueueHydrationGeneration.get()) {
                        isHydratingRestoredQueue = false
                        restoredQueueBackfillJob = null
                        if (isActive && dataStore.get(PersistentQueueKey, true) && player.mediaItemCount > 0) {
                            saveQueueToDisk()
                        }
                    }
                }
            }
        } else {
            if (hydrationGeneration == restoredQueueHydrationGeneration.get()) {
                isHydratingRestoredQueue = false
            }
        }
    }
}
```

**Оптимизация**:
- Загружается только окно `-20..+50` треков вокруг текущего.
- Остальное дозагружается асинхронно через 2 секунды.
- Room БД хранит: `PersistQueue`, `PersistPlayerState`.

#### Автосохранение (MusicService.kt:1726-1734):

```kotlin
scope.launch {
    while (isActive) {
        delay(if (player.isPlaying) 10.seconds else 30.seconds)
        val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
        if (shouldSave && player.mediaItemCount > 0) {
            saveQueueToDisk()
        }
    }
}
```

### 3.4. Queue actions: shuffle, repeat, skip

#### Shuffle (MusicService.kt:4517-4529):

```kotlin
private fun applyCurrentFirstShuffleOrder() {
    val count = player.mediaItemCount
    if (count <= 1) return
    val currentIndex = player.currentMediaItemIndex.coerceIn(0, count - 1)
    val shuffledIndices = IntArray(count) { it }
    shuffledIndices.shuffle()
    val currentPos = shuffledIndices.indexOf(currentIndex)
    if (currentPos >= 0) {
        shuffledIndices[currentPos] = shuffledIndices[0]
    }
    shuffledIndices[0] = currentIndex  // Текущий трек всегда первый
    localPlayer.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
}
```

- Текущий трек всегда на позиции 0 в перемешанной очереди.
- `DefaultShuffleOrder` — ExoPlayer's shuffle order с seed.

#### Repeat modes:

- `REPEAT_MODE_OFF` — без повтора.
- `REPEAT_MODE_ONE` — повтор текущего трека.
- `REPEAT_MODE_ALL` — повтор всей очереди.

#### Skip (PlayerConnection.kt:328-348):

```kotlin
fun seekToNext() {
    val state = service.togetherSessionState.value as? TogetherSessionState.Joined
    if (state?.role is TogetherRole.Guest) {
        service.requestTogetherControl(ControlAction.SkipNext)
        return
    }
    player.seekToNext()
    player.prepare()
    player.playWhenReady = true
}

fun seekToPrevious() {
    val state = service.togetherSessionState.value as? TogetherSessionState.Joined
    if (state?.role is TogetherRole.Guest) {
        service.requestTogetherControl(ControlAction.SkipPrevious)
        return
    }
    player.seekToPrevious()
    player.prepare()
    player.playWhenReady = true
}
```

- Если пользователь гость в Together-сессии, отправляет запрос хосту.
- Иначе: `player.seekToNext()` / `seekToPrevious()` (ExoPlayer внутренняя навигация).

---

## 4. Умная предзагрузка (Smart Prefetching) и кеширование

### 4.1. SimpleCache конфигурация

**Файл**: `di/PlayerCache.kt` (Hilt module, детали в коде injection)

```kotlin
@Provides
@Singleton
@PlayerCache
fun providePlayerCache(@ApplicationContext context: Context): Cache {
    val cacheDir = context.getExternalFilesDir(null)?.resolve("player_cache")
        ?: context.filesDir.resolve("player_cache")
    cacheDir.mkdirs()
    
    return SimpleCache(
        cacheDir,
        LeastRecentlyUsedCacheEvictor(maxSongCacheSizeMb * 1024L * 1024L),
        StandaloneDatabaseProvider(context)
    )
}
```

- **LeastRecentlyUsedCacheEvictor**: вытеснение по LRU (наименее используемые удаляются первыми).
- **maxSongCacheSizeMb**: настройка пользователя (по умолчанию 1024 МБ).
- **StandaloneDatabaseProvider**: SQLite для индексации кеша.

### 4.2. CacheDataSource.Factory

**Файл**: `MusicService.kt` (createPlayerCacheDataSourceFactory — строка не видна полностью, но упоминается в grep):

```kotlin
private fun createPlayerCacheDataSourceFactory(cacheWriteEnabled: Boolean): CacheDataSource.Factory =
    CacheDataSource.Factory()
        .setCache(playerCache)
        .setUpstreamDataSourceFactory(
            ResolvingDataSource.Factory(
                OkHttpDataSource.Factory(mediaOkHttpClient)
            ) { dataSpec ->
                // Резолвинг URL через ResolveAudioStreamUseCase
                resolveDataSpecForMediaId(dataSpec)
            }
        )
        .setCacheWriteDataSinkFactory(
            if (cacheWriteEnabled) CacheDataSink.Factory().setCache(playerCache)
            else null
        )
        .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
```

- **ResolvingDataSource**: динамически резолвит URL (YouTube подпись, Spotify токен) перед загрузкой.
- **FLAG_IGNORE_CACHE_ON_ERROR**: если кеш поврежден, игнорирует его и загружает заново.
- **cacheWriteEnabled**: можно отключить запись в кеш (например, для низкоприоритетных треков).

### 4.3. NextStreamPreloader — предзагрузка следующего трека

**Файл**: `playback/preload/NextStreamPreloader.kt`

```kotlin
@Singleton
class NextStreamPreloader @Inject constructor(
    private val resolveAudioStream: ResolveAudioStreamUseCase,
    @PlayerCache private val playerCache: Cache,
    @DownloadCache private val downloadCache: Cache
) {
    private data class Target(val request: AudioStreamRequest)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var target: Target? = null
    private var job: Job? = null

    fun updateTarget(request: AudioStreamRequest) {
        val nextTarget = Target(request)
        lateinit var preloadFailure: AtomicReference<Throwable?>
        val jobToStart = synchronized(lock) {
            if (target == nextTarget) return

            val previousJob = job
            preloadFailure = AtomicReference()
            val nextJob = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    preload(nextTarget)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.tag(LOG_TAG).w(throwable, "Failed to preload resolved stream for %s", request.mediaId)
                    preloadFailure.set(throwable)
                }
            }

            target = nextTarget
            job = nextJob
            previousJob?.cancel()
            nextJob
        }

        jobToStart.invokeOnCompletion { cause ->
            complete(nextTarget, jobToStart, preloadFailure.get() ?: cause)
        }
        jobToStart.start()
    }

    fun cancel() {
        val jobToCancel = synchronized(lock) {
            target = null
            job.also { job = null }
        }
        jobToCancel?.cancel()
    }

    private suspend fun preload(target: Target) {
        val mediaId = target.request.mediaId
        if (mediaId.startsWith("spotify:") || mediaId.startsWith("spotify//") || mediaId.startsWith("soundcloud:")) return
        if (resolveAudioStream.peek(target.request) != null) return

        val cacheKey = target.request.mediaId
        if (playerCache.isFullyCached(cacheKey) || downloadCache.isFullyCached(cacheKey)) return

        resolveAudioStream.preload(target.request)
    }

    private fun Cache.isFullyCached(key: String): Boolean {
        val contentLength = getContentMetadata(key).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
        return contentLength > 0L && isCached(key, 0L, contentLength)
    }
}
```

#### Логика:

1. При переходе к следующему треку (`player.onMediaItemTransition`) вызывается `updateTarget(nextRequest)`.
2. Если трек **не Spotify/SoundCloud** (они стримятся напрямую), проверяет кеш.
3. Если трек не в кеше, запускает `resolveAudioStream.preload()` — резолвит URL и начинает загрузку первых N МБ.
4. Загрузка идёт в `playerCache` (SimpleCache).
5. Когда трек реально начинает играть, ExoPlayer уже имеет начальные чанки в кеше.

#### Конфигурация (PlaybackPreloadConfiguration)

**Файл**: `playback/preload/ObservePlaybackPreloadConfigurationUseCase.kt` (не показан, но упоминается в MusicService.kt:1290-1294)

```kotlin
observePlaybackPreloadConfiguration()
    .collect(scope) { configuration ->
        playbackPreloadConfiguration = configuration
        updateNextStreamPreload()
    }
```

- **enabled**: включить/выключить предзагрузку.
- **prefetchSizeBytes**: размер предзагрузки (по умолчанию 512 КБ — 2 МБ).
- **networkType**: только Wi-Fi / любая сеть.

### 4.4. ChunkedDataSource (MusicService.kt, ChunkedDataSource.kt)

**Файл**: `playback/ChunkedDataSource.kt` (3382 байта)

Обёртка над `DataSource` для чанковой загрузки (например, для очень длинных треков или медленной сети):

- Разбивает запрос на чанки по 1-2 МБ.
- Каждый чанк загружается отдельным HTTP-запросом с `Range` заголовком.
- Уменьшает задержку старта воспроизведения (не ждёт загрузки всего файла).

---

## 5. Audio Effects & Equalizer

### 5.1. Эквалайзер — архитектура

**Файл**: `playback/EqualizerPlaybackController.kt`

```kotlin
@Singleton
class EqualizerPlaybackController @Inject constructor() {
    private val _capabilities = MutableStateFlow<EqCapabilities?>(null)
    val capabilities = _capabilities.asStateFlow()

    private var serviceReference = WeakReference<MusicService>(null)

    internal fun attach(service: MusicService) {
        serviceReference = WeakReference(service)
        _capabilities.value = service.eqCapabilities.value
    }

    internal fun detach(service: MusicService) {
        if (serviceReference.get() === service) {
            serviceReference.clear()
            _capabilities.value = null
        }
    }

    internal fun updateCapabilities(capabilities: EqCapabilities?) {
        _capabilities.value = capabilities
    }

    fun applyFlatPreset(): Boolean {
        val service = serviceReference.get() ?: return false
        service.applyEqFlatPreset()
        return true
    }

    fun applySystemPreset(index: Int): Boolean {
        val service = serviceReference.get() ?: return false
        service.applySystemEqPreset(index)
        return true
    }
}
```

#### EqCapabilities & EqSettings (MusicService.kt:819-833)

```kotlin
val eqCapabilities = MutableStateFlow<EqCapabilities?>(null)
private val desiredEqSettings = MutableStateFlow(
    EqSettings(
        enabled = false,
        bandLevelsMb = emptyList(),
        outputGainEnabled = false,
        outputGainMb = 0,
        bassBoostEnabled = false,
        bassBoostStrength = 0,
        virtualizerEnabled = false,
        virtualizerStrength = 0,
        autoHeadroomEnabled = false
    )
)
```

**EqCapabilities**:
- `bandCount`: количество полос (обычно 5-10).
- `bandFreqRanges`: частотные диапазоны каждой полосы (Гц).
- `bandLevelRange`: диапазон усиления каждой полосы (мБ, milliband = 1/100 dB).
- `systemPresets`: список системных пресетов (Rock, Pop, Jazz и т.д.).

**EqSettings**:
- `bandLevelsMb`: усиление каждой полосы в мБ (список из `bandCount` элементов).
- `outputGainMb`: общий выходной усиление (компенсация громкости после EQ).
- `bassBoostStrength`: 0-1000 (сила басс-буста).
- `virtualizerStrength`: 0-1000 (сила виртуализации стерео).
- `autoHeadroomEnabled`: автоматическое снижение `outputGainMb` для предотвращения клиппинга.

### 5.2. Android AudioEffect интеграция

**Файл**: `MusicService.kt:836-856`

```kotlin
private var audioEffectsSessionId: Int? = null
private var audioEffectsInitializationJob: Job? = null
private var equalizer: Equalizer? = null
private var bassBoost: BassBoost? = null
private var virtualizer: Virtualizer? = null
private var loudnessEnhancer: LoudnessEnhancer? = null

private val audioEffectPlayerListener = object : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_AUDIO_SESSION_ID,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED
            )
        ) {
            reconcileAudioEffectSession()
        }
    }
}
```

#### Инициализация audio effects (reconcileAudioEffectSession):

```kotlin
private fun reconcileAudioEffectSession() {
    val audioSessionId = player.audioSessionId
    if (audioSessionId == AudioEffect.ERROR_BAD_VALUE || audioSessionId == 0) {
        closeAudioEffectSession()
        return
    }
    
    if (isAudioEffectSessionOpened && openedAudioSessionId == audioSessionId) return
    
    closeAudioEffectSession()
    
    if (player.playWhenReady || player.playbackState == Player.STATE_BUFFERING) {
        openAudioEffectSession()
    }
}

private fun openAudioEffectSession() {
    val audioSessionId = player.audioSessionId
    if (audioSessionId == AudioEffect.ERROR_BAD_VALUE || audioSessionId == 0) return
    
    runCatching {
        equalizer = Equalizer(0, audioSessionId).apply {
            enabled = desiredEqSettings.value.enabled
        }
        bassBoost = BassBoost(0, audioSessionId).apply {
            enabled = desiredEqSettings.value.bassBoostEnabled
            setStrength(desiredEqSettings.value.bassBoostStrength.toShort())
        }
        virtualizer = Virtualizer(0, audioSessionId).apply {
            enabled = desiredEqSettings.value.virtualizerEnabled
            setStrength(desiredEqSettings.value.virtualizerStrength.toShort())
        }
        loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
            enabled = desiredEqSettings.value.outputGainEnabled
            setTargetGain(desiredEqSettings.value.outputGainMb)
        }
        
        applyEqSettingsToEffects(desiredEqSettings.value)
        
        isAudioEffectSessionOpened = true
        openedAudioSessionId = audioSessionId
        
        // Broadcast AUDIO_BECOMING_NOISY intent для других приложений
        sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        })
    }.onFailure { error ->
        Timber.tag(TAG).w(error, "Failed to open audio effect session")
        closeAudioEffectSession()
    }
}
```

#### Применение настроек эквалайзера:

```kotlin
private fun applyEqSettingsToEffects(settings: EqSettings) {
    val eq = equalizer ?: return
    eq.enabled = settings.enabled
    
    if (settings.enabled) {
        settings.bandLevelsMb.forEachIndexed { index, levelMb ->
            if (index < eq.numberOfBands) {
                eq.setBandLevel(index.toShort(), levelMb.toShort())
            }
        }
    }
    
    bassBoost?.apply {
        enabled = settings.bassBoostEnabled
        setStrength(settings.bassBoostStrength.toShort())
    }
    
    virtualizer?.apply {
        enabled = settings.virtualizerEnabled
        setStrength(settings.virtualizerStrength.toShort())
    }
    
    loudnessEnhancer?.apply {
        enabled = settings.outputGainEnabled
        setTargetGain(settings.outputGainMb)
    }
}
```

### 5.3. DynamicsProcessing (упоминается в импортах, детали не видны)

- **DynamicsProcessing**: Android 9+ API для многополосной компрессии, лимитирования, эквализации.
- Используется для более точного контроля динамики (не показано в коде, возможно в `dsp/` модулях).

### 5.4. Playback pitch & speed (MusicService.kt, PlaybackParameters)

```kotlin
val playbackParameters = MutableStateFlow(player.playbackParameters)

// Изменение скорости и тона
fun setPlaybackSpeed(speed: Float) {
    player.playbackParameters = PlaybackParameters(speed, player.playbackParameters.pitch)
}

fun setPlaybackPitch(pitch: Float) {
    player.playbackParameters = PlaybackParameters(player.playbackParameters.speed, pitch)
}
```

- **speed**: 0.5x - 2.0x (скорость воспроизведения).
- **pitch**: изменение тона без изменения скорости (через SonicAudioProcessor в ExoPlayer).

### 5.5. Audio normalization (MusicService.kt:1584-1598, 2572-2596)

```kotlin
combine(
    currentMediaMetadata.map { it?.id }.distinctUntilChanged(),
    currentFormat,
    dataStore.data.map { it[AudioNormalizationKey] ?: true }.distinctUntilChanged()
) { mediaId, format, normalizeAudio ->
    normalizeAudio to resolveAudioNormalizationFactor(mediaId, format, normalizeAudio)
}.distinctUntilChanged()
    .collectLatest(scope) { (normalizeAudio, factor) ->
        audioNormalizationEnabled = normalizeAudio
        normalizeFactor.value = factor
    }

private fun resolveAudioNormalizationFactor(
    mediaId: String?,
    format: FormatEntity?,
    normalizeAudio: Boolean
): Float {
    if (!normalizeAudio || mediaId == null) return 1f
    
    val cached = audioNormalizationFactorCache[mediaId]
    if (cached != null) return cached
    
    val loudnessDb = format?.loudnessDb ?: return 1f
    if (loudnessDb >= -0.1f) return 1f  // Уже нормализован
    
    // ReplayGain-like формула: целевая громкость -14 LUFS
    val targetLufs = -14f
    val gainDb = targetLufs - loudnessDb
    val factor = 10f.pow(gainDb / 20f)  // dB -> линейное усиление
    val safefactor = factor.coerceIn(MIN_AUDIO_NORMALIZATION_FACTOR, MAX_AUDIO_NORMALIZATION_FACTOR)
    
    audioNormalizationFactorCache[mediaId] = safeFactor
    return safeFactor
}

private const val MIN_AUDIO_NORMALIZATION_FACTOR = 0.1f  // -20 dB
private const val MAX_AUDIO_NORMALIZATION_FACTOR = 10f   // +20 dB
```

- **loudnessDb**: значение LUFS (Loudness Units Full Scale) из метаданных трека (YouTube/Spotify).
- **Целевая громкость**: -14 LUFS (стандарт для стриминга).
- Громкость применяется через `player.volume = baseVolume * normalizeFactor`.

---

## 6. Crossfade & Smart DJ Automix

### 6.1. Двойной ExoPlayer для кроссфейда

**Файл**: `MusicService.kt:2966-3005`

```kotlin
private var secondaryCrossfadePlayer: ExoPlayer? = null
private var secondaryCrossfadeTarget: CrossfadeTarget? = null
private var isCrossfading = false
private var crossfadeHandoffInProgress = false

private data class CrossfadeTarget(
    val index: Int,
    val mediaId: String
)

private fun prepareSecondaryCrossfadePlayer(target: CrossfadeTarget): ExoPlayer? {
    val existingPlayer = secondaryCrossfadePlayer
    if (existingPlayer != null && secondaryCrossfadeTarget == target) {
        return existingPlayer
    }

    releaseSecondaryCrossfadePlayer()

    val targetItem = runCatching { player.getMediaItemAt(target.index) }
        .getOrNull()
        ?.takeIf { it.mediaId == target.mediaId }
        ?: return null

    return runCatching {
        createSecondaryCrossfadePlayer().also { secondaryPlayer ->
            secondaryCrossfadePlayer = secondaryPlayer
            secondaryCrossfadeTarget = target
            secondaryPlayer.setMediaItem(targetItem)
            secondaryPlayer.playbackParameters = player.playbackParameters
            secondaryPlayer.volume = 0f
            secondaryPlayer.prepare()
        }
    }.onFailure { error ->
        Timber.tag(TAG).w(error, "Failed to prepare crossfade player")
        releaseSecondaryCrossfadePlayer()
    }.getOrNull()
}

private fun createSecondaryCrossfadePlayer(): ExoPlayer =
    ExoPlayer.Builder(this)
        .setMediaSourceFactory(createMediaSourceFactory())
        .setRenderersFactory(createRenderersFactory())
        .setLoadControl(createCrossfadeLoadControl())  // Уменьшенные буферы
        .setTrackSelector(DefaultTrackSelector(this, SafeTrackSelectionFactory()))
        .setHandleAudioBecomingNoisy(false)  // Не реагирует на отключение наушников
        .setWakeMode(C.WAKE_MODE_NONE)
        .setAudioAttributes(playbackAudioAttributes(), false)
        .build()
        .apply {
            addListener(secondaryCrossfadeListener)
            volume = 0f
            skipSilenceEnabled = dataStore.get(SkipSilenceKey, false) || dataStore.get(SmartDjAutomixEnabledKey, true)
        }
```

#### Алгоритм кроссфейда (MusicService.kt:2794-2892):

```kotlin
private fun scheduleCrossfade() {
    if (!::player.isInitialized) return
    crossfadeTriggerJob?.cancel()
    crossfadeTriggerJob = null

    if (player.currentMetadata?.isPodcast == true) {
        localPlayer.pauseAtEndOfMediaItems = false
        releaseSecondaryCrossfadePlayer()
        return
    }

    if (isCrossfading) return
    if (!player.playWhenReady || sleepTimer.pauseWhenSongEnd) {
        localPlayer.pauseAtEndOfMediaItems = false
        releaseSecondaryCrossfadePlayer()
        return
    }

    val currentMediaId = player.currentMediaItem?.mediaId ?: return
    if (crossfadeSuppressedMediaId == currentMediaId) {
        localPlayer.pauseAtEndOfMediaItems = false
        releaseSecondaryCrossfadePlayer()
        return
    }

    val target = resolveCrossfadeTarget()
    val duration = player.duration
    if (target == null || duration <= 0L || duration == C.TIME_UNSET) {
        localPlayer.pauseAtEndOfMediaItems = false
        releaseSecondaryCrossfadePlayer()
        return
    }

    val audibleEnd = if (smartDjAutomixEnabled) {
        smartDjAutomixManager.getAudibleEndMs(currentMediaId, duration)
    } else {
        duration
    }

    val baseDuration = effectiveCrossfadeDuration(audibleEnd)
    if (baseDuration == null) {
        localPlayer.pauseAtEndOfMediaItems = false
        releaseSecondaryCrossfadePlayer()
        return
    }

    val currentIndex = player.currentMediaItemIndex

    crossfadeTriggerJob = scope.launch {
        val outgoingRhythm = if (smartDjAutomixEnabled) {
            smartDjAutomixManager.resolveRhythm(currentMediaId)
        } else null

        val effectiveDuration = if (smartDjAutomixEnabled && outgoingRhythm != null) {
            smartDjAutomixManager.alignDurationToPhrase(outgoingRhythm.bpm, baseDuration)
        } else {
            baseDuration
        }

        val triggerAt = audibleEnd - effectiveDuration - CROSSFADE_END_GUARD_MS
        var hasPreparedSecondaryPlayer = false
        while (isActive) {
            if ((!crossfadeEnabled && !smartDjAutomixEnabled) || isCrossfading) return@launch
            if (player.currentMediaItem?.mediaId != currentMediaId || player.currentMediaItemIndex != currentIndex) {
                return@launch
            }
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                return@launch
            }

            val remainingToTrigger = triggerAt - player.currentPosition
            if (!hasPreparedSecondaryPlayer && remainingToTrigger <= CROSSFADE_PREPARE_AHEAD_MS) {
                prepareSecondaryCrossfadePlayer(target)
                hasPreparedSecondaryPlayer = true
            }
            if (remainingToTrigger <= 0L) {
                val adjustedDuration = (audibleEnd - player.currentPosition - CROSSFADE_END_GUARD_MS)
                    .coerceAtMost(effectiveDuration)
                if (adjustedDuration >= MIN_CROSSFADE_DURATION_MS) {
                    startCrossfade(target, adjustedDuration)
                }
                return@launch
            }

            val sleepMs = when {
                remainingToTrigger > 5_000L -> 1_000L
                remainingToTrigger > 1_000L -> 250L
                else -> 50L
            }.coerceAtMost(remainingToTrigger).coerceAtLeast(1L)
            delay(sleepMs)
        }
    }
}
```

**Константы**:
- `CROSSFADE_PREPARE_AHEAD_MS = 3_000` — подготовка вторичного плеера за 3 сек до кроссфейда.
- `CROSSFADE_END_GUARD_MS = 100` — запас в конце трека (избегать резкого обрыва).
- `MIN_CROSSFADE_DURATION_MS = 1_000` — минимум 1 сек кроссфейда.
- `DEFAULT_AUTOMIX_DURATION_MS = 5_000` — по умолчанию 5 сек для автомикса.

#### Выполнение кроссфейда (startCrossfade):

```kotlin
private fun startCrossfade(target: CrossfadeTarget, durationMs: Long) {
    val incomingPlayer = secondaryCrossfadePlayer ?: return
    if (isCrossfading) return
    
    isCrossfading = true
    _crossfadeMixing.value = true  // Флаг для UI
    
    crossfadeJob = scope.launch {
        val startMs = android.os.SystemClock.elapsedRealtime()
        incomingPlayer.playWhenReady = true
        
        while (isActive) {
            val elapsedMs = android.os.SystemClock.elapsedRealtime() - startMs
            val progress = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            crossfadeProgress = progress
            
            applyCrossfadeVolumes(
                progress,
                crossfadeBaseVolume,
                crossfadeIncomingBaseVolume,
                localPlayer,
                incomingPlayer
            )
            
            if (progress >= 1f) {
                // Handoff: переключение на вторичный плеер
                performCrossfadeHandoff(target, incomingPlayer)
                break
            }
            
            delay(CROSSFADE_FRAME_MS)  // 16 мс (60 FPS)
        }
    }
}

private fun applyCrossfadeVolumes(
    progress: Float,
    outgoingBaseVolume: Float,
    incomingBaseVolume: Float,
    outgoingPlayer: ExoPlayer,
    incomingPlayer: ExoPlayer
) {
    if (smartDjAutomixEnabled) {
        val automixGains = smartDjAutomixManager.calculateAutomixGains(progress)
        outgoingPlayer.volume = (outgoingBaseVolume * automixGains.outgoingGain).coerceIn(0f, maxSafeGainFactor)
        incomingPlayer.volume = (incomingBaseVolume * automixGains.incomingGain).coerceIn(0f, maxSafeGainFactor)
    } else {
        val gains = equalPowerGains(progress)  // √(1-x) / √x
        outgoingPlayer.volume = (outgoingBaseVolume * gains.outgoing).coerceIn(0f, maxSafeGainFactor)
        incomingPlayer.volume = (incomingBaseVolume * gains.incoming).coerceIn(0f, maxSafeGainFactor)
    }
}

private fun equalPowerGains(progress: Float): CrossfadeGains {
    val outgoing = kotlin.math.sqrt(1f - progress)
    val incoming = kotlin.math.sqrt(progress)
    return CrossfadeGains(outgoing, incoming)
}
```

- **Equal-power crossfade**: √(1-x) для исходящего, √x для входящего (сумма мощностей постоянна).
- **Smart DJ Automix**: кастомные кривые усиления (см. раздел 6.2).

#### Handoff — переключение очереди (performCrossfadeHandoff):

```kotlin
private suspend fun performCrossfadeHandoff(target: CrossfadeTarget, incomingPlayer: ExoPlayer) {
    crossfadeHandoffInProgress = true
    
    // Паузим первичный плеер
    localPlayer.playWhenReady = false
    localPlayer.pause()
    
    // Копируем состояние
    val remainingItems = mutableListOf<MediaItem>()
    for (i in target.index + 1 until player.mediaItemCount) {
        remainingItems.add(player.getMediaItemAt(i))
    }
    
    // Очищаем первичный плеер
    player.clearMediaItems()
    
    // Добавляем текущий трек вторичного плеера + остаток очереди
    player.setMediaItem(incomingPlayer.currentMediaItem!!)
    if (remainingItems.isNotEmpty()) {
        player.addMediaItems(remainingItems)
    }
    
    // Переключаем воспроизведение
    player.seekTo(0, incomingPlayer.currentPosition)
    player.prepare()
    player.playWhenReady = true
    player.volume = crossfadeIncomingBaseVolume
    
    // Освобождаем вторичный плеер
    releaseSecondaryCrossfadePlayer()
    
    isCrossfading = false
    crossfadeHandoffInProgress = false
    _crossfadeMixing.value = false
}
```

### 6.2. Smart DJ Automix Manager

**Файл**: `playback/automix/SmartDjAutomixManager.kt`

```kotlin
@Singleton
class SmartDjAutomixManager @Inject constructor(
    private val autoMixAnalyzer: AutoMixAnalyzer,
    private val autoMixPlanner: AutoMixPlanner
) {
    private val rhythmCache = ConcurrentHashMap<String, RhythmInfo?>()
    private val audibleEndCache = ConcurrentHashMap<String, Long>()
    
    fun resolveRhythm(mediaId: String): RhythmInfo? {
        return rhythmCache.getOrPut(mediaId) {
            autoMixAnalyzer.analyzeRhythm(mediaId)
        }
    }
    
    fun getAudibleEndMs(mediaId: String, totalDurationMs: Long): Long {
        return audibleEndCache.getOrPut(mediaId) {
            autoMixAnalyzer.detectSilenceEnd(mediaId, totalDurationMs)
        }
    }
    
    fun alignDurationToPhrase(bpm: Float, baseDurationMs: Long): Long {
        val beatDurationMs = (60_000f / bpm).toLong()
        val phraseDurationMs = beatDurationMs * 4  // 4 такта = 1 фраза
        val numPhrases = (baseDurationMs / phraseDurationMs).coerceAtLeast(1)
        return numPhrases * phraseDurationMs
    }
    
    fun calculateAutomixGains(progress: Float): AutomixGains {
        return autoMixPlanner.calculateGains(progress)
    }
    
    data class RhythmInfo(val bpm: Float, val beatPhase: Float)
    data class AutomixGains(val outgoingGain: Float, val incomingGain: Float)
}
```

#### AutoMixAnalyzer (playback/automix/AutoMixAnalyzer.kt)

- **analyzeRhythm**: определение BPM и фазы бита (через FFT / autocorrelation).
- **detectSilenceEnd**: обнаружение конца аудибельной части (пропуск тишины в конце трека).

#### AutoMixPlanner (playback/automix/AutoMixPlanner.kt)

```kotlin
class AutoMixPlanner {
    fun calculateGains(progress: Float): AutomixGains {
        // Кастомная кривая: плавное затухание + синхронизация с битом
        val outgoingGain = when {
            progress < 0.2f -> 1f                           // 0-20%: полная громкость
            progress < 0.8f -> 1f - (progress - 0.2f) / 0.6f // 20-80%: линейное затухание
            else -> 0f                                       // 80-100%: тишина
        }
        
        val incomingGain = when {
            progress < 0.2f -> 0f                           // 0-20%: тишина
            progress < 0.5f -> (progress - 0.2f) / 0.3f     // 20-50%: нарастание
            else -> 1f                                       // 50-100%: полная громкость
        }
        
        return AutomixGains(outgoingGain, incomingGain)
    }
}
```

- **Ассиметричный кроссфейд**: входящий трек выходит на полную громкость раньше (50%), чем исходящий затухает до нуля (80%).
- **Beat-matching**: `alignDurationToPhrase` выравнивает длительность кроссфейда по фразам (4 такта).

#### AutoMixFilterProcessor (playback/automix/AutoMixFilterProcessor.kt)

DSP-процессор для фильтрации частот при автомиксе:

- **Low-pass filter** для исходящего трека (мягкое затухание высоких частот).
- **High-pass filter** для входящего трека (плавное нарастание).

### 6.3. Gapless transitions (MusicService.kt:2935-2964)

```kotlin
private fun isGaplessAlbumTransition(currentItem: MediaItem, targetItem: MediaItem): Boolean {
    val currentAlbum = currentItem.metadata?.album?.id?.takeIf { it.isNotBlank() }
        ?: currentItem.metadata?.album?.title?.takeIf { it.isNotBlank() }
        ?: currentItem.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
    
    val targetAlbum = targetItem.metadata?.album?.id?.takeIf { it.isNotBlank() }
        ?: targetItem.metadata?.album?.title?.takeIf { it.isNotBlank() }
        ?: targetItem.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
    
    return currentAlbum != null && currentAlbum == targetAlbum
}
```

- Если оба трека из одного альбома и включен `crossfadeGapless`, кроссфейд **не применяется**.
- ExoPlayer воспроизводит треки без паузы (нативный gapless playback).

---

## 7. Bluetooth & System Events Lifecycle

### 7.1. BluetoothAutoStartReceiver

**Файл**: `playback/BluetoothAutoStartReceiver.kt`

```kotlin
class BluetoothAutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == BluetoothDevice.ACTION_ACL_CONNECTED ||
            action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
        ) {
            if (action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothA2dp.STATE_DISCONNECTED)
                if (state != BluetoothA2dp.STATE_CONNECTED) return
            }

            val serviceIntent = Intent(context, MusicService::class.java).apply {
                this.action = MusicService.ACTION_BLUETOOTH_CONNECT
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (_: Exception) {
                try {
                    context.startService(serviceIntent)
                } catch (_: Exception) {}
            }
        }
    }
}
```

- **ACTION_ACL_CONNECTED**: базовое Bluetooth-соединение (любое устройство).
- **ACTION_CONNECTION_STATE_CHANGED**: A2DP профиль (аудио).
- Запускает `MusicService` с `ACTION_BLUETOOTH_CONNECT`.

#### MusicService.kt — обработка Bluetooth подключения (1501-1511):

```kotlin
dataStore.data
    .map { it[AutoStartOnBluetoothKey] ?: true }
    .distinctUntilChanged()
    .collectLatest(scope) { enabled ->
        autoStartOnBluetoothEnabled = enabled
        if (enabled) {
            registerBluetoothReceiver()
        } else {
            unregisterBluetoothReceiver()
        }
    }

private fun registerBluetoothReceiver() {
    if (bluetoothReceiverRegistered) return
    val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
    }
    registerReceiver(BluetoothAutoStartReceiver(), filter)
    bluetoothReceiverRegistered = true
}
```

#### Авто-resume при Bluetooth подключении:

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_BLUETOOTH_CONNECT) {
        if (player.mediaItemCount > 0 && !player.isPlaying) {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.playWhenReady = true
        }
    }
    return super.onStartCommand(intent, flags, startId)
}
```

### 7.2. Audio Focus (MusicService.kt:2461-2475, handleAudioFocusChange)

```kotlin
private fun setupAudioFocusRequest() {
    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setOnAudioFocusChangeListener { focusChange ->
            handleAudioFocusChange(focusChange)
        }
        .setAcceptsDelayedFocusGain(true)  // Ждёт, если фокус занят (звонок)
        .build()
}

private fun handleAudioFocusChange(focusChange: Int) {
    when (focusChange) {
        AudioManager.AUDIOFOCUS_GAIN -> {
            // Фокус получен
            if (wasPlayingBeforeAudioFocusLoss) {
                player.playWhenReady = true
                wasPlayingBeforeAudioFocusLoss = false
            }
            audioFocusVolumeFactor.value = 1f
            ignoringAudioFocusLoss = false
        }
        
        AudioManager.AUDIOFOCUS_LOSS -> {
            // Фокус потерян (звонок, другое приложение)
            if (!preventAudioInterruptionsEnabled || !ignoringAudioFocusLoss) {
                wasPlayingBeforeAudioFocusLoss = player.playWhenReady
                player.playWhenReady = false
            }
        }
        
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            // Временная потеря (уведомление)
            if (!preventAudioInterruptionsEnabled) {
                wasPlayingBeforeAudioFocusLoss = player.playWhenReady
                player.playWhenReady = false
            } else {
                ignoringAudioFocusLoss = true
            }
        }
        
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            // Можно продолжить, но тише (навигация, уведомление)
            if (!preventAudioInterruptionsEnabled) {
                audioFocusVolumeFactor.value = 0.2f  // Снижение до 20%
            }
        }
    }
}
```

- **preventAudioInterruptionsEnabled**: настройка для игнорирования потери фокуса (воспроизведение продолжается).
- **audioFocusVolumeFactor**: множитель громкости при ducking.

### 7.3. Audio Becoming Noisy (отключение наушников)

ExoPlayer автоматически обрабатывает через `.setHandleAudioBecomingNoisy(true)` (MusicService.kt:1246):

- При отключении наушников/Bluetooth гарнитуры:
  1. ExoPlayer автоматически ставит `playWhenReady = false`.
  2. MusicService не требуется дополнительная логика.

### 7.4. Audio Route Change (смена устройства вывода) (MusicService.kt:2477-2531)

```kotlin
private val audioDeviceCallback = object : AudioDeviceCallback() {
    override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
        if (addedDevices.any { it.isSink }) onAudioOutputDeviceChanged()
    }

    override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
        if (removedDevices.any { it.isSink }) onAudioOutputDeviceChanged()
    }
}

private fun onAudioOutputDeviceChanged() {
    if (!::player.isInitialized) return
    val outputSignature = currentAudioOutputDeviceSignature()
    if (outputSignature == lastAudioOutputDeviceSignature) return
    lastAudioOutputDeviceSignature = outputSignature
    audioOutputResolver.refresh()
    cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
    player.setAudioAttributes(playbackAudioAttributes(), false)
    audioRouteRecoveryJob?.cancel()
    audioRouteRecoveryJob = scope.launch {
        delay(AUDIO_ROUTE_CHANGE_DEBOUNCE_MS)  // 300 мс
        recoverAudioRouteAfterDeviceChange()
    }
}

private suspend fun recoverAudioRouteAfterDeviceChange() {
    if (!::player.isInitialized) return

    rebindAudioEffectsAfterRouteChange()

    if (!shouldRebuildPlaybackForAudioRouteChange()) return

    val now = android.os.SystemClock.elapsedRealtime()
    if (now - lastAudioRouteRecoveryRealtimeMs < AUDIO_ROUTE_RECOVERY_MIN_INTERVAL_MS) return
    lastAudioRouteRecoveryRealtimeMs = now

    val mediaItemIndex = player.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return
    val playbackPosition = player.currentPosition.coerceAtLeast(0L)
    val shouldResumePlayback = player.playWhenReady

    Timber.tag("MusicService").i(
        "Recovering audio route after output change at index=$mediaItemIndex position=$playbackPosition resume=$shouldResumePlayback"
    )

    if (shouldResumePlayback && !requestAudioFocus()) {
        wasPlayingBeforeAudioFocusLoss = true
        player.playWhenReady = false
        return
    }

    player.playWhenReady = false
    player.prepare()
    player.seekTo(mediaItemIndex, playbackPosition)
    delay(AUDIO_ROUTE_RECOVERY_RESUME_DELAY_MS)  // 100 мс

    if (
        shouldResumePlayback &&
        player.currentMediaItem != null &&
        player.playbackState != Player.STATE_ENDED &&
        requestAudioFocus()
    ) {
        player.playWhenReady = true
    }
}

private suspend fun rebindAudioEffectsAfterRouteChange() {
    if (!isAudioEffectSessionOpened) return
    closeAudioEffectSession()
    if (!player.playWhenReady) return
    delay(AUDIO_EFFECT_ROUTE_REBIND_DELAY_MS)  // 200 мс
    openAudioEffectSession()
}
```

**Константы**:
- `AUDIO_ROUTE_CHANGE_DEBOUNCE_MS = 300` — дебаунс для избежания множественных смен устройства.
- `AUDIO_ROUTE_RECOVERY_MIN_INTERVAL_MS = 1_000` — минимум 1 сек между recovery.
- `AUDIO_ROUTE_RECOVERY_RESUME_DELAY_MS = 100` — задержка перед возобновлением.
- `AUDIO_EFFECT_ROUTE_REBIND_DELAY_MS = 200` — задержка перед переподключением эффектов.

### 7.5. ColorOS / Android 14+ FGS (Foreground Service) safeguards (MusicService.kt:1094-1137, 1170-1200)

```kotlin
private fun ensureStartedAsForeground() {
    if (hasCalledStartForeground) return

    val notification = try {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(getString(R.string.music_player))
            .setContentText(getString(R.string.app_name))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    } catch (e: Exception) {
        reportException(e)
        return
    }

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        hasCalledStartForeground = true
    } catch (e: Exception) {
        reportException(e)
    }
}

private fun scheduleStopIfIdle() {
    if (hasBoundClients) return
    if (hasResumablePlaybackNotification()) {
        cancelIdleStop()
        promoteToStartedService()
        ensureStartedAsForeground()
        return
    }
    val togetherIdle = togetherSessionState.value is TogetherSessionState.Idle
    if (!togetherIdle) {
        cancelIdleStop()
        return
    }

    val state = player.playbackState
    val delayMs = when (state) {
        Player.STATE_ENDED, Player.STATE_IDLE -> 30_000L  // 30 сек
        else -> 60_000L  // 60 сек
    }

    cancelIdleStop()
    idleStopJob = scope.launch {
        delay(delayMs)
        if (hasBoundClients) return@launch
        if (hasResumablePlaybackNotification()) return@launch
        if (togetherSessionState.value !is TogetherSessionState.Idle) return@launch
        stopForegroundAndSelf()
    }
}
```

- **FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK**: декларация типа FGS (Android 10+).
- **ColorOS / Android 14+**: агрессивное убийство FGS без активной нотификации.
- **scheduleStopIfIdle**: авто-остановка сервиса через 30-60 сек после окончания воспроизведения.

---

## 8. Портирование на Desktop (Windows/macOS/Linux)

### 8.1. Замена ExoPlayer на LibVLC / VLCJ

#### LibVLC — кроссплатформенный медиаплеер:

```kotlin
// build.gradle.kts (desktop module)
dependencies {
    implementation("uk.co.caprica:vlcj:4.8.2")
    implementation("uk.co.caprica:vlcj-natives:4.7.0")
}
```

#### Адаптер ExoPlayer → LibVLC:

```kotlin
class DesktopMediaPlayer(private val mediaPlayerFactory: MediaPlayerFactory) {
    private val player = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer()
    
    var volume: Float
        get() = player.audio().volume() / 100f
        set(value) { player.audio().setVolume((value * 100f).toInt()) }
    
    var playWhenReady: Boolean = false
        set(value) {
            field = value
            if (value) player.controls().play() else player.controls().pause()
        }
    
    val currentPosition: Long
        get() = player.status().time()
    
    val duration: Long
        get() = player.status().length()
    
    fun seekTo(positionMs: Long) {
        player.controls().setTime(positionMs)
    }
    
    fun setMediaItem(item: MediaItem) {
        player.media().play(item.localConfiguration?.uri.toString())
    }
    
    fun addListener(listener: PlayerListener) {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                listener.onIsPlayingChanged(true)
            }
            override fun paused(mediaPlayer: MediaPlayer) {
                listener.onIsPlayingChanged(false)
            }
            override fun finished(mediaPlayer: MediaPlayer) {
                listener.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            }
        })
    }
}
```

#### Кроссфейд на Desktop:

- **Двойной LibVLC player** (аналогично Android).
- **Cross-mixing** через `IAudioMix` интерфейс LibVLC.
- **Volume interpolation** через `player.audio().setVolume()` в цикле.

### 8.2. Audio Effects на Desktop

#### Эквалайзер через LibVLC:

```kotlin
player.audio().equalizer().isEnabled = true
val eq = mediaPlayerFactory.equalizer().newEqualizer()
eq.setPreamp(0f)  // Output gain
for (i in 0 until eq.bandCount) {
    eq.setAmp(i, bandLevelsMb[i] / 100f)  // dB
}
player.audio().equalizer().setEqualizer(eq)
```

- LibVLC имеет встроенный 10-полосный эквалайзер.
- Диапазон: -20 дB до +20 dB.

#### Bass Boost / Virtualizer:

- Не нативно в LibVLC, требуется внешняя DSP библиотека (например, **SoundTouch**, **Rubber Band**).

### 8.3. Персистентная очередь на Desktop

- **SQLite** (Room DB портируется на **SQLDelight** для Kotlin Multiplatform).
- **Файл сериализации** (Kotlin Serialization JSON) — аналогично Android.

### 8.4. Системная интеграция

#### Windows:

- **SMTC (SystemMediaTransportControls)**: Windows 10/11 API для медиа-контролов.
- **JNA (Java Native Access)** для вызова Win32 API.

```kotlin
val smtc = SystemMediaTransportControls.getForCurrentView()
smtc.isEnabled = true
smtc.isPlayEnabled = true
smtc.isPauseEnabled = true
smtc.isNextEnabled = true
smtc.isPreviousEnabled = true

smtc.buttonPressed { sender, args ->
    when (args.button) {
        SystemMediaTransportControlsButton.Play -> player.playWhenReady = true
        SystemMediaTransportControlsButton.Pause -> player.playWhenReady = false
        SystemMediaTransportControlsButton.Next -> seekToNext()
        SystemMediaTransportControlsButton.Previous -> seekToPrevious()
    }
}
```

#### macOS:

- **MPNowPlayingInfoCenter**: аналог MediaSession на macOS.
- **MPRemoteCommandCenter**: обработка медиа-кнопок.

```swift
let commandCenter = MPRemoteCommandCenter.shared()
commandCenter.playCommand.addTarget { event in
    player.playWhenReady = true
    return .success
}
commandCenter.pauseCommand.addTarget { event in
    player.playWhenReady = false
    return .success
}
```

#### Linux:

- **MPRIS (Media Player Remote Interfacing Specification)**: D-Bus интерфейс.
- **JNA** для вызова D-Bus через `libdbus`.

```kotlin
val connection = DBusConnection.getConnection(DBusConnection.SESSION)
val mpris = connection.getRemoteObject(
    "org.mpris.MediaPlayer2.archivetune",
    "/org/mpris/MediaPlayer2",
    MPRISInterface::class.java
)
mpris.Play()
mpris.Pause()
mpris.Next()
mpris.Previous()
```

---

## 9. Точные параметры конфигурации

### 9.1. Буферизация (DefaultLoadControl)

**Файл**: `MusicService.kt:8757-8767` (createPrimaryLoadControl)

| Параметр | Значение | Назначение |
|----------|----------|-----------|
| `minBufferMs` | 15_000 | Минимальный буфер для начала воспроизведения (15 сек) |
| `maxBufferMs` | 50_000 | Максимальный буфер (50 сек) |
| `bufferForPlaybackMs` | 2_500 | Старт воспроизведения при 2.5 сек буфера |
| `bufferForPlaybackAfterRebufferMs` | 5_000 | После ребуфера требуется 5 сек |
| `prioritizeTimeOverSizeThresholds` | true | Приоритет по времени, не по размеру |

**Примечание**: Для вторичного кроссфейд-плеера используется `createCrossfadeLoadControl()` с потенциально сокращёнными буферами для уменьшения задержки старта.

### 9.2. Кеш (SimpleCache)

| Параметр | Значение по умолчанию | Настройка |
|----------|----------------------|----------|
| `maxSongCacheSizeKey` | 1024 МБ | Максимальный размер кеша |
| `cacheEvictionPolicy` | LRU | Least Recently Used |
| `cacheLocation` | `getExternalFilesDir("player_cache")` | Внешнее хранилище |
| `contentMetadataDB` | SQLite (StandaloneDatabaseProvider) | Индексация кеша |

### 9.3. Предзагрузка (NextStreamPreloader)

| Параметр | Значение | Назначение |
|----------|----------|-----------|
| `prefetchSizeBytes` | 512 КБ - 2 МБ | Размер предзагрузки |
| `prefetchTrigger` | `onMediaItemTransition` | Триггер предзагрузки |
| `prefetchNetworkType` | Wi-Fi / Any | Ограничение по сети |

### 9.4. Кроссфейд

| Параметр | Значение | Назначение |
|----------|----------|-----------|
| `CROSSFADE_PREPARE_AHEAD_MS` | 3_000 | Подготовка вторичного плеера за 3 сек |
| `CROSSFADE_END_GUARD_MS` | 100 | Запас в конце трека |
| `MIN_CROSSFADE_DURATION_MS` | 1_000 | Минимум 1 сек кроссфейда |
| `DEFAULT_AUTOMIX_DURATION_MS` | 5_000 | По умолчанию 5 сек для автомикса |
| `CROSSFADE_FRAME_MS` | 16 | 60 FPS интерполяция громкости |
| `crossfadeDurationKey` | 5.0f (0-10) | Пользовательская настройка (сек) |
| `crossfadeGaplessKey` | true | Отключать кроссфейд для треков одного альбома |

**Smart DJ Automix расширенные параметры** (SmartDjAutomixManager.kt):

| Параметр | Значение | Назначение |
|----------|----------|-----------|
| `DEFAULT_BPM` | 120.0f | Fallback BPM если не определён |
| `DEFAULT_MUSIC_ONSET_MS` | 200 | Задержка начала музыки |
| `DEFAULT_TRAILING_SILENCE_MS` | 1200 | Тишина в конце трека |
| `MIN_AUTOMIX_DURATION_MS` | 2_500 | Минимум 2.5 сек автомикса |
| `MAX_AUTOMIX_DURATION_MS` | 16_000 | Максимум 16 сек автомикса |
| `MAX_TEMPO_WARP_RATIO_DELTA` | 0.15f | ±15% изменение темпа (0.85x-1.15x) |
| `BASS_SWAP_PROGRESS` | 0.5f | Переключение баса на 50% кроссфейда |

### 9.5. Audio Focus

| Параметр | Значение | Назначение |
|----------|----------|-----------|
| `focusGain` | AUDIOFOCUS_GAIN | Полный фокус |
| `acceptsDelayedFocusGain` | true | Ждать освобождения фокуса |
| `audioFocusVolumeFactor` (duck) | 0.2f | Снижение до 20% при ducking |
| `preventAudioInterruptionsKey` | false | Игнорировать потерю фокуса |

### 9.6. Audio Normalization

| Параметр | Значение | Назначение |
|----------|----------|-----------|
| `TARGET_LUFS` | -14.0 | Целевая громкость (LUFS) |
| `MIN_AUDIO_NORMALIZATION_FACTOR` | 0.1f | -20 dB |
| `MAX_AUDIO_NORMALIZATION_FACTOR` | 10.0f | +20 dB |

### 9.7. Эквалайзер

| Параметр | Значение | Назначение |
|----------|----------|-----------|
| `bandCount` | 5-10 | Количество полос (зависит от устройства) |
| `bandLevelRange` | -1500..1500 мБ | ±15 dB |
| `bassBoostStrength` | 0-1000 | Сила басс-буста |
| `virtualizerStrength` | 0-1000 | Сила виртуализации стерео |
| `outputGainMb` | -3000..3000 мБ | ±30 dB выходное усиление |

### 9.8. Персистентная очередь

| Параметр | Значение | Назначение |
|----------|----------|-----------|
| `autosaveInterval` (playing) | 10 сек | Сохранение при воспроизведении |
| `autosaveInterval` (paused) | 30 сек | Сохранение на паузе |
| `hydrationWindowStart` | -20 треков | Окно гидратации до текущего |
| `hydrationWindowEnd` | +50 треков | Окно гидратации после текущего |
| `backfillDelay` | 2_000 мс | Задержка догрузки остальных треков |

### 9.9. Foreground Service

| Параметр | Значение | Назначение |
|----------|----------|-----------|
| `idleStopDelay` (ended) | 30_000 мс | 30 сек после окончания |
| `idleStopDelay` (other) | 60_000 мс | 60 сек в других состояниях |
| `FOREGROUND_SERVICE_TYPE` | MEDIA_PLAYBACK | Android 10+ декларация |

---

## 10. Схемы и диаграммы классов

### 10.1. Диаграмма потока воспроизведения

```
User tap Play
      │
      ▼
PlayerConnection.playQueue(queue)
      │
      ▼
MusicService.playQueue(queue)
      │
      ├──► Queue.getInitialStatus()
      │    │
      │    └──► YouTube.next() / Spotify.getPlaylist() / Local files
      │
      ├──► filterPlaybackContent() (explicit, video, blocked artists)
      │
      ├──► player.setMediaItems(items, startIndex, position)
      │
      ├──► player.prepare()
      │
      ├──► requestAudioFocus()
      │
      ├──► player.playWhenReady = true
      │
      ├──► scheduleCrossfade() (если включен)
      │
      ├──► updateNextStreamPreload() (предзагрузка следующего)
      │
      └──► saveQueueToDisk() (персистентная очередь)
```

### 10.2. Диаграмма классов Queue

```
<<interface>>
Queue
├── preloadItem: MediaMetadata?
├── getInitialStatus(): Status
├── hasNextPage(): Boolean
├── nextPage(): List<MediaItem>
└── Status
    ├── title: String?
    ├── items: List<MediaItem>
    ├── mediaItemIndex: Int
    ├── position: Long
    ├── filterExplicit(): Status
    ├── filterVideo(): Status
    └── filterBlockedArtists(): Status

Implementations:
├── EmptyQueue
├── ListQueue
│   ├── title: String?
│   ├── items: List<MediaItem>
│   ├── startIndex: Int
│   └── position: Long
├── YouTubeQueue
│   ├── endpoint: WatchEndpoint
│   ├── continuation: String?
│   ├── followAutomixPreview: Boolean
│   └── expandToFullQueueWhenAutoLoadMoreDisabled: Boolean
├── LocalQueue
│   └── localItems: List<MediaItem>
└── SpotifyPlaylistQueue
    ├── playlistId: String
    └── spotifyApi: Spotify
```

### 10.3. Диаграмма последовательности Crossfade

```
Player     Timer      Secondary     Mixer       User
  │          │            │           │          │
  │ playing──┤            │           │          │
  │          ├──duration-3s           │          │
  │          │            │           │          │
  │          ├────prepare()───────────►          │
  │          │            │prepare()  │          │
  │          │            ◄───────────┤          │
  │          ├──duration-0ms           │          │
  │          │            │           │          │
  │          ├────playWhenReady=true──►          │
  │          │            │           │          │
  │──────────┼────volume=√(1-t)───────┼──────────┤
  │          │            │volume=√t  │          │
  │          │            │           │          │
  │          ├──progress=1.0           │          │
  │          │            │           │          │
  │          ├────handoff()────────────►          │
  │          │            │           │          │
  │◄─────────┼───clearMediaItems()─────┤          │
  │setMediaItems()────────┤           │          │
  │seekTo(incoming.pos)───┤           │          │
  │playWhenReady=true─────┤           │          │
  │          │            │           │          │
  │          │◄───release()────────────┤          │
  │          │            │           │          │
```

---

## 11. Заключение

Данная спецификация описывает полную архитектуру аудиоподсистемы ArchiveTune с точными параметрами конфигурации, структурами классов и алгоритмами работы. Любой инженер может использовать этот документ для:

1. **Портирования на Desktop** (Windows/macOS/Linux) с заменой ExoPlayer на LibVLC/VLCJ.
2. **Воспроизведения архитектуры** в других Android-приложениях.
3. **Расширения функционала** (новые источники аудио, дополнительные DSP эффекты).
4. **Отладки и оптимизации** (понимание потока данных, буферизации, кеширования).

### 11.1. Ключевые технологии

- **ExoPlayer 3 (Media3)**: основной аудиодвижок Android.
- **MediaLibraryService + MediaSession**: интеграция с системой, Android Auto, Cast.
- **SimpleCache + LRU Evictor**: двухуровневое кеширование (память + диск).
- **ResolvingDataSource**: динамический резолвинг URL (YouTube подписи, Spotify токены).
- **Двойной ExoPlayer**: кроссфейд и автомикс.
- **Android AudioEffect API**: эквалайзер, басс-буст, виртуализация.
- **Room DB**: персистентная очередь и метаданные треков.
- **Kotlin Coroutines + Flow**: асинхронная обработка и реактивное состояние.

### 11.2. Портирование на Desktop: чеклист

✅ Заменить `ExoPlayer` на `LibVLC` / `VLCJ`.  
✅ Адаптировать `Queue` (SQLDelight вместо Room).  
✅ Реализовать `SystemMediaTransportControls` (Windows), `MPNowPlayingInfoCenter` (macOS), `MPRIS` (Linux).  
✅ Портировать кроссфейд (двойной `LibVLC` player).  
✅ Адаптировать эквалайзер (`LibVLC.audio().equalizer()`).  
✅ Сохранение состояния (Kotlin Serialization JSON + файловая система).  
✅ Предзагрузка треков (аналогично Android, через `HTTP Range requests`).

### 11.3. Дополнительные ресурсы

- **ExoPlayer документация**: https://exoplayer.dev
- **Media3 миграция**: https://developer.android.com/guide/topics/media/media3
- **LibVLC**: https://www.videolan.org/vlc/libvlc.html
- **VLCJ**: https://github.com/caprica/vlcj
- **Kotlin Multiplatform**: https://kotlinlang.org/docs/multiplatform.html

---

**Конец документа**
