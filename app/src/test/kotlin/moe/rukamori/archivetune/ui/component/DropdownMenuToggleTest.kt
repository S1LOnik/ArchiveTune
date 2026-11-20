package moe.rukamori.archivetune.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DropdownMenuToggleTest {

    @Test
    fun `outside dismiss followed by same-gesture trigger tap keeps menu closed`() {
        var nowMs = 1_000L
        val toggle = DropdownMenuToggleState(clockMs = { nowMs })

        assertFalse(toggle.expanded)

        // Initial tap opens the menu.
        assertTrue(toggle.toggle())
        assertTrue(toggle.expanded)

        // Touch-down on trigger button fires PopupWindow outside dismiss first.
        nowMs = 1_500L
        toggle.onDismissRequest()
        assertFalse(toggle.expanded)

        // Touch-up on trigger button fires onClick shortly after (< 260ms).
        nowMs = 1_540L
        assertFalse(toggle.toggle())
        assertFalse(toggle.expanded)
    }

    @Test
    fun `tap after suppress window reopens menu cleanly`() {
        var nowMs = 2_000L
        val toggle = DropdownMenuToggleState(clockMs = { nowMs })

        assertTrue(toggle.toggle())
        assertTrue(toggle.expanded)

        nowMs = 2_200L
        toggle.onDismissRequest()
        assertFalse(toggle.expanded)

        // Same-gesture tap within 260ms stays closed.
        nowMs = 2_260L
        assertFalse(toggle.toggle())
        assertFalse(toggle.expanded)

        // Tap after 260ms suppression window opens the menu again.
        nowMs = 2_461L
        assertTrue(toggle.toggle())
        assertTrue(toggle.expanded)
    }

    @Test
    fun `closeFromItemSelection allows immediate reopen without suppression`() {
        var nowMs = 5_000L
        val toggle = DropdownMenuToggleState(clockMs = { nowMs })

        assertTrue(toggle.toggle())
        assertTrue(toggle.expanded)

        // Selecting an item closes the menu without recording an outside dismiss timestamp.
        nowMs = 5_050L
        toggle.closeFromItemSelection()
        assertFalse(toggle.expanded)

        // Immediate subsequent tap (10ms later) reopens cleanly.
        nowMs = 5_060L
        assertTrue(toggle.toggle())
        assertTrue(toggle.expanded)
    }

    @Test
    fun `rapid double taps toggle open and closed predictably`() {
        var nowMs = 10_000L
        val toggle = DropdownMenuToggleState(clockMs = { nowMs })

        // Direct toggle open -> close -> open.
        assertTrue(toggle.toggle())
        assertTrue(toggle.expanded)

        nowMs = 10_020L
        assertFalse(toggle.toggle())
        assertFalse(toggle.expanded)

        nowMs = 10_040L
        assertTrue(toggle.toggle())
        assertTrue(toggle.expanded)

        // Outside dismiss + rapid double bounce within suppression window stays closed.
        nowMs = 10_100L
        toggle.onDismissRequest()
        nowMs = 10_115L
        assertFalse(toggle.toggle())
        nowMs = 10_190L
        assertFalse(toggle.toggle())
        assertFalse(toggle.expanded)
    }

    @Test
    fun `syncAnchorPresence resets expanded state when anchor disappears`() {
        var nowMs = 20_000L
        val toggle = DropdownMenuToggleState(clockMs = { nowMs })

        assertTrue(toggle.toggle())
        assertTrue(toggle.expanded)

        toggle.syncAnchorPresence(visible = false)
        assertFalse(toggle.expanded)

        // When anchor becomes visible again, tapping opens normally.
        toggle.syncAnchorPresence(visible = true)
        nowMs = 20_010L
        assertTrue(toggle.toggle())
        assertTrue(toggle.expanded)
    }
}
