/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HomeFeedStyle
import moe.rukamori.archivetune.constants.HomeFeedStyleKey
import moe.rukamori.archivetune.constants.HomeHiddenSectionsKey
import moe.rukamori.archivetune.constants.HomeSectionOrderKey
import moe.rukamori.archivetune.ui.motion.ArchiveTuneHapticKind
import moe.rukamori.archivetune.ui.motion.rememberArchiveTuneHapticTrigger
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.dataStore
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCustomizerSheet(
    currentStyle: HomeFeedStyle,
    sectionOrderRaw: String,
    hiddenSections: Set<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = rememberArchiveTuneHapticTrigger()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var orderedSections by remember(sectionOrderRaw) {
        mutableStateOf(HomeLayoutPolicy.resolveSectionOrder(sectionOrderRaw))
    }
    var draggingId by remember { mutableStateOf<HomeSectionId?>(null) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 58.dp.toPx() }

    val persistOrder: (List<HomeSectionId>) -> Unit = { updated ->
        orderedSections = updated
        PreferenceStore.launchEdit(context.dataStore) {
            this[HomeSectionOrderKey] = HomeLayoutPolicy.serializeSectionOrder(updated)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_customize_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = {
                        haptic(ArchiveTuneHapticKind.SELECTION)
                        orderedSections = HomeSectionId.DefaultOrder
                        PreferenceStore.launchEdit(context.dataStore) {
                            remove(HomeSectionOrderKey)
                            remove(HomeHiddenSectionsKey)
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painterResource(R.drawable.restore),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.home_customize_reset_order))
                }
            }

            Text(
                text = stringResource(R.string.home_customize_layouts),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(HomeFeedStyle.entries, key = { it.name }) { style ->
                    val selected = style == currentStyle
                    HomeLayoutWireframeCard(
                        style = style,
                        selected = selected,
                        onClick = {
                            haptic(ArchiveTuneHapticKind.SELECTION)
                            PreferenceStore.launchEdit(context.dataStore) {
                                this[HomeFeedStyleKey] = style.name
                            }
                        },
                    )
                }
            }

            Text(
                text = stringResource(R.string.home_customize_sections),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(
                    items = orderedSections,
                    key = { _, section -> section.key },
                ) { index, section ->
                    val isVisible = section.key !in hiddenSections
                    val isDragging = draggingId == section
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color =
                            if (isDragging) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragDeltaY else 0f
                                },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(34.dp)
                                        .pointerInput(section, orderedSections) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptic(ArchiveTuneHapticKind.LONG_PRESS)
                                                    draggingId = section
                                                    dragDeltaY = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragDeltaY += dragAmount.y
                                                    val stepShift = (dragDeltaY / rowHeightPx).roundToInt()
                                                    if (stepShift != 0) {
                                                        val curIdx = orderedSections.indexOf(section)
                                                        val targetIdx =
                                                            (curIdx + stepShift).coerceIn(0, orderedSections.lastIndex)
                                                        if (targetIdx != curIdx && curIdx >= 0) {
                                                            orderedSections =
                                                                HomeLayoutPolicy.reorderSection(
                                                                    orderedSections,
                                                                    curIdx,
                                                                    targetIdx,
                                                                )
                                                            dragDeltaY -= (targetIdx - curIdx) * rowHeightPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingId = null
                                                    dragDeltaY = 0f
                                                    persistOrder(orderedSections)
                                                },
                                                onDragCancel = {
                                                    draggingId = null
                                                    dragDeltaY = 0f
                                                },
                                            )
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.drag_handle),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Text(
                                text = stringResource(section.titleRes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isVisible) FontWeight.Medium else FontWeight.Normal,
                                color =
                                    if (isVisible) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            IconButton(
                                onClick = {
                                    haptic(ArchiveTuneHapticKind.SELECTION)
                                    persistOrder(HomeLayoutPolicy.moveSectionUp(orderedSections, section))
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_upward),
                                    contentDescription = stringResource(R.string.home_move_up),
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic(ArchiveTuneHapticKind.SELECTION)
                                    persistOrder(HomeLayoutPolicy.moveSectionDown(orderedSections, section))
                                },
                                enabled = index < orderedSections.lastIndex,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_downward),
                                    contentDescription = stringResource(R.string.home_move_down),
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            Switch(
                                checked = isVisible,
                                onCheckedChange = {
                                    haptic(ArchiveTuneHapticKind.SELECTION)
                                    val updatedHidden =
                                        HomeLayoutPolicy.toggleSectionVisibility(hiddenSections, section)
                                    PreferenceStore.launchEdit(context.dataStore) {
                                        this[HomeHiddenSectionsKey] = updatedHidden
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeLayoutWireframeCard(
    style: HomeFeedStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val block = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val accentBlock = primary.copy(alpha = 0.55f)
    val labelRes =
        when (style) {
            HomeFeedStyle.STANDARD -> R.string.home_feed_style_standard
            HomeFeedStyle.COMPACT -> R.string.home_feed_style_compact
            HomeFeedStyle.GALLERY -> R.string.home_feed_style_gallery
            HomeFeedStyle.NOW -> R.string.home_feed_style_now
            HomeFeedStyle.COVERS -> R.string.home_feed_style_covers
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .width(88.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                )
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onClick)
                .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(54.dp)) {
            val w = size.width
            val h = size.height
            val gap = 4.dp.toPx()
            val cr = CornerRadius(4.dp.toPx())
            when (style) {
                HomeFeedStyle.STANDARD -> {
                    drawRoundRect(accentBlock, Offset.Zero, Size(w, h * 0.44f), cr)
                    drawRoundRect(block, Offset(0f, h * 0.52f), Size(w * 0.48f, h * 0.46f), cr)
                    drawRoundRect(block, Offset(w * 0.52f, h * 0.52f), Size(w * 0.48f, h * 0.46f), cr)
                }
                HomeFeedStyle.COMPACT -> {
                    repeat(4) { i ->
                        val top = i * (h / 4f)
                        drawRoundRect(
                            if (i == 0) accentBlock else block,
                            Offset(0f, top + 1f),
                            Size(w, h / 4f - gap),
                            cr,
                        )
                    }
                }
                HomeFeedStyle.GALLERY -> {
                    drawRoundRect(accentBlock, Offset.Zero, Size(w, h * 0.58f), cr)
                    drawRoundRect(block, Offset(0f, h * 0.68f), Size(w * 0.30f, h * 0.30f), cr)
                    drawRoundRect(block, Offset(w * 0.35f, h * 0.68f), Size(w * 0.30f, h * 0.30f), cr)
                    drawRoundRect(block, Offset(w * 0.70f, h * 0.68f), Size(w * 0.30f, h * 0.30f), cr)
                }
                HomeFeedStyle.NOW -> {
                    drawRoundRect(accentBlock, Offset.Zero, Size(w, h * 0.30f), cr)
                    drawRoundRect(block, Offset(0f, h * 0.36f), Size(w * 0.48f, h * 0.22f), cr)
                    drawRoundRect(block, Offset(w * 0.52f, h * 0.36f), Size(w * 0.48f, h * 0.22f), cr)
                    drawRoundRect(block, Offset(0f, h * 0.65f), Size(w, h * 0.14f), cr)
                    drawRoundRect(block, Offset(0f, h * 0.84f), Size(w, h * 0.14f), cr)
                }
                HomeFeedStyle.COVERS -> {
                    val leftW = w * 0.56f
                    drawRoundRect(accentBlock, Offset.Zero, Size(leftW, h * 0.68f), cr)
                    val rightX = leftW + gap
                    val rightW = w - rightX
                    val smallH = (h * 0.68f - gap) / 2f
                    drawRoundRect(block, Offset(rightX, 0f), Size(rightW, smallH), cr)
                    drawRoundRect(block, Offset(rightX, smallH + gap), Size(rightW, smallH), cr)
                    drawRoundRect(block, Offset(0f, h * 0.76f), Size(w * 0.46f, h * 0.22f), cr)
                    drawRoundRect(block, Offset(w * 0.52f, h * 0.76f), Size(w * 0.48f, h * 0.22f), cr)
                }
            }
        }
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
