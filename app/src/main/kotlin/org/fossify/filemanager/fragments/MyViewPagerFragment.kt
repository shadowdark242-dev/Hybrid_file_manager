package org.fossify.filemanager.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyFloatingActionButton
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.databinding.CleanupFragmentBinding
import org.fossify.filemanager.databinding.ItemsFragmentBinding
import org.fossify.filemanager.databinding.RecentsFragmentBinding
import org.fossify.filemanager.databinding.StorageFragmentBinding
import org.fossify.filemanager.extensions.isPathOnRoot
import org.fossify.filemanager.extensions.tryOpenPathIntent
import org.fossify.filemanager.dialogs.DeleteProgressDialog
import org.fossify.filemanager.helpers.FolderSizeCache
import org.fossify.filemanager.services.DeleteService

abstract class MyViewPagerFragment<BINDING : MyViewPagerFragment.InnerBinding>(context: Context, attributeSet: AttributeSet) :
    RelativeLayout(context, attributeSet) {
    protected var activity: SimpleActivity? = null
    protected var currentViewType = VIEW_TYPE_LIST

    var currentPath = ""
    var isGetContentIntent = false
    var isGetRingtonePicker = false
    var isPickMultipleIntent = false
    var wantedMimeTypes = listOf("")
    protected var isCreateDocumentIntent = false
    protected lateinit var innerBinding: BINDING

    protected fun clickedPath(path: String) {
        if (isGetContentIntent || isCreateDocumentIntent) {
            (activity as MainActivity).pickedPath(path)
        } else if (isGetRingtonePicker) {
            if (path.isAudioFast()) {
                (activity as MainActivity).pickedRingtone(path)
            } else {
                activity?.toast(R.string.select_audio_file)
            }
        } else {
            activity?.tryOpenPathIntent(path, false)
        }
    }

    fun updateIsCreateDocumentIntent(isCreateDocumentIntent: Boolean) {
        val iconId = if (isCreateDocumentIntent) {
            R.drawable.ic_check_vector
        } else {
            R.drawable.ic_plus_vector
        }

        this.isCreateDocumentIntent = isCreateDocumentIntent
        val fabIcon = context.resources.getColoredDrawableWithColor(iconId, context.getProperPrimaryColor().getContrastColor())
        innerBinding.itemsFab?.setImageDrawable(fabIcon)
    }

    fun handleFileDeleting(files: ArrayList<FileDirItem>, hasFolder: Boolean) {
        val firstPath = files.firstOrNull()?.path
        val activity = activity
        if (firstPath == null || firstPath.isEmpty() || context == null || activity == null) {
            return
        }

        // OTG and the SAF-only Android/data folders can only be deleted through the system document APIs
        val (systemOnly, direct) = files.partition { activity.isPathOnOTG(it.path) || activity.isRestrictedSAFOnlyRoot(it.path) }
        if (systemOnly.isNotEmpty()) {
            deleteWithSystemApi(ArrayList(systemOnly), hasFolder)
        }

        if (direct.isEmpty()) {
            return
        }

        val useRoot = context!!.isPathOnRoot(firstPath)
        activity.handleNotificationPermission { }
        val progressDialog = DeleteProgressDialog(activity)
        DeleteService.enqueue(activity, direct.map { it.path }, useRoot) { result ->
            progressDialog.dismiss()
            val ctx = this.activity ?: return@enqueue
            when {
                result.cancelled -> ctx.toast(R.string.deleting_cancelled)
                result.failedPaths.isEmpty() -> ctx.toast(ctx.getString(R.string.deleting_done, result.deletedCount))
                useRoot -> ctx.toast(R.string.deleting_failed)
                else -> {
                    // let the system APIs try what plain file access could not delete (e.g. SD card on older Android)
                    val leftovers = direct.filter { it.path in result.failedPaths }
                    deleteWithSystemApi(ArrayList(leftovers), hasFolder)
                }
            }

            refreshFragment()
            (ctx as? MainActivity)?.refreshOtherPane(this)
        }
    }

    private fun deleteWithSystemApi(files: ArrayList<FileDirItem>, hasFolder: Boolean) {
        FolderSizeCache.invalidate(files.map { it.path })
        (activity as SimpleActivity).deleteFiles(files, hasFolder) {
            FolderSizeCache.invalidate(files.map { it.path })
            activity?.runOnUiThread {
                if (!it) {
                    activity?.toast(R.string.unknown_error_occurred)
                }
                refreshFragment()
                (activity as? MainActivity)?.refreshOtherPane(this)
            }
        }
    }

    protected fun isProperMimeType(wantedMimeType: String, path: String, isDirectory: Boolean): Boolean {
        return if (wantedMimeType.isEmpty() || wantedMimeType == "*/*" || isDirectory) {
            true
        } else {
            val fileMimeType = path.getMimeType()
            if (wantedMimeType.endsWith("/*")) {
                fileMimeType.substringBefore("/").equals(wantedMimeType.substringBefore("/"), true)
            } else {
                fileMimeType.equals(wantedMimeType, true)
            }
        }
    }

    abstract fun setupFragment(activity: SimpleActivity)

    abstract fun onResume(textColor: Int)

    abstract fun refreshFragment()

    abstract fun searchQueryChanged(text: String)

    interface InnerBinding {
        val itemsFab: MyFloatingActionButton?
    }

    class ItemsInnerBinding(val binding: ItemsFragmentBinding) : InnerBinding {
        override val itemsFab: MyFloatingActionButton = binding.itemsFab
    }

    class RecentsInnerBinding(val binding: RecentsFragmentBinding) : InnerBinding {
        override val itemsFab: MyFloatingActionButton? = null
    }

    class StorageInnerBinding(val binding: StorageFragmentBinding) : InnerBinding {
        override val itemsFab: MyFloatingActionButton? = null
    }

    class CleanupInnerBinding(val binding: CleanupFragmentBinding) : InnerBinding {
        override val itemsFab: MyFloatingActionButton? = null
    }
}
