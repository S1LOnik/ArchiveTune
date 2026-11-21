/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MusicServiceIdleDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun pausedPlaybackReleasesForegroundAndCanResume() {
        val audio = File.createTempFile("playback-idle-", ".wav", context.cacheDir)
        val sampleRate = 8000
        val pcm = ByteArray(sampleRate * 2 * 120)
        val header =
            ByteBuffer
                .allocate(44)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray())
                .putInt(36 + pcm.size)
                .put("WAVEfmt ".toByteArray())
                .putInt(16)
                .putShort(1)
                .putShort(1)
                .putInt(sampleRate)
                .putInt(sampleRate * 2)
                .putShort(2)
                .putShort(16)
                .put("data".toByteArray())
                .putInt(pcm.size)
                .array()
        audio.outputStream().use {
            it.write(header)
            it.write(pcm)
        }

        val services = ArrayBlockingQueue<MusicService>(1)
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    services.add((binder as MusicService.MusicBinder).service)
                }

                override fun onServiceDisconnected(name: ComponentName) = Unit
            }
        val intent = Intent(context, MusicService::class.java)
        var bound = false
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            assertTrue(bound)
            val service = checkNotNull(services.poll(20, TimeUnit.SECONDS)) { "MusicService did not bind" }
            await("queue restoration", 20_000) { service.queueRestoreCompleted.value }
            instrumentation.runOnMainSync {
                service.player.setMediaItem(MediaItem.fromUri(Uri.fromFile(audio)))
                service.player.prepare()
                service.player.play()
            }
            await("local playback", 20_000) { isPlaying(service) }
            await("foreground playback service", 15_000) { isPlaybackForeground() }

            var pausedPosition = 0L
            instrumentation.runOnMainSync {
                service.player.pause()
                pausedPosition = service.player.currentPosition
            }
            await("foreground service release after pause", 80_000) { !isPlaybackForeground() }
            instrumentation.runOnMainSync {
                assertEquals(1, service.player.mediaItemCount)
                assertTrue(kotlin.math.abs(pausedPosition - service.player.currentPosition) <= 150L)
                service.player.play()
            }
            await("resumed playback", 15_000) { isPlaying(service) }
            await("foreground after resuming", 15_000) { isPlaybackForeground() }
            instrumentation.runOnMainSync {
                service.player.stop()
                service.player.clearMediaItems()
            }
        } finally {
            if (bound) context.unbindService(connection)
            context.stopService(intent)
            audio.delete()
        }
    }

    private fun isPlaying(service: MusicService): Boolean {
        var playing = false
        instrumentation.runOnMainSync { playing = service.player.isPlaying }
        return playing
    }

    private fun isPlaybackForeground(): Boolean =
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "dumpsys activity services ${context.packageName}/${MusicService::class.java.name}",
            ),
        ).bufferedReader().use { output ->
            output.lineSequence().any { "isForeground=true" in it }
        }

    private fun await(description: String, timeoutMs: Long, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            if (predicate()) return
            SystemClock.sleep(500L)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("Timed out waiting for $description")
    }
}
