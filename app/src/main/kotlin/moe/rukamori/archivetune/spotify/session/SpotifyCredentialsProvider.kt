/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.session

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.constants.SpotifySpKeyKey
import moe.rukamori.archivetune.utils.dataStore
import java.io.File

/**
 * Secure ContentProvider allowing sibling builds (Debug <-> Release) signed with
 * the same certificate to synchronize Spotify credentials and cookies seamlessly.
 */
class SpotifyCredentialsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null

        // Verify that caller is either this same process or a sibling app (debug <-> release)
        val callerUid = Binder.getCallingUid()
        if (callerUid != Process.myUid()) {
            val callerPackages = ctx.packageManager.getPackagesForUid(callerUid).orEmpty()
            val isAuthorized = callerPackages.any { pkg ->
                pkg == "moe.rukamori.archivetune" ||
                    pkg == "moe.rukamori.archivetune.debug" ||
                    ctx.packageManager.checkSignatures(ctx.packageName, pkg) == PackageManager.SIGNATURE_MATCH
            }
            if (!isAuthorized) {
                return null
            }
        }

        val cursor = MatrixCursor(arrayOf("credentials", "sp_dc", "sp_key", "access_token", "refresh_token", "expires_at", "username", "auth_blob", "auth_type"))
        val file = File(ctx.filesDir, "spotify_credentials.json")
        val content = if (file.exists() && file.length() > 0) file.readText() else null
        val prefs = runCatching { runBlocking(Dispatchers.IO) { ctx.dataStore.data.first() } }.getOrNull()
        val spDc = prefs?.get(SpotifySpDcKey).orEmpty()
        val spKey = prefs?.get(SpotifySpKeyKey).orEmpty()

        val store = SpotifyCredentialsStore(ctx)
        val accessToken = store.getSavedAccessToken()
        val refreshToken = store.getSavedRefreshToken()
        val expiresAt = store.getSavedTokenExpiresAt()
        val username = store.getSavedUsername()
        val authBlob = store.getSavedAuthBlob()
        val authBlobBase64 = authBlob?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        val authType = store.getSavedAuthType()

        val hasAny = content != null || spDc.isNotBlank() || !accessToken.isNullOrBlank() || !username.isNullOrBlank()
        if (hasAny) {
            cursor.addRow(
                arrayOf(
                    content.orEmpty(),
                    spDc,
                    spKey,
                    accessToken.orEmpty(),
                    refreshToken.orEmpty(),
                    expiresAt.toString(),
                    username.orEmpty(),
                    authBlobBase64.orEmpty(),
                    authType.orEmpty(),
                )
            )
        }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.moe.rukamori.archivetune.spotify"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
