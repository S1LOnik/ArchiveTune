/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.mascot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.MascotAiEnabledKey
import moe.rukamori.archivetune.constants.MascotCharacterKey
import moe.rukamori.archivetune.constants.MascotChatterFrequencyKey
import moe.rukamori.archivetune.constants.MascotEnabledKey
import moe.rukamori.archivetune.constants.MascotPlatformPhysicsKey
import moe.rukamori.archivetune.constants.MascotScaleKey
import moe.rukamori.archivetune.constants.MascotSpeechEnabledKey
import moe.rukamori.archivetune.constants.MascotUiReactionsKey
import moe.rukamori.archivetune.constants.MascotWanderEnabledKey
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun MascotOverlay(
    modifier: Modifier = Modifier,
    bottomNavHeight: androidx.compose.ui.unit.Dp = 80.dp,
    miniPlayerCollapsedBound: androidx.compose.ui.unit.Dp = 0.dp,
    isMiniPlayerVisible: Boolean = false,
    isPlayerExpanded: Boolean = false,
) {
    val (mascotEnabled) = rememberPreference(MascotEnabledKey, defaultValue = false)
    if (!mascotEnabled) return

    val (mascotSpeechEnabled) = rememberPreference(MascotSpeechEnabledKey, defaultValue = true)
    val (mascotScale) = rememberPreference(MascotScaleKey, defaultValue = 1.0f)
    val (mascotCharacterId) = rememberPreference(MascotCharacterKey, defaultValue = "claudie")
    val (mascotWanderEnabled) = rememberPreference(MascotWanderEnabledKey, defaultValue = true)
    val (mascotChatterFrequency) = rememberPreference(MascotChatterFrequencyKey, defaultValue = 1)
    val (mascotUiReactions) = rememberPreference(MascotUiReactionsKey, defaultValue = true)
    val (mascotPlatformPhysics) = rememberPreference(MascotPlatformPhysicsKey, defaultValue = true)
    val (mascotAiEnabled) = rememberPreference(MascotAiEnabledKey, defaultValue = false)

    val character = remember(mascotCharacterId) {
        MascotRegistry.getCharacter(mascotCharacterId)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val isPlaying = playerConnection?.isPlaying?.collectAsState()?.value ?: false
    val currentMediaMetadata = playerConnection?.mediaMetadata?.collectAsState()?.value

    var currentState by remember { mutableStateOf(MascotState.IDLE) }
    var speechText by remember { mutableStateOf<String?>(null) }
    var speechVisible by remember { mutableStateOf(false) }
    var speechJob by remember { mutableStateOf<Job?>(null) }
    var aiSpeechJob by remember { mutableStateOf<Job?>(null) }
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }

    fun speak(text: String, durationMs: Long = 3800L, priority: Int = 1) {
        if (!mascotSpeechEnabled) return
        if (mascotChatterFrequency == 0 && priority < 2) return
        speechJob?.cancel()
        speechText = text
        speechVisible = true
        speechJob = coroutineScope.launch {
            delay(durationMs)
            speechVisible = false
        }
    }

    fun speakWithOptionalAi(event: MascotEvent, durationMs: Long = 4200L, priority: Int = 1) {
        speak(character.getRandomLine(event), durationMs, priority)
        if (!mascotAiEnabled || !mascotSpeechEnabled) return
        aiSpeechJob?.cancel()
        aiSpeechJob = coroutineScope.launch {
            val title = (event as? MascotEvent.TrackStarted)?.title ?: currentMediaMetadata?.title
            val artist =
                (event as? MascotEvent.TrackStarted)?.artist
                    ?: currentMediaMetadata?.artists?.joinToString { it.name }
            val liveLine =
                MascotAiCommentator.generateLiveLineOrNull(
                    context = context,
                    playerConnection = playerConnection,
                    event = event,
                    currentTrackTitle = title,
                    currentTrackArtist = artist,
                )
            if (!liveLine.isNullOrBlank()) {
                speak(liveLine, durationMs = 5200L, priority = priority)
            }
        }
    }

    // Auto-reset active reaction animations back to IDLE / PLAYING after playing 1-2 times (3.5s)
    LaunchedEffect(currentState) {
        if (currentState != MascotState.IDLE &&
            currentState != MascotState.SLEEP &&
            currentState != MascotState.PLAYING
        ) {
            delay(3600L)
            if (isActive) {
                currentState = if (isPlaying) MascotState.PLAYING else MascotState.IDLE
            }
        }
    }

    // Reaction to playback state & track changes
    if (playerConnection != null) {
        MascotPlaybackEffects(
            playerConnection = playerConnection,
            character = character,
            onStateChange = { currentState = it },
            speak = { text, durationMs -> speak(text, durationMs) },
            onTrackStarted = { event -> speakWithOptionalAi(event, 4800L) },
        )
    }

    // Listen to UI interactions & navigation via MascotEventBus
    LaunchedEffect(character) {
        MascotEventBus.events.collect { event ->
            when (event) {
                is MascotEvent.NavigatedHome -> {
                    currentState = MascotState.IDLE
                    speak(character.getRandomLine(event), 3200L)
                }
                is MascotEvent.NavigatedLibrary -> {
                    currentState = listOf(MascotState.PARTY, MascotState.NOTES).random()
                    speak(character.getRandomLine(event), 3500L)
                }
                is MascotEvent.NavigatedSearch -> {
                    currentState = MascotState.SEARCH
                    speak(character.getRandomLine(event), 3500L)
                }
                is MascotEvent.NavigatedSettings -> {
                    currentState = MascotState.COFFEE
                    speak(character.getRandomLine(event), 3200L)
                }
                is MascotEvent.NavigatedHistory -> {
                    currentState = MascotState.NOTES
                    speak(character.getRandomLine(event), 3500L)
                }
                is MascotEvent.LibrarySongs,
                is MascotEvent.LibraryArtists,
                is MascotEvent.LibraryAlbums,
                is MascotEvent.LibraryPlaylists,
                is MascotEvent.LibraryPodcasts,
                is MascotEvent.LibrarySpotify,
                is MascotEvent.LibrarySoundCloud,
                is MascotEvent.LibraryGeneral -> {
                    currentState = listOf(
                        MascotState.DANCE,
                        MascotState.PLAYING,
                        MascotState.PARTY,
                        MascotState.GUITAR,
                        MascotState.NOTES
                    ).random()
                    speak(character.getRandomLine(event), 3500L)
                }
                is MascotEvent.TrackLiked -> {
                    currentState = MascotState.LOVE
                    speakWithOptionalAi(event, 3800L)
                }
                is MascotEvent.TrackUnliked -> {
                    currentState = MascotState.IDLE
                    speak(character.getRandomLine(event), 2800L)
                }
                is MascotEvent.VolumeChanged -> {
                    if (mascotUiReactions) {
                        currentState = if (event.isUp) MascotState.GUITAR else MascotState.PLAYING
                        speak(character.getRandomLine(event), 2800L, priority = 1)
                    }
                }
                is MascotEvent.PlayerMenuOpened -> {
                    if (mascotUiReactions) {
                        currentState = MascotState.NOTES
                        speak(character.getRandomLine(event), 3000L, priority = 1)
                    }
                }
                is MascotEvent.WallBounce -> {
                    if (mascotUiReactions) {
                        speak(character.getRandomLine(event), 3000L, priority = 2)
                    }
                }
                else -> {
                    speak(character.getRandomLine(event), 3000L)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val mascotSizeDp = (86 * mascotScale.coerceIn(0.6f, 1.6f)).dp
        val mascotSizePx = with(density) { mascotSizeDp.toPx() }
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Calibrate foot alignment: Lottie character sprite has ~11% transparent bottom margin
        val footOffsetPx = mascotSizePx * 0.11f

        val minXPx = with(density) { 10.dp.toPx() }
        val maxXPx = (screenWidthPx - mascotSizePx - minXPx).coerceAtLeast(minXPx)
        val minYPx = with(density) { 68.dp.toPx() }

        // Platform physics levels:
        // 1. MiniPlayer platform: feet rest directly on top of the mini-player
        val miniPlayerPlatformY = if (isMiniPlayerVisible && miniPlayerCollapsedBound > 0.dp) {
            val miniPlayerTopPx = screenHeightPx - with(density) { miniPlayerCollapsedBound.toPx() }
            (miniPlayerTopPx - mascotSizePx + footOffsetPx).coerceIn(minYPx, screenHeightPx - mascotSizePx)
        } else {
            val fallbackMiniTop = screenHeightPx - with(density) { 148.dp.toPx() }
            (fallbackMiniTop - mascotSizePx + footOffsetPx).coerceIn(minYPx, screenHeightPx - mascotSizePx)
        }

        // 2. Navigation bar platform: feet rest directly on top of floating/docked bottom nav
        val navBarTopPx = screenHeightPx - with(density) { bottomNavHeight.toPx() }
        val navBarPlatformY = (navBarTopPx - mascotSizePx + footOffsetPx).coerceIn(minYPx, screenHeightPx - mascotSizePx)

        // 3. Top bar platform
        val topBarPlatformY = minYPx

        val maxYPx = if (isMiniPlayerVisible) miniPlayerPlatformY else navBarPlatformY

        val currentPlatformLevel = remember(
            mascotPlatformPhysics,
            isPlaying,
            isPlayerExpanded,
            isMiniPlayerVisible,
            topBarPlatformY,
            miniPlayerPlatformY,
            navBarPlatformY,
        ) {
            when {
                isPlayerExpanded -> topBarPlatformY
                mascotPlatformPhysics -> if (isMiniPlayerVisible) miniPlayerPlatformY else navBarPlatformY
                else -> minYPx + (navBarPlatformY - minYPx) * 0.45f
            }
        }

        val posX = remember { Animatable(maxXPx) }
        val posY = remember { Animatable(currentPlatformLevel) }
        val rotationZ = remember { Animatable(0f) }
        val scaleX = remember { Animatable(1f) }
        val scaleY = remember { Animatable(1f) }

        var isDragging by remember { mutableStateOf(false) }

        // Keep mascot aligned to platform level on dynamic UI changes
        LaunchedEffect(currentPlatformLevel) {
            if (!isDragging && (mascotPlatformPhysics || isPlayerExpanded)) {
                posY.animateTo(currentPlatformLevel, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
            }
        }

        // Rapid tap detection to trigger ANNOYED state
        var tapCount by remember { mutableIntStateOf(0) }
        var lastTapTime by remember { mutableLongStateOf(0L) }

        // Wandering / Platform Walking Mode with beat pacing
        LaunchedEffect(
            mascotWanderEnabled,
            currentState,
            isPlaying,
            isDragging,
            mascotPlatformPhysics,
            isPlayerExpanded,
            isMiniPlayerVisible,
        ) {
            if (!mascotWanderEnabled || isDragging) return@LaunchedEffect
            while (isActive) {
                val delayTime = if (isPlaying) (14_000L..26_000L).random() else (24_000L..42_000L).random()
                delay(delayTime)
                if (isDragging) continue
                if (currentState == MascotState.IDLE || currentState == MascotState.PLAYING || currentState == MascotState.NOTES || currentState == MascotState.GUITAR) {
                    val targetX = (minXPx.toInt()..maxXPx.toInt()).random().toFloat()
                    val isMovingRight = targetX > posX.value

                    scaleX.animateTo(if (isMovingRight) 1f else -1f, spring(stiffness = Spring.StiffnessMedium))

                    val distance = abs(targetX - posX.value)
                    val steps = (distance / with(density) { 28.dp.toPx() }).toInt().coerceIn(3, 8)

                    // Desired walking ground Y
                    val groundY = when {
                        isPlayerExpanded -> topBarPlatformY
                        mascotPlatformPhysics -> if (isMiniPlayerVisible) miniPlayerPlatformY else navBarPlatformY
                        else -> posY.value
                    }

                    // Walk / pace animation
                    currentState = MascotState.WALK

                    for (step in 0 until steps) {
                        if (isDragging) break
                        val curTargetX = posX.value + (targetX - posX.value) / (steps - step)
                        coroutineScope.launch {
                            // Walking hop with beat bounce
                            val hopHeight = if (isPlaying) 18f else 12f
                            posY.animateTo((groundY - hopHeight).coerceAtLeast(minYPx), spring(stiffness = Spring.StiffnessHigh))
                            posY.animateTo(groundY, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium))
                        }
                        posX.animateTo(curTargetX, spring(stiffness = Spring.StiffnessLow))
                        delay(130L)
                    }
                    scaleX.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                    currentState = if (isPlaying) {
                        listOf(MascotState.PLAYING, MascotState.NOTES, MascotState.GUITAR).random()
                    } else {
                        MascotState.IDLE
                    }

                    if (Random.nextFloat() < 0.40f) {
                        speak(character.getRandomLine(MascotEvent.WanderWalk), 3200L, priority = 1)
                    }
                }
            }
        }

        val velocityTracker = remember { VelocityTracker() }

        val composition by rememberLottieComposition(
            spec = LottieCompositionSpec.Asset(character.getAnimationAsset(currentState))
        )

        // Mascot Figure
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = posX.value.roundToInt(),
                        y = posY.value.roundToInt(),
                    )
                }
                .graphicsLayer {
                    this.rotationZ = rotationZ.value
                    this.scaleX = scaleX.value
                    this.scaleY = scaleY.value
                }
                .pointerInput(character.id) {
                    detectTapGestures(
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 900L) {
                                tapCount++
                            } else {
                                tapCount = 1
                            }
                            lastTapTime = now

                            coroutineScope.launch {
                                launch {
                                    scaleX.animateTo(1.22f, spring(stiffness = Spring.StiffnessMediumLow))
                                    scaleX.animateTo(1.0f, spring(stiffness = Spring.StiffnessMedium))
                                }
                                launch {
                                    scaleY.animateTo(0.78f, spring(stiffness = Spring.StiffnessMediumLow))
                                    scaleY.animateTo(1.0f, spring(stiffness = Spring.StiffnessMedium))
                                }
                                launch {
                                    val jumpY = (posY.value - 40f).coerceAtLeast(minYPx)
                                    posY.animateTo(jumpY, spring(stiffness = Spring.StiffnessHigh))
                                    posY.animateTo(posY.value + 40f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
                                }
                            }

                            if (tapCount >= 3) {
                                currentState = MascotState.ANNOYED
                                speakWithOptionalAi(MascotEvent.RapidTapMascot, 3800L, priority = 2)
                                tapCount = 0
                            } else {
                                val tapStates = listOf(
                                    MascotState.WAVE,
                                    MascotState.LAUGH,
                                    MascotState.JUMP,
                                    MascotState.PARTY,
                                    MascotState.GUITAR,
                                    MascotState.LIKE,
                                )
                                currentState = tapStates.random()
                                speakWithOptionalAi(MascotEvent.TapMascot, 3600L)
                            }
                        },
                        onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope.launch {
                                launch {
                                    scaleX.animateTo(1.3f, spring(stiffness = Spring.StiffnessHigh))
                                    scaleX.animateTo(1.0f, spring(stiffness = Spring.StiffnessMedium))
                                }
                                launch {
                                    scaleY.animateTo(1.3f, spring(stiffness = Spring.StiffnessHigh))
                                    scaleY.animateTo(1.0f, spring(stiffness = Spring.StiffnessMedium))
                                }
                            }
                            if (playerConnection != null) {
                                val isCurrentlyPlaying = playerConnection.isPlaying.value
                                if (isCurrentlyPlaying) {
                                    playerConnection.player.pause()
                                } else {
                                    playerConnection.player.play()
                                }
                            }
                            currentState = MascotState.PARTY
                            speakWithOptionalAi(MascotEvent.DoubleTapMascot, 3600L)
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope.launch {
                                rotationZ.animateTo(-15f, spring(stiffness = Spring.StiffnessMedium))
                                rotationZ.animateTo(15f, spring(stiffness = Spring.StiffnessMedium))
                                rotationZ.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                            }
                            currentState = MascotState.LOVE
                            speakWithOptionalAi(MascotEvent.LongPressMascot, 3800L, priority = 2)
                        }
                    )
                }
                .pointerInput(character.id) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            velocityTracker.resetTracking()
                            coroutineScope.launch {
                                scaleX.animateTo(1.12f, spring(stiffness = Spring.StiffnessLow))
                                scaleY.animateTo(1.12f, spring(stiffness = Spring.StiffnessLow))
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            val newX = (posX.value + dragAmount.x).coerceIn(minXPx - 16f, maxXPx + 16f)
                            val newY = (posY.value + dragAmount.y).coerceIn(minYPx - 16f, maxYPx + 16f)

                            coroutineScope.launch {
                                posX.snapTo(newX)
                                posY.snapTo(newY)
                                val tilt = (dragAmount.x * 0.45f).coerceIn(-22f, 22f)
                                rotationZ.snapTo(tilt)
                            }

                            if (abs(dragAmount.x) > 24 || abs(dragAmount.y) > 24) {
                                currentState = MascotState.DIZZY
                            }
                        },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity()
                            coroutineScope.launch {
                                launch { scaleX.animateTo(1.0f, spring(stiffness = Spring.StiffnessMedium)) }
                                launch { scaleY.animateTo(1.0f, spring(stiffness = Spring.StiffnessMedium)) }
                                launch { rotationZ.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }

                                val finalX = (posX.value + velocity.x * 0.12f).coerceIn(minXPx, maxXPx)

                                val targetY = if (mascotPlatformPhysics) {
                                    // Land on closest platform (TopBar, MiniPlayer, or NavBar)
                                    val candidatePlatforms = if (isMiniPlayerVisible) {
                                        listOf(topBarPlatformY, miniPlayerPlatformY)
                                    } else {
                                        listOf(topBarPlatformY, navBarPlatformY)
                                    }
                                    val currentY = posY.value + velocity.y * 0.12f
                                    candidatePlatforms.minByOrNull { abs(it - currentY) } ?: if (isMiniPlayerVisible) miniPlayerPlatformY else navBarPlatformY
                                } else {
                                    (posY.value + velocity.y * 0.14f).coerceIn(minYPx, maxYPx)
                                }

                                launch {
                                    posX.animateTo(
                                        targetValue = finalX,
                                        animationSpec = spring(
                                            dampingRatio = 0.65f,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                    )
                                }

                                launch {
                                    posY.animateTo(
                                        targetValue = targetY,
                                        animationSpec = spring(
                                            dampingRatio = 0.68f,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                    )
                                    // Landing bounce
                                    if (mascotPlatformPhysics) {
                                        scaleY.animateTo(0.82f, spring(stiffness = Spring.StiffnessHigh))
                                        scaleY.animateTo(1.0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
                                    }
                                }

                                currentState = if (isPlaying) MascotState.PLAYING else MascotState.IDLE
                                val isFast = abs(velocity.x) > 1100f || abs(velocity.y) > 1100f
                                if (isFast) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    speak(character.getRandomLine(MascotEvent.WallBounce), 3000L)
                                } else if (mascotPlatformPhysics) {
                                    speak(character.getRandomLine(MascotEvent.PlatformLanded), 2800L, priority = 1)
                                }
                                isDragging = false
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            coroutineScope.launch {
                                scaleX.animateTo(1f)
                                scaleY.animateTo(1f)
                                rotationZ.animateTo(0f)
                                currentState = if (isPlaying) MascotState.PLAYING else MascotState.IDLE
                            }
                        }
                    )
                }
        ) {
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(mascotSizeDp),
            )
        }

        // Speech Bubble - Clamped Positioning (Never overflows screen)
        AnimatedVisibility(
            visible = speechVisible && !speechText.isNullOrBlank(),
            enter = fadeIn() + scaleIn(initialScale = 0.75f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.offset {
                val bw = bubbleSize.width.toFloat()
                val bh = bubbleSize.height.toFloat()
                val isRightHalf = posX.value > (screenWidthPx / 2f)
                val isTopHalf = posY.value < (screenHeightPx * 0.28f)

                // Clamped horizontal position
                val bx = if (isRightHalf) {
                    (posX.value + mascotSizePx - bw).coerceIn(
                        minXPx,
                        (screenWidthPx - minXPx - bw).coerceAtLeast(minXPx)
                    )
                } else {
                    posX.value.coerceIn(
                        minXPx,
                        (screenWidthPx - minXPx - bw).coerceAtLeast(minXPx)
                    )
                }

                val by = if (isTopHalf) {
                    (posY.value + mascotSizePx + 8f).coerceIn(
                        minYPx,
                        (screenHeightPx - with(density) { 90.dp.toPx() } - bh).coerceAtLeast(minYPx)
                    )
                } else {
                    (posY.value - bh - 8f).coerceIn(
                        minYPx,
                        (screenHeightPx - with(density) { 90.dp.toPx() } - bh).coerceAtLeast(minYPx)
                    )
                }

                IntOffset(bx.roundToInt(), by.roundToInt())
            }
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .onSizeChanged { bubbleSize = it }
                    .widthIn(max = 220.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
            ) {
                Text(
                    text = speechText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun MascotPlaybackEffects(
    playerConnection: moe.rukamori.archivetune.playback.PlayerConnection,
    character: MascotCharacter,
    onStateChange: (MascotState) -> Unit,
    speak: (String, Long) -> Unit,
    onTrackStarted: (MascotEvent.TrackStarted) -> Unit,
) {
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    // Periodically cycle authentic music listening animations while track is playing
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            onStateChange(MascotState.PLAYING) // Put on headphones first!
            while (isActive) {
                delay((18_000L..32_000L).random())
                if (!isPlaying) break
                val musicalStates = listOf(
                    MascotState.PLAYING, // Headphones
                    MascotState.GUITAR,  // Guitar solo
                    MascotState.NOTES,   // Swaying to beat
                    MascotState.DANCE,   // Rave dance
                )
                onStateChange(musicalStates.random())
            }
        } else {
            onStateChange(MascotState.COFFEE)
            speak(character.getRandomLine(MascotEvent.PlaybackPaused), 3000L)
            delay(4000L)
            if (!isPlaying) {
                onStateChange(MascotState.IDLE)
            }
        }
    }

    LaunchedEffect(mediaMetadata?.id) {
        val title = mediaMetadata?.title
        val artist = mediaMetadata?.artists?.joinToString { it.name }.orEmpty()
        if (!title.isNullOrBlank()) {
            val musicalStates = listOf(
                MascotState.PLAYING,
                MascotState.GUITAR,
                MascotState.NOTES,
                MascotState.DANCE,
            )
            onStateChange(musicalStates.random())
            onTrackStarted(MascotEvent.TrackStarted(title, artist))
        }
    }
}
