/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.motion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import moe.rukamori.archivetune.constants.BackgroundMotionPolicy
import moe.rukamori.archivetune.constants.MotionProfile

/**
 * Resolved app-wide motion policy derived from [MotionProfile], OS animator scale,
 * the legacy `disableAnimations` override, and per-channel toggles.
 */
@Immutable
data class MotionConfig(
    val profile: MotionProfile = MotionProfile.SYSTEM,
    val legacyDisableAnimations: Boolean = false,
    val systemAnimatorScale: Float = 1f,
    val screenTransitionsChannel: Boolean = true,
    val artworkMotionChannel: Boolean = true,
    val pressFeedbackChannel: Boolean = true,
    val decorativeBackgroundChannel: Boolean = true,
) {
    val animationsEnabled: Boolean
        get() =
            !legacyDisableAnimations &&
                profile != MotionProfile.REDUCED &&
                (profile != MotionProfile.SYSTEM || systemAnimatorScale > 0.01f)

    val effectiveProfile: MotionProfile
        get() = if (!animationsEnabled) MotionProfile.REDUCED else profile

    val screenTransitionsEnabled: Boolean
        get() = animationsEnabled && screenTransitionsChannel

    val artworkMotionEnabled: Boolean
        get() = animationsEnabled && artworkMotionChannel

    val pressFeedbackEnabled: Boolean
        get() = animationsEnabled && pressFeedbackChannel

    val decorativeBackgroundEnabled: Boolean
        get() = animationsEnabled && decorativeBackgroundChannel

    fun <T> sheetSpring(): FiniteAnimationSpec<T> =
        when (effectiveProfile) {
            MotionProfile.REDUCED -> snap()
            MotionProfile.FAST ->
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                )
            MotionProfile.SMOOTH ->
                spring(
                    dampingRatio = 0.92f,
                    stiffness = Spring.StiffnessLow,
                )
            MotionProfile.SYSTEM ->
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
        }

    fun <T> sheetSoftSpring(): FiniteAnimationSpec<T> =
        when (effectiveProfile) {
            MotionProfile.REDUCED -> snap()
            MotionProfile.FAST ->
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            MotionProfile.SMOOTH ->
                spring(
                    dampingRatio = 0.94f,
                    stiffness = 160f,
                )
            MotionProfile.SYSTEM ->
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow,
                )
        }

    fun <T> navSpring(): FiniteAnimationSpec<T> =
        when (effectiveProfile) {
            MotionProfile.REDUCED -> snap()
            MotionProfile.FAST ->
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                )
            MotionProfile.SMOOTH ->
                spring(
                    dampingRatio = 0.88f,
                    stiffness = Spring.StiffnessLow,
                )
            MotionProfile.SYSTEM ->
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 320f,
                )
        }

    fun <T> pressSpring(): FiniteAnimationSpec<T> =
        if (!pressFeedbackEnabled) {
            snap()
        } else {
            when (effectiveProfile) {
                MotionProfile.REDUCED -> snap()
                MotionProfile.FAST -> spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessHigh)
                MotionProfile.SMOOTH -> spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
                MotionProfile.SYSTEM -> spring(dampingRatio = 0.80f, stiffness = 950f)
            }
        }

    fun <T> trackSwapSpec(): FiniteAnimationSpec<T> =
        if (!artworkMotionEnabled) {
            snap()
        } else {
            when (effectiveProfile) {
                MotionProfile.REDUCED -> snap()
                MotionProfile.FAST -> tween(durationMillis = 190, easing = FastOutSlowInEasing)
                MotionProfile.SMOOTH -> tween(durationMillis = 360, easing = FastOutSlowInEasing)
                MotionProfile.SYSTEM -> tween(durationMillis = 270, easing = FastOutSlowInEasing)
            }
        }

    fun tabTransitionDurationMs(): Int =
        if (!screenTransitionsEnabled) {
            0
        } else {
            when (effectiveProfile) {
                MotionProfile.REDUCED -> 0
                MotionProfile.FAST -> 180
                MotionProfile.SMOOTH -> 340
                MotionProfile.SYSTEM -> 260
            }
        }

    fun subScreenTransitionDurationMs(): Int =
        if (!screenTransitionsEnabled) {
            0
        } else {
            when (effectiveProfile) {
                MotionProfile.REDUCED -> 0
                MotionProfile.FAST -> 210
                MotionProfile.SMOOTH -> 380
                MotionProfile.SYSTEM -> 300
            }
        }

    fun <T> microTween(
        baseMillis: Int = 200,
        easing: Easing = FastOutSlowInEasing,
    ): FiniteAnimationSpec<T> {
        if (!animationsEnabled) return snap()
        val factor =
            when (effectiveProfile) {
                MotionProfile.REDUCED -> 0f
                MotionProfile.FAST -> 0.65f
                MotionProfile.SMOOTH -> 1.25f
                MotionProfile.SYSTEM -> 1f
            }
        val duration = (baseMillis * factor).toInt().coerceAtLeast(1)
        return tween(durationMillis = duration, easing = easing)
    }

    val highlightIsStatic: Boolean
        get() = !animationsEnabled

    companion object {
        val System = MotionConfig()
        val Reduced = MotionConfig(profile = MotionProfile.REDUCED)
    }
}

