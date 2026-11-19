/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.HapticIntensity
import moe.rukamori.archivetune.constants.HapticIntensityKey
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

val LocalHapticIntensity = staticCompositionLocalOf { HapticIntensity.STANDARD }

enum class ArchiveTuneHapticKind {
    PLAY_PAUSE,
    SKIP,
    LIKE,
    SELECTION,
    LONG_PRESS,
}

fun effectiveHapticIntensity(
    legacyEnabled: Boolean,
    intensity: HapticIntensity,
): HapticIntensity = if (!legacyEnabled) HapticIntensity.OFF else intensity

fun performArchiveTuneHaptic(
    view: View?,
    hapticFeedback: HapticFeedback?,
    intensity: HapticIntensity,
    legacyEnabled: Boolean = true,
    kind: ArchiveTuneHapticKind = ArchiveTuneHapticKind.SELECTION,
) {
    val resolved = effectiveHapticIntensity(legacyEnabled, intensity)
    if (resolved == HapticIntensity.OFF) return

    if (view != null) {
        val constant =
            when (resolved) {
                HapticIntensity.OFF -> return
                HapticIntensity.LIGHT ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        HapticFeedbackConstants.CLOCK_TICK
                    } else {
                        HapticFeedbackConstants.KEYBOARD_TAP
                    }
                HapticIntensity.STANDARD ->
                    when (kind) {
                        ArchiveTuneHapticKind.PLAY_PAUSE,
                        ArchiveTuneHapticKind.LIKE,
                        ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                HapticFeedbackConstants.CONTEXT_CLICK
                            } else {
                                HapticFeedbackConstants.VIRTUAL_KEY
                            }
                        ArchiveTuneHapticKind.SKIP -> HapticFeedbackConstants.VIRTUAL_KEY
                        ArchiveTuneHapticKind.SELECTION ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                HapticFeedbackConstants.CLOCK_TICK
                            } else {
                                HapticFeedbackConstants.KEYBOARD_TAP
                            }
                        ArchiveTuneHapticKind.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
                    }
                HapticIntensity.STRONG ->
                    when (kind) {
                        ArchiveTuneHapticKind.PLAY_PAUSE,
                        ArchiveTuneHapticKind.LIKE,
                        ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                HapticFeedbackConstants.CONFIRM
                            } else {
                                HapticFeedbackConstants.LONG_PRESS
                            }
                        ArchiveTuneHapticKind.SKIP,
                        ArchiveTuneHapticKind.LONG_PRESS,
                        -> HapticFeedbackConstants.LONG_PRESS
                        ArchiveTuneHapticKind.SELECTION -> HapticFeedbackConstants.VIRTUAL_KEY
                    }
            }
        view.performHapticFeedback(constant)
        return
    }

    val composeType =
        when (resolved) {
            HapticIntensity.OFF -> return
            HapticIntensity.LIGHT -> HapticFeedbackType.TextHandleMove
            HapticIntensity.STANDARD ->
                if (kind == ArchiveTuneHapticKind.LONG_PRESS) {
                    HapticFeedbackType.LongPress
                } else {
                    HapticFeedbackType.TextHandleMove
                }
            HapticIntensity.STRONG -> HapticFeedbackType.LongPress
        }
    hapticFeedback?.performHapticFeedback(composeType)
}

@Composable
fun rememberArchiveTuneHapticTrigger(): (ArchiveTuneHapticKind) -> Unit {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val legacyEnabled = rememberPreference(EnableHapticFeedbackKey, true).value
    val prefIntensity = rememberEnumPreference(HapticIntensityKey, HapticIntensity.STANDARD).value
    return { kind ->
        performArchiveTuneHaptic(
            view = view,
            hapticFeedback = haptic,
            intensity = prefIntensity,
            legacyEnabled = legacyEnabled,
            kind = kind,
        )
    }
}
