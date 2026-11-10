package org.fossify.filemanager.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Keeps app icons of .apk files as small PNGs in the cache folder.
 * An icon is built once on a background thread; the cache is wiped on every app start.
 */
object ApkIconCache {
    private const val DIR = "apk_icons"
    private const val ICON_SIZE = 192

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = HashSet<String>()

    private fun dir(context: Context) = File(context.cacheDir, DIR)

    private fun fileFor(context: Context, apk: File): File {
        val key = "${apk.absolutePath}|${apk.lastModified()}|${apk.length()}".hashCode().toUInt().toString(16)
        return File(dir(context), "$key.png")
    }

    /** Wipes icons from earlier runs so they are rebuilt fresh. */
    fun clear(context: Context) {
        executor.execute { dir(context).deleteRecursively() }
    }

    /** Returns the cached icon file, or null when it has not been built yet. */
    fun getCached(context: Context, path: String): File? {
        val file = fileFor(context, File(path))
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    /** Builds the icon in the background and calls [onReady] on the main thread when it is stored. */
    fun request(context: Context, path: String, onReady: () -> Unit) {
        val appContext = context.applicationContext
        synchronized(pending) {
            if (!pending.add(path)) {
                return
            }
        }

        executor.execute {
            var built = false
            try {
                val target = fileFor(appContext, File(path))
                if (!target.exists()) {
                    val pm = appContext.packageManager
                    val info = pm.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)
                    val appInfo = info?.applicationInfo
                    if (appInfo != null) {
                        appInfo.sourceDir = path
                        appInfo.publicSourceDir = path
                        val drawable = appInfo.loadIcon(pm)
                        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
                        drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
                        drawable.draw(Canvas(bitmap))
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                        built = true
                    }
                } else {
                    built = true
                }
            } catch (ignored: Throwable) {
            }

            synchronized(pending) { pending.remove(path) }
            if (built) {
                mainHandler.post(onReady)
            }
        }
    }
}
