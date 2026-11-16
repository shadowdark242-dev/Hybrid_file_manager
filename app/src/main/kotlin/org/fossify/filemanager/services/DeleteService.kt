package org.fossify.filemanager.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.fossify.filemanager.R
import org.fossify.filemanager.helpers.FolderSizeCache
import java.io.File
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue

/**
 * Deletes files and folders in the background as a foreground service, so a big folder keeps being deleted
 * when the app is left, with progress shown in a notification and in the app.
 */
class DeleteService : Service() {
    /** One delete request. [onDone] runs on the main thread. */
    class Job(
        val paths: List<String>,
        val useRoot: Boolean,
        val onDone: (Result) -> Unit,
    )

    class Result(
        val deletedCount: Int,
        /** Top-level paths that still exist afterwards. */
        val failedPaths: List<String>,
        val cancelled: Boolean,
    )

    class Progress(
        val title: String,
        val done: Int,
        /** 0 while counting or when the total is unknown (root). */
        val total: Int,
        val counting: Boolean,
    )

    companion object {
        private const val CHANNEL_ID = "file_operations"
        private const val NOTIFICATION_ID = 7301
        private const val ACTION_CANCEL = "org.fossify.filemanager.DELETE_CANCEL"

        private val lock = Any()
        private val queue = LinkedBlockingQueue<Job>()
        private val mainHandler = Handler(Looper.getMainLooper())

        @Volatile
        private var workerRunning = false

        @Volatile
        private var cancelRequested = false

        @Volatile
        var currentProgress: Progress? = null
            private set

        /** Notified on the main thread with the current progress, or null when everything is finished. */
        val progressListeners = LinkedHashSet<(Progress?) -> Unit>()

        fun enqueue(context: Context, paths: List<String>, useRoot: Boolean, onDone: (Result) -> Unit) {
            if (paths.isEmpty()) {
                onDone(Result(0, emptyList(), false))
                return
            }

            synchronized(lock) {
                queue.add(Job(paths, useRoot, onDone))
            }

            val intent = Intent(context, DeleteService::class.java)
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: Exception) {
                // the service can't be started (should not happen from a visible activity), delete in a plain thread
                Thread { processQueue(context.applicationContext, null) }.start()
            }
        }

        fun cancel() {
            cancelRequested = true
        }

        private fun publish(progress: Progress?) {
            currentProgress = progress
            mainHandler.post {
                progressListeners.toList().forEach { it(progress) }
            }
        }

