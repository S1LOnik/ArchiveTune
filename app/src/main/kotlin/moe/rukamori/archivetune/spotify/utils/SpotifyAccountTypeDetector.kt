/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.utils

import timber.log.Timber
import xyz.gianlu.librespot.core.Session

/**
 * Detects Spotify account subscription type (Free vs Premium) to adjust audio quality.
 *
 * Free accounts:
 * - 320 kbps blocked (AudioKeyFailure 403)
 * - 160 kbps maximum supported
 * - Requires ad telemetry for compliance
 *
 * Premium accounts:
 * - Full 320 kbps access
 * - No ad requirements
 */
object SpotifyAccountTypeDetector {
    private const val TAG = "SpotifyAccountType"

    enum class AccountType {
        FREE,
        PREMIUM,
        UNKNOWN
    }

    /**
     * Detects account type from active Librespot session.
     * Uses APWelcome product info to determine subscription status.
     */
    fun detectAccountType(session: Session?): AccountType {
        if (session == null || !session.isValid) {
            Timber.tag(TAG).w("Cannot detect account type: session invalid or null")
            return AccountType.UNKNOWN
        }

        return try {
            val apWelcome = session.apWelcome()
            if (apWelcome == null) {
                Timber.tag(TAG).w("APWelcome is null, cannot determine account type")
                return AccountType.UNKNOWN
            }

            // Check product type from APWelcome
            val productType = apWelcome.accountTypeLoggedIn
            val isPremium = when {
                productType == null -> false
                productType.toString().contains("premium", ignoreCase = true) -> true
                productType.toString().contains("unlimited", ignoreCase = true) -> true
                productType.toString().contains("free", ignoreCase = true) -> false
                else -> {
                    // Fallback: check if user has premium capabilities
                    apWelcome.hasCanonicalUsername() // Premium users typically have canonical usernames
                }
            }

            val accountType = if (isPremium) AccountType.PREMIUM else AccountType.FREE
            Timber.tag(TAG).i(
                "Detected account type: %s (product=%s, user=%s)",
                accountType,
                productType,
                session.username()
            )
            accountType
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to detect account type")
            AccountType.UNKNOWN
        }
    }

    /**
     * Returns recommended maximum audio quality for the account type.
     * Free: 160 kbps maximum to avoid 403 errors
     * Premium: 320 kbps maximum
     */
    fun getMaxQualityForAccount(accountType: AccountType): xyz.gianlu.librespot.audio.decoders.AudioQuality {
        return when (accountType) {
            AccountType.FREE -> xyz.gianlu.librespot.audio.decoders.AudioQuality.HIGH // 160 kbps
            AccountType.PREMIUM -> xyz.gianlu.librespot.audio.decoders.AudioQuality.VERY_HIGH // 320 kbps
            AccountType.UNKNOWN -> xyz.gianlu.librespot.audio.decoders.AudioQuality.HIGH // Safe fallback
        }
    }
}
