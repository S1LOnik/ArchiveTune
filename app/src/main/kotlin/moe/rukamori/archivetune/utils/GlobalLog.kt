/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import moe.rukamori.archivetune.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MAX_ENTRIES = if (BuildConfig.DEBUG) 10_000 else 500
private val MAX_JOURNAL_BYTES = if (BuildConfig.DEBUG) 10 * 1024 * 1024 else 256 * 1024
private const val JOURNAL_DIRECTORY = "diagnostics"
private const val JOURNAL_FILE_NAME = "application.log"
private const val NULL_TAG = "-"

data class LogEntry(
    val time: Long,
    val level: Int,
    val tag: String?,
    val message: String,
)

/**
 * App-owned diagnostic journal.
 *
 * Android's log buffers are not durable and some OEM builds restrict access to them, so this is
 * deliberately independent of logcat. Entries are retained in the app's private storage and are
 * restored when the app starts again, including after a process death.
 */
object GlobalLog {
    private val lock = Any()
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    @Volatile
    private var journalFile: File? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (journalFile != null) return

            val directory = File(context.filesDir, JOURNAL_DIRECTORY)
            val file = File(directory, JOURNAL_FILE_NAME)
            journalFile = file
            _logs.value = readJournal(file)
        }
    }

    fun append(
        level: Int,
        tag: String?,
        message: String,
    ) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        synchronized(lock) {
            _logs.value = (_logs.value + entry).takeLast(MAX_ENTRIES)
            appendToJournal(entry)
        }
    }

    fun recordThrowable(
        throwable: Throwable,
        tag: String = "Exception",
        message: String = "Unhandled exception",
    ) {
        append(
            level = Log.ERROR,
            tag = tag,
            message = "$message\n${Log.getStackTraceString(throwable)}",
        )
    }

    fun clear() {
        synchronized(lock) {
            _logs.value = emptyList()
            runCatching { journalFile?.delete() }
        }
    }

    fun flush() {
        // Entries are synchronously synced to disk in appendToJournal
    }

    fun format(entry: LogEntry): String {
        val ts = synchronized(timeFormat) { timeFormat.format(Date(entry.time)) }
        val lvl =
            when (entry.level) {
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                else -> "?"
            }
        val tag = entry.tag.orEmpty()
        return "[$ts] $lvl/$tag: ${entry.message}"
    }

    private fun appendToJournal(entry: LogEntry) {
        val file = journalFile ?: return
        val directory = file.parentFile ?: return
        if (!directory.exists() && !directory.mkdirs()) return

        runCatching {
            FileOutputStream(file, true).use { output ->
                output.write(entry.toJournalLine().toByteArray(Charsets.UTF_8))
                // Make an app crash or OEM process kill much less likely to lose the final event.
                output.fd.sync()
            }
            trimJournalIfNeeded(file)
        }
    }

    private fun trimJournalIfNeeded(file: File) {
        if (file.length() <= MAX_JOURNAL_BYTES) return

        val retainedLines = ArrayDeque<String>()
        var retainedBytes = 0
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val lineBytes = line.toByteArray(Charsets.UTF_8).size + 1
                retainedLines.addLast(line)
                retainedBytes += lineBytes
                while (retainedBytes > MAX_JOURNAL_BYTES && retainedLines.isNotEmpty()) {
                    retainedBytes -= retainedLines.removeFirst().toByteArray(Charsets.UTF_8).size + 1
                }
            }
        }

        val temporaryFile = File(file.parentFile, "$JOURNAL_FILE_NAME.tmp")
        temporaryFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            retainedLines.forEach { line ->
                writer.appendLine(line)
            }
        }
        if (!temporaryFile.renameTo(file)) {
            temporaryFile.delete()
        }
    }

    private fun readJournal(file: File): List<LogEntry> =
        runCatching {
            if (!file.isFile) return@runCatching emptyList()
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                val entries = lines.mapNotNull(::parseJournalLine).toList()
                entries.takeLast(MAX_ENTRIES)
            }
        }.getOrDefault(emptyList())

    private fun LogEntry.toJournalLine(): String {
        val encodedTag = tag?.encodeJournalPart() ?: NULL_TAG
        return "$time\t$level\t$encodedTag\t${message.encodeJournalPart()}\n"
    }

    private fun parseJournalLine(line: String): LogEntry? {
        val parts = line.split('\t', limit = 4)
        if (parts.size != 4) return null
        val time = parts[0].toLongOrNull() ?: return null
        val level = parts[1].toIntOrNull() ?: return null
        val tag = if (parts[2] == NULL_TAG) null else parts[2].decodeJournalPart() ?: return null
        val message = parts[3].decodeJournalPart() ?: return null
        return LogEntry(time = time, level = level, tag = tag, message = message)
    }

    private fun String.encodeJournalPart(): String =
        Base64.encodeToString(toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun String.decodeJournalPart(): String? =
        runCatching {
            Base64.decode(this, Base64.NO_WRAP).toString(Charsets.UTF_8)
        }.getOrNull()
}

/** Timber Tree that forwards logs to [GlobalLog]. */
class GlobalLogTree : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        if (!BuildConfig.DEBUG && priority < Log.INFO) return
        try {
            val final = if (t != null) "$message\n${Log.getStackTraceString(t)}" else message
            GlobalLog.append(priority, tag, final)
        } catch (_: Exception) {
            // Diagnostics must never make the app fail.
        }
    }
}
