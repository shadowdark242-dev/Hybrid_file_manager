package org.fossify.filemanager.activities

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityFolderUsageBinding
import java.io.File

/** Shows how internal storage is split between top-level folders, biggest first. */
class FolderUsageActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityFolderUsageBinding::inflate)

    private val root: File = Environment.getExternalStorageDirectory()
    private val folderSizes = HashMap<String, Long>()
    private var looseFilesSize = 0L
    private var isScanning = true
    private var totalBytes = 0L
    private var freeBytes = 0L

    private val palette = intArrayOf(
        0xFF6AA9FF.toInt(),
        0xFFF0B64A.toInt(),
        0xFF5FD38D.toInt(),
        0xFFB48CF2.toInt(),
        0xFFF29E6B.toInt(),
        0xFF5CC8D6.toInt()
    )
    private val otherColor = 0xFF8A93A6.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(folderUsageScrollview))
            setupMaterialScrollListener(folderUsageScrollview, folderUsageAppbar)
            folderUsageScanning.setTextColor(getProperTextColor())
        }

        val stat = StatFs(root.path)
        totalBytes = stat.totalBytes
        freeBytes = stat.availableBytes
        render()
        startScan()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.folderUsageAppbar, NavigationIcon.Arrow)
    }

    private fun startScan() {
        ensureBackgroundThread {
            val entries = root.listFiles() ?: emptyArray()
            var loose = 0L
            for (entry in entries) {
                if (isDestroyed) return@ensureBackgroundThread
                if (entry.isDirectory) {
                    val size = directorySize(entry)
                    runOnUiThread {
                        folderSizes[entry.name] = size
                        render()
                    }
                } else {
                    loose += entry.length()
                }
            }

            runOnUiThread {
                looseFilesSize = loose
                isScanning = false
                render()
            }
        }
    }

    private fun directorySize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach {
            if (it.isFile) {
                size += it.length()
            }
        }
        return size
    }

    private fun render() {
        val textColor = getProperTextColor()
        val sorted = folderSizes.entries.sortedByDescending { it.value }
        val scannedTotal = sorted.sumOf { it.value } + looseFilesSize
        val used = (totalBytes - freeBytes).coerceAtLeast(scannedTotal)

        val segments = ArrayList<Pair<Float, Int>>()
        val top = sorted.take(palette.size)
        top.forEachIndexed { index, entry ->
            segments.add(entry.value.toFloat() to palette[index])
        }

        val otherUsed = used - top.sumOf { it.value }
        if (otherUsed > 0) {
            segments.add(otherUsed.toFloat() to otherColor)
        }

        if (freeBytes > 0) {
            segments.add(freeBytes.toFloat() to textColor.adjustAlpha(0.15f))
        }

        binding.folderUsageDonut.setData(
            segments = segments,
            title = used.formatSize(),
            subtitle = getString(R.string.storage_of, totalBytes.formatSize()),
            textColor = textColor
        )

        binding.folderUsageScanning.beVisibleIf(isScanning)
        binding.folderUsageList.removeAllViews()

        val maxSize = (sorted.firstOrNull()?.value ?: 1L).coerceAtLeast(1L)
        sorted.take(MAX_ROWS).forEachIndexed { index, entry ->
            val color = palette.getOrNull(index) ?: otherColor
            addRow(entry.key, entry.value, maxSize, color, textColor) { openFolder(entry.key) }
        }

        if (looseFilesSize > 0) {
            addRow(getString(R.string.files_in_root), looseFilesSize, maxSize, otherColor, textColor, null)
        }
    }

    private fun addRow(
        name: String,
        size: Long,
        maxSize: Long,
        color: Int,
        textColor: Int,
        onClick: (() -> Unit)?
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            if (onClick != null) {
                setBackgroundResource(R.drawable.ripple_all_corners)
                setOnClickListener { onClick() }
            }
        }

        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = View(this).apply {
            setBackgroundColor(color)
        }
        line.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(10) })

        val nameView = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(textColor)
            maxLines = 1
        }
        line.addView(nameView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val sizeView = TextView(this).apply {
            text = size.formatSize()
            textSize = 14f
            setTextColor(textColor)
        }
        line.addView(sizeView)
        row.addView(line)

        val fraction = (size.toFloat() / maxSize.toFloat()).coerceIn(0.02f, 1f)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(textColor.adjustAlpha(0.12f))
        }
        bar.addView(View(this).apply { setBackgroundColor(color) }, LinearLayout.LayoutParams(0, dp(6), fraction))
        bar.addView(View(this), LinearLayout.LayoutParams(0, dp(6), 1f - fraction))
        row.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)).apply { topMargin = dp(8) })

        binding.folderUsageList.addView(row)
    }

    private fun openFolder(name: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.fromFile(File(root, name))
        }
        startActivity(intent)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_ROWS = 30
    }
}
