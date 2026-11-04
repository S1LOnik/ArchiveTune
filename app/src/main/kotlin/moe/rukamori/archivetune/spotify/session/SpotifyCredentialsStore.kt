/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.SecureRandom

/**
 * Secure storage for Spotify credentials using Android Keystore / EncryptedSharedPreferences.
 * Stores reusable Librespot auth credentials, tokens, and hardware-consistent device ID.
 */
class SpotifyCredentialsStore(context: Context) {

    private val securePrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(false)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Timber.tag("SpotifyCredentialsStore").w(e, "EncryptedSharedPreferences unavailable, falling back to private prefs")
        context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
    }

    val deviceId: String
        get() {
            var id = securePrefs.getString(KEY_DEVICE_ID, null)
            if (id == null || id.length != 40) {
                val randomBytes = ByteArray(20)
                SecureRandom().nextBytes(randomBytes)
                id = randomBytes.joinToString("") { "%02x".format(it) } // Exactly 40 hex characters
                securePrefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    fun saveCredentials(
        username: String,
        authData: ByteArray,
        authType: String = "AUTHENTICATION_STORED_SPOTIFY_CREDENTIALS",
    ) {
        val encodedBlob = Base64.encodeToString(authData, Base64.NO_WRAP)
        securePrefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_AUTH_BLOB, encodedBlob)
            .putString(KEY_AUTH_TYPE, authType)
            .apply()
    }

    fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long,
    ) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)
        val editor = securePrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
        if (refreshToken != null) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        editor.apply()
    }

    fun getSavedUsername(): String? = securePrefs.getString(KEY_USERNAME, null)

    fun getSavedAuthBlob(): ByteArray? {
        val encoded = securePrefs.getString(KEY_AUTH_BLOB, null) ?: return null
        return try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun getSavedAuthType(): String? = securePrefs.getString(KEY_AUTH_TYPE, null)

    fun getSavedAccessToken(): String? = securePrefs.getString(KEY_ACCESS_TOKEN, null)

    fun getSavedRefreshToken(): String? = securePrefs.getString(KEY_REFRESH_TOKEN, null)

    fun getSavedTokenExpiresAt(): Long = securePrefs.getLong(KEY_EXPIRES_AT, 0L)

    fun clearCredentials() {
        securePrefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_AUTH_BLOB)
            .remove(KEY_AUTH_TYPE)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    fun hasCredentials(): Boolean =
        (getSavedUsername() != null && getSavedAuthBlob() != null) ||
            (!getSavedAccessToken().isNullOrBlank())

    companion object {
        private const val PREFS_NAME = "spotify_secure_credentials"
        private const val KEY_DEVICE_ID = "spotify_device_id"
        private const val KEY_USERNAME = "spotify_username"
        private const val KEY_AUTH_BLOB = "spotify_auth_blob"
        private const val KEY_AUTH_TYPE = "spotify_auth_type"
        private const val KEY_ACCESS_TOKEN = "spotify_oauth_access_token"
        private const val KEY_REFRESH_TOKEN = "spotify_oauth_refresh_token"
        private const val KEY_EXPIRES_AT = "spotify_oauth_expires_at"
    }
}
