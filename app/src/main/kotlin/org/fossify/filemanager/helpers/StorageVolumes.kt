package org.fossify.filemanager.helpers

import android.content.Context
import org.fossify.commons.extensions.getBasePath
import org.fossify.commons.extensions.hasExternalSDCard
import org.fossify.commons.extensions.hasOTGConnected
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.otgPath
import org.fossify.commons.extensions.sdCardPath
import org.fossify.filemanager.R
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.views.StorageButtonsBar

const val VOLUME_INTERNAL = "internal"
const val VOLUME_SD = "sd"
const val VOLUME_OTG = "otg"
const val VOLUME_ROOT = "root"

/** The storages that can be picked right now: internal, SD card, connected USB drive and (with root access) "/". */
fun Context.getStorageButtonItems(includeOtg: Boolean = true, includeRoot: Boolean = true): List<StorageButtonsBar.Item> {
    val items = ArrayList<StorageButtonsBar.Item>()
    items.add(StorageButtonsBar.Item(VOLUME_INTERNAL, internalStoragePath, getString(R.string.internal), R.drawable.ic_smartphone_vector))
    if (hasExternalSDCard()) {
        items.add(StorageButtonsBar.Item(VOLUME_SD, sdCardPath, getString(R.string.sd_card), R.drawable.ic_sd_card_vector))
    }

    if (includeOtg && hasOTGConnected()) {
        items.add(StorageButtonsBar.Item(VOLUME_OTG, otgPath, getString(R.string.usb), R.drawable.ic_usb_vector))
    }

    if (includeRoot && config.enableRootAccess) {
        items.add(StorageButtonsBar.Item(VOLUME_ROOT, "/", getString(R.string.root), R.drawable.ic_folder_vector))
    }
    return items
}

/** Which storage button a path belongs to. */
fun Context.getStorageKeyForPath(path: String): String {
    val basePath = path.getBasePath(this)
    return when {
        basePath == internalStoragePath -> VOLUME_INTERNAL
        sdCardPath.isNotEmpty() && basePath == sdCardPath -> VOLUME_SD
        otgPath.isNotEmpty() && basePath == otgPath -> VOLUME_OTG
        else -> VOLUME_ROOT
    }
}
