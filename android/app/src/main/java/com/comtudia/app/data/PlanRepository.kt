package com.comtudia.app.data

import android.content.Context
import android.net.Uri
import com.comtudia.app.core.Plan
import com.comtudia.app.core.PlanFormatException
import com.comtudia.app.core.PlanJson
import com.comtudia.app.core.ValidationException
import com.comtudia.app.core.requireValid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Owns the persisted plan (`plan.json`) and exposes it as a [StateFlow].
 *
 * The document format is the desktop format, so a plan can be moved between the
 * desktop app and the phone unchanged. Reads and writes are atomic: a temporary
 * file in the same directory is synced and renamed over the destination. A
 * corrupt file is never deleted or overwritten implicitly; [loadError] reports
 * the problem and [plan] falls back to [Plan.default] for this process only.
 */
class PlanRepository(
    context: Context,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope? = null,
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    /** The plan document on disk. */
    val file: File = File(File(appContext.filesDir, CONFIG_DIR), PLAN_FILE)

    private val _plan = MutableStateFlow(Plan.default())

    /** The current plan in memory, valid or the default fallback. */
    val plan: StateFlow<Plan> = _plan.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)

    /** A description of the last load problem, or `null` when the plan is fine. */
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    init {
        // The document is a few hundred bytes, so a synchronous first load keeps
        // the very first frame correct without noticeable delay.
        val loaded = loadFromDisk()
        _plan.value = loaded.plan
        _loadError.value = loaded.error
    }

    /** Returns the current plan without touching the disk. */
    fun current(): Plan = _plan.value

    /** Re-reads the plan from disk and publishes the result. */
    fun reload(): Plan {
        val loaded = onDisk { loadFromDisk() }
        _plan.value = loaded.plan
        _loadError.value = loaded.error
        return loaded.plan
    }

    /**
     * Validates and atomically writes [plan], then publishes it.
     *
     * The write is small, so it runs to completion before the result is
     * returned; the disk call itself is dispatched to [Dispatchers.IO].
     */
    fun save(plan: Plan): Result<Unit> {
        val next = plan.copy(version = Plan.VERSION)
        try {
            next.requireValid()
        } catch (e: ValidationException) {
            return Result.failure(e)
        }

        return runCatching {
            onDisk { writeToDisk(next) }
        }.onSuccess {
            _plan.value = next
            _loadError.value = null
        }
    }

    /** Reads a plan document from [uri], validates it and saves it. */
    fun importFrom(uri: Uri): Result<Plan> = runCatching {
        val text = onDisk { readText(uri) }
        val imported = PlanJson.decode(text)
        save(imported).getOrThrow()
        imported
    }

    /** Writes the current plan to [uri] as a desktop-compatible document. */
    fun exportTo(uri: Uri): Result<Unit> = runCatching {
        val text = PlanJson.encode(current())
        onDisk { writeText(uri, text) }
    }

    private fun <T> onDisk(block: () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun loadFromDisk(): Loaded = synchronized(lock) {
        if (!file.exists()) return@synchronized Loaded(Plan.default(), null)

        val text = try {
            readFileText(file)
        } catch (e: IOException) {
            return@synchronized Loaded(Plan.default(), "cannot read ${file.name}: ${e.message}")
        } catch (e: SecurityException) {
            return@synchronized Loaded(Plan.default(), "cannot read ${file.name}: ${e.message}")
        }

        try {
            Loaded(PlanJson.decode(text), null)
        } catch (e: PlanFormatException) {
            // Keep the file untouched so the user can recover or fix it.
            Loaded(Plan.default(), "${file.name}: ${e.message}")
        }
    }

    private fun writeToDisk(plan: Plan) {
        val bytes = PlanJson.encode(plan).toByteArray(Charsets.UTF_8)
        synchronized(lock) {
            val dir = file.parentFile ?: throw IOException("plan path has no directory")
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                throw IOException("cannot create ${dir.absolutePath}")
            }
            setOwnerOnly(dir, executable = true)

            val tmp = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, dir)
            try {
                FileOutputStream(tmp).use { out ->
                    out.write(bytes)
                    out.flush()
                    out.fd.sync()
                }
                setOwnerOnly(tmp, executable = false)
                if (!tmp.renameTo(file)) {
                    throw IOException("cannot replace ${file.absolutePath}")
                }
            } catch (e: Exception) {
                // Remove only the temporary file; the previous plan stays intact.
                tmp.delete()
                throw e
            }
        }
    }

    /**
     * Restricts [target] to the app user (0600, or 0700 with [executable]).
     * Android's per-app storage is already private, but the desktop counterpart
     * uses these modes and matching it keeps the files consistent.
     */
    private fun setOwnerOnly(target: File, executable: Boolean) {
        val readable = target.setReadable(true, true)
        val writable = target.setWritable(true, true)
        val executableSet = target.setExecutable(executable, false)
        if (!readable || !writable || !executableSet) {
            throw IOException("cannot restrict permissions on ${target.absolutePath}")
        }
    }

    private fun readFileText(file: File): String {
        val length = file.length()
        if (length > MAX_PLAN_BYTES) {
            throw IOException("plan file is larger than ${MAX_PLAN_BYTES / 1024} KiB")
        }
        return file.inputStream().use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun readText(uri: Uri): String {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("cannot open $uri")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) {
                    throw PlanFormatException("plan file is larger than 64 KiB")
                }
                out.write(buffer, 0, read)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    private fun writeText(uri: Uri, text: String) {
        val output = appContext.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("cannot open $uri for writing")
        output.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
        }
    }

    private data class Loaded(val plan: Plan, val error: String?)

    companion object {
        private const val CONFIG_DIR = "comtudia"
        private const val PLAN_FILE = "plan.json"
        private const val TEMP_PREFIX = ".plan-"
        private const val TEMP_SUFFIX = ".tmp"
        private const val MAX_PLAN_BYTES = 1024L * 1024L
        private const val MAX_IMPORT_BYTES = 64 * 1024
    }
}