        private fun processQueue(context: Context, service: DeleteService?) {
            synchronized(lock) {
                if (workerRunning) {
                    return
                }
                workerRunning = true
            }

            while (true) {
                val job = synchronized(lock) {
                    queue.poll().also {
                        if (it == null) {
                            workerRunning = false
                        }
                    }
                } ?: break

                cancelRequested = false
                val result = try {
                    Worker(context, service).run(job)
                } catch (e: Exception) {
                    Result(0, job.paths.filter { File(it).exists() }, false)
                }

                FolderSizeCache.invalidate(job.paths)
                mainHandler.post { job.onDone(result) }
            }

            publish(null)
            mainHandler.post { service?.stopIfIdle() }
        }
    }

    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_CANCEL) {
            cancel()
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(this, currentProgress), type)
        } catch (e: Exception) {
        }

        if (intent?.action != ACTION_CANCEL) {
            Thread { processQueue(applicationContext, this) }.start()
        } else if (!workerRunning && queue.isEmpty()) {
            stopIfIdle()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ limits dataSync services; stop deleting rather than letting the system kill the app
        cancel()
        stopIfIdle()
    }

    private fun stopIfIdle() {
        synchronized(lock) {
            if (workerRunning || queue.isNotEmpty()) {
                return
            }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    private class Worker(val context: Context, val service: DeleteService?) {
        private var lastUiUpdate = 0L
        private var lastNotificationUpdate = 0L
        private var title = ""
        private var done = 0
        private var total = 0

        fun run(job: Job): Result {
            title = if (job.paths.size == 1) {
                context.getString(R.string.deleting_title_single, File(job.paths.first()).name)
            } else {
                context.getString(R.string.deleting_title_multiple, job.paths.size)
            }

            return if (job.useRoot) runRoot(job) else runRegular(job)
        }

        private fun runRegular(job: Job): Result {
            report(counting = true, force = true)
            for (path in job.paths) {
                if (cancelRequested) break
                total += count(File(path))
            }

            report(counting = false, force = true)
            for (path in job.paths) {
                if (cancelRequested) break
                deleteRecursively(File(path))
                removeFromMediaStore(path)
            }

            val failed = job.paths.filter { File(it).exists() || isLink(File(it)) }
            return Result(done, failed, cancelRequested)
        }

        private fun count(file: File): Int {
            if (cancelRequested) return 0
            var result = 1
            if (!isLink(file) && file.isDirectory) {
                file.listFiles()?.forEach { result += count(it) }
                report(counting = true)
            }
            return result
        }

        private fun deleteRecursively(file: File) {
            if (cancelRequested) return
            if (!isLink(file) && file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursively(it) }
            }

            if (cancelRequested) return
            if (file.delete()) {
                done++
                report(counting = false)
            }
        }

        private fun runRoot(job: Job): Result {
            report(counting = false, force = true)
            val failed = ArrayList<String>()
            for (path in job.paths) {
                if (cancelRequested) {
                    failed.add(path)
                    continue
                }

                val target = "/" + path.trim('/')
                if (target == "/") continue
                val quoted = "'" + target.replace("'", "'\\''") + "'"
                try {
                    val process = ProcessBuilder("su", "-c", "rm -rf $quoted").redirectErrorStream(true).start()
                    process.inputStream.use { stream -> while (stream.read() != -1) Unit }
                    if (process.waitFor() == 0) {
                        done++
                        report(counting = false, force = true)
                    } else {
                        failed.add(path)
                    }
                } catch (e: Exception) {
                    failed.add(path)
                }
            }

            return Result(done, failed, cancelRequested)
        }

        private fun isLink(file: File) = try {
            Files.isSymbolicLink(file.toPath())
        } catch (e: Exception) {
            false
        }

        /** Drops the deleted path and everything below it from the media database, so galleries don't show ghosts. */
        private fun removeFromMediaStore(path: String) {
            try {
                val escaped = path.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                val data = MediaStore.MediaColumns.DATA
                context.contentResolver.delete(
                    MediaStore.Files.getContentUri("external"),
                    "$data = ? OR $data LIKE ? ESCAPE '\\'",
                    arrayOf(path, "$escaped/%")
                )
            } catch (e: Exception) {
                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            }
        }

        private fun report(counting: Boolean, force: Boolean = false) {
            val now = SystemClock.uptimeMillis()
            if (force || now - lastUiUpdate >= 150) {
                lastUiUpdate = now
                publish(Progress(title, done, if (counting) 0 else total, counting))
            }

            if (service != null && (force || now - lastNotificationUpdate >= 700)) {
                lastNotificationUpdate = now
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                try {
                    manager.notify(NOTIFICATION_ID, buildNotification(context, currentProgress))
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.file_operations), NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }
}

private fun buildNotification(context: Context, progress: DeleteService.Progress?): Notification {
    val title = progress?.title ?: context.getString(R.string.deleting_in_progress)
    val builder = NotificationCompat.Builder(context, "file_operations")
        .setSmallIcon(R.drawable.ic_delete_vector)
        .setContentTitle(title)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)

    when {
        progress == null || progress.counting -> {
            builder.setContentText(context.getString(R.string.deleting_counting))
            builder.setProgress(0, 0, true)
        }

        progress.total > 0 -> {
            builder.setContentText(context.getString(R.string.deleting_progress, progress.done, progress.total))
            builder.setProgress(progress.total, progress.done.coerceAtMost(progress.total), false)
        }

        else -> {
            builder.setContentText(context.getString(R.string.deleting_in_progress))
            builder.setProgress(0, 0, true)
        }
    }

    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        builder.setContentIntent(PendingIntent.getActivity(context, 0, launch, flags))
    }

    val cancelIntent = Intent(context, DeleteService::class.java).setAction("org.fossify.filemanager.DELETE_CANCEL")
    val cancelPending = PendingIntent.getService(context, 1, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    builder.addAction(0, context.getString(R.string.cancel), cancelPending)
    return builder.build()
}
