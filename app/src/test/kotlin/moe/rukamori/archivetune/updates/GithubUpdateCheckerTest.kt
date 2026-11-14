package moe.rukamori.archivetune.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubUpdateCheckerTest {
    @Test
    fun newerPatchIsDetected() {
        assertTrue(GithubUpdateChecker.isNewer("16.2.1", "16.2.0"))
    }

    @Test
    fun newerMinorBeatsHigherPatch() {
        assertTrue(GithubUpdateChecker.isNewer("16.3.0", "16.2.9"))
    }

    @Test
    fun comparesNumericallyNotLexicographically() {
        assertTrue(GithubUpdateChecker.isNewer("16.10.0", "16.9.0"))
        assertFalse(GithubUpdateChecker.isNewer("16.9.0", "16.10.0"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(GithubUpdateChecker.isNewer("16.2.0", "16.2.0"))
    }

    @Test
    fun missingTrailingPartsAreTreatedAsZero() {
        assertFalse(GithubUpdateChecker.isNewer("16.2", "16.2.0"))
        assertTrue(GithubUpdateChecker.isNewer("16.2.1", "16.2"))
        assertFalse(GithubUpdateChecker.isNewer("16", "16.0.0"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(GithubUpdateChecker.isNewer("16.1.3", "16.2.0"))
    }

    @Test
    fun preReleaseSuffixIsIgnored() {
        assertFalse(GithubUpdateChecker.isNewer("16.2.0-beta", "16.2.0"))
        assertTrue(GithubUpdateChecker.isNewer("16.3.0-rc1", "16.2.0"))
    }
}