val LocalMotionConfig = staticCompositionLocalOf { MotionConfig.System }

val LocalPlayerBackgroundMotionPolicy =
    staticCompositionLocalOf { BackgroundMotionPolicy.RESPECT_POWER_SAVER }

fun shouldRunDecorativeMotion(
    isForeground: Boolean,
    isVisible: Boolean,
    isPlaying: Boolean,
    isPowerSaveMode: Boolean,
    motionConfig: MotionConfig,
    policy: BackgroundMotionPolicy,
): Boolean {
    if (!isForeground || !isVisible) return false
    if (!motionConfig.decorativeBackgroundEnabled) return false
    return when (policy) {
        BackgroundMotionPolicy.NEVER -> false
        BackgroundMotionPolicy.WHILE_PLAYING_OPEN -> isPlaying
        BackgroundMotionPolicy.RESPECT_POWER_SAVER -> isPlaying && !isPowerSaveMode
    }
}

@Composable
fun rememberSystemAnimatorScale(): Float {
    val context = LocalContext.current
    var scale by remember(context) {
        mutableFloatStateOf(readSystemAnimatorScale(context))
    }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    scale = readSystemAnimatorScale(context)
                }
            }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        if (uri != null) {
            runCatching { resolver.registerContentObserver(uri, false, observer) }
        }
        onDispose {
            runCatching { resolver.unregisterContentObserver(observer) }
        }
    }

    return scale
}

private fun readSystemAnimatorScale(context: Context): Float =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)

@Composable
fun rememberAppForegroundState(): State<Boolean> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialForeground =
        remember(lifecycleOwner) {
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
    val foregroundState = remember(lifecycleOwner) { mutableStateOf(initialForeground) }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START,
                    Lifecycle.Event.ON_RESUME,
                    -> foregroundState.value = true

                    Lifecycle.Event.ON_STOP,
                    Lifecycle.Event.ON_DESTROY,
                    -> foregroundState.value = false

                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return foregroundState
}

@Composable
fun rememberPowerSaveModeState(): State<Boolean> {
    val context = LocalContext.current
    val powerManager =
        remember(context) {
            context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        }
    val powerSaveState = remember(powerManager) { mutableStateOf(powerManager?.isPowerSaveMode == true) }

    DisposableEffect(context, powerManager) {
        powerSaveState.value = powerManager?.isPowerSaveMode == true
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    powerSaveState.value = powerManager?.isPowerSaveMode == true
                }
            }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        runCatching { context.applicationContext.registerReceiver(receiver, filter) }
        onDispose {
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
        }
    }
    return powerSaveState
}

@Composable
fun rememberDecorativeMotionActive(
    isPlaying: Boolean,
    isVisible: Boolean,
): Boolean {
    val isForeground by rememberAppForegroundState()
    val isPowerSaveMode by rememberPowerSaveModeState()
    val motionConfig = LocalMotionConfig.current
    val policy = LocalPlayerBackgroundMotionPolicy.current
    return remember(isForeground, isVisible, isPlaying, isPowerSaveMode, motionConfig, policy) {
        shouldRunDecorativeMotion(
            isForeground = isForeground,
            isVisible = isVisible,
            isPlaying = isPlaying,
            isPowerSaveMode = isPowerSaveMode,
            motionConfig = motionConfig,
            policy = policy,
        )
    }
}
