package moe.rukamori.archivetune.ui.component

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal const val DEFAULT_DROPDOWN_SUPPRESS_WINDOW_MS = 260L

/**
 * Coordinates Compose [androidx.compose.material3.DropdownMenu] expansion state so tapping the
 * anchor button while the menu is already open cleanly closes it instead of immediately reopening.
 *
 * Android's underlying popup window dispatches `onDismissRequest` on outside touch-down before the
 * anchor button receives its `onClick` on touch-up. Recording the outside-dismiss timestamp and
 * suppressing `toggle()` during a short window prevents the anchor click from flipping `expanded`
 * right back to `true`.
 */
@Stable
class DropdownMenuToggleState(
    initialExpanded: Boolean = false,
    private val suppressWindowMs: Long = DEFAULT_DROPDOWN_SUPPRESS_WINDOW_MS,
    private val clockMs: () -> Long = { SystemClock.uptimeMillis() },
) {
    var expanded by mutableStateOf(initialExpanded)
        private set

    private var lastOutsideDismissUptimeMs: Long = Long.MIN_VALUE

    private fun shouldSuppressReopen(nowMs: Long): Boolean {
        val dismissedAt = lastOutsideDismissUptimeMs
        if (dismissedAt == Long.MIN_VALUE) return false
        val elapsed = nowMs - dismissedAt
        return elapsed in 0..suppressWindowMs
    }

    /**
     * Invoked by the anchor/trigger button `onClick`.
     * Returns the resulting [expanded] state.
     */
    fun toggle(): Boolean {
        val now = clockMs()
        if (expanded) {
            expanded = false
            lastOutsideDismissUptimeMs = Long.MIN_VALUE
            return false
        }
        if (shouldSuppressReopen(now)) {
            expanded = false
            return false
        }
        expanded = true
        lastOutsideDismissUptimeMs = Long.MIN_VALUE
        return true
    }

    /**
     * Opens the menu unless an outside dismiss just occurred within the suppression window.
     */
    fun open(): Boolean {
        return if (expanded) true else toggle()
    }

    /**
     * Invoked by `DropdownMenu(onDismissRequest = ...)`.
     * Records the outside-dismiss timestamp so a same-gesture anchor click does not reopen the menu.
     */
    fun onDismissRequest() {
        expanded = false
        lastOutsideDismissUptimeMs = clockMs()
    }

    /**
     * Invoked when a menu item is selected or the menu is closed programmatically without an
     * outside touch-down dismiss, allowing immediate subsequent reopening.
     */
    fun closeFromItemSelection() {
        expanded = false
        lastOutsideDismissUptimeMs = Long.MIN_VALUE
    }

    /**
     * Resets expansion state when the anchor disappears due to navigation, scroll, or data change.
     */
    fun syncAnchorPresence(visible: Boolean) {
        if (!visible) {
            expanded = false
            lastOutsideDismissUptimeMs = Long.MIN_VALUE
        }
    }

    /**
     * Adapts an externally hoisted boolean state to the same outside-dismiss suppression rules.
     */
    fun onExternalTriggerClick(
        currentExpanded: Boolean,
        setExpanded: (Boolean) -> Unit,
    ): Boolean {
        val now = clockMs()
        if (currentExpanded) {
            expanded = false
            lastOutsideDismissUptimeMs = Long.MIN_VALUE
            setExpanded(false)
            return false
        }
        if (shouldSuppressReopen(now)) {
            expanded = false
            setExpanded(false)
            return false
        }
        expanded = true
        lastOutsideDismissUptimeMs = Long.MIN_VALUE
        setExpanded(true)
        return true
    }

    fun onExternalDismissRequest(setExpanded: (Boolean) -> Unit) {
        expanded = false
        lastOutsideDismissUptimeMs = clockMs()
        setExpanded(false)
    }

    fun onExternalItemSelection(setExpanded: (Boolean) -> Unit) {
        expanded = false
        lastOutsideDismissUptimeMs = Long.MIN_VALUE
        setExpanded(false)
    }
}

@Composable
fun rememberDropdownMenuToggle(
    initialExpanded: Boolean = false,
    anchorVisible: Boolean = true,
): DropdownMenuToggleState {
    val state = remember { DropdownMenuToggleState(initialExpanded = initialExpanded) }
    LaunchedEffect(anchorVisible) {
        state.syncAnchorPresence(anchorVisible)
    }
    return state
}
