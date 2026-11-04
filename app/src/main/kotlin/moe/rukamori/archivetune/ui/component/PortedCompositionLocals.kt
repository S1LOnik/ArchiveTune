/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

// [FIX-ZCODE] Докрутка портирования 18.0: composition-local'ы донора, на которые
// ссылаются перенесённые компоненты (плеер, меню, floating navbar).
val LocalUnglassColorScheme: ProvidableCompositionLocal<ColorScheme?> =
    compositionLocalOf { null }

@Composable
fun UnglassedDialogTheme(content: @Composable () -> Unit) {
    val unglassed = LocalUnglassColorScheme.current
    MaterialTheme(
        colorScheme = unglassed ?: MaterialTheme.colorScheme,
        content = content,
    )
}

val LocalGlassMenuContent = staticCompositionLocalOf { false }
