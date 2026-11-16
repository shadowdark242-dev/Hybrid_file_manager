package org.fossify.filemanager.dialogs

import android.app.Activity
import android.view.Gravity
import android.content.res.ColorStateList
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.filemanager.R
import org.fossify.filemanager.services.DeleteService

/** Shows the progress of [DeleteService]; it can be hidden (deletion goes on) or used to cancel. */
class DeleteProgressDialog(private val activity: Activity) {
    private var dialog: AlertDialog? = null
    private val title: TextView
    private val details: TextView
    private val bar: ProgressBar
    private val listener: (DeleteService.Progress?) -> Unit = { update(it) }

    init {
        val density = activity.resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val textColor = activity.getProperTextColor()

        title = TextView(activity).apply {
            textSize = 16f
            setTextColor(textColor)
        }
        bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            val color = ColorStateList.valueOf(activity.getProperPrimaryColor())
            progressTintList = color
            indeterminateTintList = color
        }
        details = TextView(activity).apply {
            textSize = 14f
            setTextColor(textColor)
            alpha = 0.8f
            gravity = Gravity.END
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad / 2)
            addView(title)
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (16 * density).toInt()
            })
            addView(details, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * density).toInt()
            })
        }

        update(DeleteService.currentProgress)
        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.hide) { _, _ -> }
            .setNegativeButton(R.string.cancel) { _, _ -> DeleteService.cancel() }
            .apply {
                activity.setupDialogStuff(root, this, cancelOnTouchOutside = false) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.setOnDismissListener { DeleteService.progressListeners.remove(listener) }
                }
            }

        DeleteService.progressListeners.add(listener)
    }

    private fun update(progress: DeleteService.Progress?) {
        if (progress == null) {
            // the job list just finished; the result toast is shown by the caller
            if (dialog != null) {
                dismiss()
            }
            title.text = activity.getString(R.string.deleting_in_progress)
            return
        }

        title.text = progress.title
        when {
            progress.counting -> {
                bar.isIndeterminate = true
                details.text = activity.getString(R.string.deleting_counting)
            }

            progress.total > 0 -> {
                if (bar.isIndeterminate) {
                    bar.isIndeterminate = false
                }
                bar.max = progress.total
                bar.progress = progress.done.coerceAtMost(progress.total)
                details.text = activity.getString(R.string.deleting_progress, progress.done, progress.total)
            }

            else -> {
                bar.isIndeterminate = true
                details.text = activity.getString(R.string.deleting_in_progress)
            }
        }
    }

    fun dismiss() {
        DeleteService.progressListeners.remove(listener)
        try {
            dialog?.dismiss()
        } catch (ignored: Exception) {
        }
        dialog = null
    }
}
