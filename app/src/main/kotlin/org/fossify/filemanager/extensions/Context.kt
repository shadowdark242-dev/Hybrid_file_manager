package org.fossify.filemanager.extensions

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.MediaStore
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isPathOnSD
import org.fossify.filemanager.helpers.Config
import org.fossify.filemanager.helpers.PRIMARY_VOLUME_NAME
import java.util.Locale

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.isPathOnRoot(path: String) = !(path.startsWith(config.internalStoragePath) || isPathOnOTG(path) || (isPathOnSD(path)))

fun Context.getAllVolumeNames(): List<String> {
    val volumeNames = mutableListOf(PRIMARY_VOLUME_NAME)
    val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        getExternalFilesDirs(null)
            .filterNotNull()
            .mapNotNull { storageManager.getStorageVolume(it) }
            .filterNot { it.isPrimary }
            .mapNotNull { it.uuid?.lowercase(Locale.US) }
            .forEach {
                volumeNames.add(it)
            }
    } else {
        // Android 6: the first dir is the internal storage, the others sit at /storage/<UUID>/Android/...
        getExternalFilesDirs(null)
            .drop(1)
            .filterNotNull()
            .mapNotNull { getLegacyVolumeUuid(it.absolutePath) }
            .forEach { volumeNames.add(it) }
    }
    return volumeNames
}

/** Volume UUID (lowercase) from a path like /storage/1234-ABCD/..., used on Android 6. */
fun getLegacyVolumeUuid(path: String): String? {
    val parts = path.split('/').filter { it.isNotEmpty() }
    return if (parts.size >= 2 && parts[0] == "storage" && parts[1] != "emulated" && parts[1] != "self") {
        parts[1].lowercase(Locale.US)
    } else {
        null
    }
}

/** MediaStore files uri of a volume. Android 9 and older have a single "external" volume for everything. */
fun Context.volumeFilesUri(volumeName: String): Uri =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Files.getContentUri(volumeName)
    } else {
        MediaStore.Files.getContentUri("external")
    }

/** On Android 9 and older, a selection that keeps an "external" query to one volume; null otherwise. */
fun Context.volumeSelection(volumeName: String): Pair<String, Array<String>>? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return null
    }

    val root = if (volumeName == PRIMARY_VOLUME_NAME) internalStoragePath else "/storage/${volumeName.uppercase(Locale.US)}"
    return "${MediaStore.Files.FileColumns.DATA} LIKE ?" to arrayOf("${root.trimEnd('/')}/%")
}
