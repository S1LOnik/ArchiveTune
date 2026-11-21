/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.Preferences
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
internal fun LiquidGlassPreferences(positions: PreferencePositions) {
    val (intensity, setIntensity) = rememberEnumPreference(LiquidGlassIntensityKey, LiquidGlassIntensity.STANDARD)
    PreferenceGroup(title = stringResource(R.string.studio_glass_details)) {
        item {
            GlassToggle(LiquidGlassEnabledKey, true, R.string.liquid_glass_effects, "liquid_glass_effects", positions)
        }
        item {
            Column(modifier = positions.modifierFor("liquid_glass_intensity")) {
                EnumListPreference(
                    title = { Text(stringResource(R.string.liquid_glass_intensity)) },
                    icon = { Icon(painterResource(R.drawable.tune), null) },
                    selectedValue = intensity,
                    onValueSelected = setIntensity,
                    valueText = {
                        stringResource(
                            when (it) {
                                LiquidGlassIntensity.SUBTLE -> R.string.liquid_glass_intensity_subtle
                                LiquidGlassIntensity.STANDARD -> R.string.liquid_glass_intensity_standard
                                LiquidGlassIntensity.VIVID -> R.string.liquid_glass_intensity_vivid
                            },
                        )
                    },
                )
            }
        }
        item {
            GlassSlider(LiquidGlassRefractionHeightKey, LIQUID_GLASS_REFRACTION_HEIGHT_DEFAULT, 0f..1f,
                R.string.liquid_glass_refraction_height, "liquid_glass_refraction_height", positions)
        }
        item {
            GlassSlider(LiquidGlassRefractionAmountKey, LIQUID_GLASS_REFRACTION_AMOUNT_DEFAULT, 0f..1f,
                R.string.liquid_glass_refraction_amount, "liquid_glass_refraction_amount", positions)
        }
        item {
            GlassSlider(LiquidGlassBlurRadiusKey, LIQUID_GLASS_BLUR_RADIUS_DEFAULT, 0f..1f,
                R.string.liquid_glass_blur_radius, "liquid_glass_blur_radius", positions)
        }
        item {
            GlassSlider(LiquidGlassTintOpacityKey, LIQUID_GLASS_TINT_OPACITY_DEFAULT, 0f..2f,
                R.string.liquid_glass_tint_opacity, "liquid_glass_tint_opacity", positions)
        }
        item {
            GlassSlider(LiquidGlassShadowDepthKey, LIQUID_GLASS_SHADOW_DEPTH_DEFAULT, 0f..1f,
                R.string.liquid_glass_shadow_depth, "liquid_glass_shadow_depth", positions)
        }
        item {
            GlassToggle(LiquidGlassDepth3DKey, LIQUID_GLASS_DEPTH_3D_DEFAULT,
                R.string.liquid_glass_depth_3d, "glass_depth_3d", positions)
        }
        item {
            GlassToggle(LiquidGlassChromaticAberrationKey, LIQUID_GLASS_CHROMATIC_ABERRATION_DEFAULT,
                R.string.liquid_glass_chromatic_aberration, "glass_chromatic_aberration", positions)
        }
        item {
            GlassToggle(LiquidGlassBackdropVibrancyKey, LIQUID_GLASS_BACKDROP_VIBRANCY_DEFAULT,
                R.string.liquid_glass_backdrop_vibrancy, "glass_backdrop_vibrancy", positions)
        }
        item {
            GlassToggle(LiquidGlassAdaptiveLuminanceKey, LIQUID_GLASS_ADAPTIVE_LUMINANCE_DEFAULT,
                R.string.liquid_glass_adaptive_luminance, "glass_adaptive_luminance", positions)
        }
    }
}

@Composable
private fun GlassSlider(
    key: Preferences.Key<Float>,
    default: Float,
    range: ClosedFloatingPointRange<Float>,
    title: Int,
    anchor: String,
    positions: PreferencePositions,
) {
    val (value, setValue) = rememberPreference(key, default)
    PreferenceEntry(
        modifier = positions.modifierFor(anchor),
        title = { Text(stringResource(title)) },
        content = {
            Slider(
                value = value.coerceIn(range),
                onValueChange = setValue,
                valueRange = range,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun GlassToggle(
    key: Preferences.Key<Boolean>,
    default: Boolean,
    title: Int,
    anchor: String,
    positions: PreferencePositions,
) {
    val (value, setValue) = rememberPreference(key, default)
    SwitchPreference(
        modifier = positions.modifierFor(anchor),
        title = { Text(stringResource(title)) },
        checked = value,
        onCheckedChange = setValue,
    )
}
