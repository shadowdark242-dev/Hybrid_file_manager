package org.fossify.filemanager.helpers

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Timer
import java.util.TimerTask

/**
 * A long-lived `su` shell that runs one command at a time and returns its output lines.
 *
 * Unlike RootTools it has no command timeout (a slow folder never leaves the UI waiting forever for a
 * callback that will not come), error messages are discarded instead of being mixed into the output, and a
 * dead shell is restarted on the next call.
 */
class RootShell private constructor() {
    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null

    /**
     * Output lines of [command], or null when root is not available, the shell died or [timeoutMs] passed.
     * On a timeout the shell is killed (a hung mount must not block every later command) and a fresh one is
     * started next time.
     */
    @Synchronized
    fun run(command: String, timeoutMs: Long = 0L): List<String>? {
        repeat(2) {
            if (!ensureStarted()) {
                return null
            }

            var timedOut = false
            val proc = process
            val watchdog = if (timeoutMs > 0) {
                Timer(true).apply {
                    schedule(object : TimerTask() {
                        override fun run() {
                            timedOut = true
                            proc?.destroy()
                        }
                    }, timeoutMs)
                }
            } else {
                null
            }

            try {
                val marker = "__HFM_END_${System.nanoTime()}__"
                writer!!.write("$command\necho $marker\n")
                writer!!.flush()

                val lines = ArrayList<String>()
                while (true) {
                    val line = reader!!.readLine() ?: throw IllegalStateException("shell closed")
                    if (line == marker) {
                        return lines
                    }
                    lines.add(line)
                }
            } catch (e: Exception) {
                close()
                if (timedOut) {
                    return null
                }
            } finally {
                watchdog?.cancel()
            }
        }
        return null
    }

    private fun ensureStarted(): Boolean {
        if (process?.isAlive == true) {
            return true
        }

        return try {
            val proc = ProcessBuilder("su").start()
            // drain stderr so the pipe never fills up; errors are also silenced inside the shell below
            Thread {
                try {
                    proc.errorStream.use { stream -> while (stream.read() != -1) Unit }
                } catch (ignored: Exception) {
                }
            }.apply { isDaemon = true }.start()
            process = proc
            writer = OutputStreamWriter(proc.outputStream, Charsets.UTF_8)
            reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
            writer!!.write("exec 2>/dev/null\n")
            writer!!.flush()
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    @Synchronized
    fun close() {
        try {
            process?.destroy()
        } catch (ignored: Exception) {
        }
        process = null
        writer = null
        reader = null
    }

    companion object {
        /** For listing folders, so it is never stuck behind a slow size scan. */
        val listing by lazy { RootShell() }

        /** For folder sizes and child counts. */
        val background by lazy { RootShell() }

        /** Wraps [path] in single quotes for the shell. */
        fun quote(path: String) = "'" + path.replace("'", "'\\''") + "'"
    }
}
