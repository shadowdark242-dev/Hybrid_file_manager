package org.fossify.filemanager.activities

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityCleanupBinding
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date

/** Finds duplicate files and empty folders in internal storage and deletes the ones the user selects. */
class CleanupActivity : SimpleActivity() {
    private class DuplicateFile(val file: File, var selected: Boolean)
    private class DuplicateGroup(val size: Long, val files: List<DuplicateFile>)
    private class EmptyFolder(val dir: File, var selected: Boolean)

    private val binding by viewBinding(ActivityCleanupBinding::inflate)
    private val root: File = Environment.getExternalStorageDirectory()

    private var groups = ArrayList<DuplicateGroup>()
    private var emptyFolders = ArrayList<EmptyFolder>()
    private var showingDuplicates = true
    private var isScanning = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(cleanupScrollview))
            setupMaterialScrollListener(cleanupScrollview, cleanupAppbar)
            cleanupStatus.setTextColor(getProperTextColor())
            cleanupTabDuplicates.setOnClickListener { selectTab(true) }
            cleanupTabEmpty.setOnClickListener { selectTab(false) }
            cleanupDeleteButton.setOnClickListener { confirmDelete() }
        }

        selectTab(true)
        startScan()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.cleanupAppbar, NavigationIcon.Arrow)
    }

    private fun selectTab(duplicates: Boolean) {
        showingDuplicates = duplicates
        val primary = getProperPrimaryColor()
        val text = getProperTextColor()
        binding.apply {
            cleanupTabDuplicates.setBackgroundColor(if (duplicates) primary else Color.TRANSPARENT)
            cleanupTabDuplicates.setTextColor(if (duplicates) Color.BLACK else text)
            cleanupTabEmpty.setBackgroundColor(if (duplicates) Color.TRANSPARENT else primary)
            cleanupTabEmpty.setTextColor(if (duplicates) text else Color.BLACK)
            cleanupDuplicatesList.beVisibleIf(duplicates)
            cleanupEmptyList.beVisibleIf(!duplicates)
        }
        updateStatusAndButton()
    }

    // region scanning

    private fun startScan() {
        ensureBackgroundThread {
            val bySize = HashMap<Long, MutableList<File>>()
            collectFiles(bySize)
            val foundGroups = findDuplicates(bySize)

            val foundEmpty = ArrayList<File>()
            collectEmptyFolders(root, foundEmpty)

            if (isDestroyed) {
                return@ensureBackgroundThread
            }

            runOnUiThread {
                groups = foundGroups
                emptyFolders = ArrayList(foundEmpty.sortedBy { it.path }.map { EmptyFolder(it, true) })
                isScanning = false
                renderDuplicates()
                renderEmptyFolders()
                updateStatusAndButton()
            }
        }
    }

    private fun collectFiles(bySize: HashMap<Long, MutableList<File>>) {
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            if (isDestroyed) {
                return
            }

            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    if (dir == root && child.name == ANDROID_DIR) {
                        continue
                    }
                    stack.add(child)
                } else if (child.isFile) {
                    val length = child.length()
                    if (length > 0) {
                        bySize.getOrPut(length) { ArrayList() }.add(child)
                    }
                }
            }
        }
    }

    private fun findDuplicates(bySize: HashMap<Long, MutableList<File>>): ArrayList<DuplicateGroup> {
        val result = ArrayList<DuplicateGroup>()
        for ((size, files) in bySize) {
            if (files.size < 2) {
                continue
            }

            val byQuickHash = HashMap<String, MutableList<File>>()
            for (file in files) {
                if (isDestroyed) {
                    return result
                }
                hash(file, QUICK_HASH_BYTES)?.let { byQuickHash.getOrPut(it) { ArrayList() }.add(file) }
            }

            for (candidates in byQuickHash.values) {
                if (candidates.size < 2) {
                    continue
                }

                val identical: Collection<List<File>> = if (size <= QUICK_HASH_BYTES) {
                    listOf(candidates)
                } else {
                    val byFullHash = HashMap<String, MutableList<File>>()
                    for (file in candidates) {
                        if (isDestroyed) {
                            return result
                        }
                        hash(file, Long.MAX_VALUE)?.let { byFullHash.getOrPut(it) { ArrayList() }.add(file) }
                    }
                    byFullHash.values
                }

                for (sameFiles in identical) {
                    if (sameFiles.size < 2) {
                        continue
                    }

                    val sorted = sameFiles.sortedBy { it.lastModified() }
                    result.add(DuplicateGroup(size, sorted.mapIndexed { index, file -> DuplicateFile(file, index > 0) }))
                }
            }
        }

        result.sortByDescending { it.size * (it.files.size - 1) }
        return ArrayList(result.take(MAX_GROUPS))
    }

    private fun hash(file: File, limit: Long): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(QUICK_HASH_BYTES.toInt())
            var remaining = limit
            FileInputStream(file).use { input ->
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) {
                        break
                    }
                    digest.update(buffer, 0, read)
                    remaining -= read
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns true when [dir] holds no files at all (only empty folders, recursively).
     * Topmost empty folders are added to [out]; their empty children are covered by them.
     */
    private fun collectEmptyFolders(dir: File, out: MutableList<File>): Boolean {
        if (isDestroyed) {
            return false
        }

        val children = dir.listFiles() ?: return false
        var allEmpty = true
        val emptyChildren = ArrayList<File>()
        for (child in children) {
            if (child.isDirectory) {
                if (dir == root && child.name == ANDROID_DIR) {
                    allEmpty = false
                    continue
                }

                if (collectEmptyFolders(child, out)) {
                    emptyChildren.add(child)
                } else {
                    allEmpty = false
                }
            } else {
                allEmpty = false
            }
        }

        if (!allEmpty) {
            out.addAll(emptyChildren)
        }
        return allEmpty
    }

    // endregion

    // region rendering

    private fun renderDuplicates() {
        val list = binding.cleanupDuplicatesList
        list.removeAllViews()
        val textColor = getProperTextColor()
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

        for (group in groups) {
            val header = TextView(this).apply {
                text = getString(
                    R.string.cleanup_group_header,
                    group.files.first().file.name,
                    group.size.formatSize(),
                    group.files.size
                )
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                setPadding(dp(16), dp(16), dp(16), dp(4))
            }
            list.addView(header)

            for (item in group.files) {
                val box = CheckBox(this).apply {
                    val relative = item.file.path.removePrefix(root.path).trimStart('/')
                    val keepNote = if (item.selected) "" else " · " + getString(R.string.cleanup_keep)
                    text = relative + "\n" + dateFormat.format(Date(item.file.lastModified())) + keepNote
                    textSize = 13f
                    setTextColor(textColor)
                    buttonTintList = ColorStateList.valueOf(getProperPrimaryColor())
                    isChecked = item.selected
                    setPadding(dp(8), dp(4), dp(16), dp(4))
                }

                box.setOnCheckedChangeListener { _, checked ->
                    if (!checked) {
                        item.selected = false
                    } else if (group.files.count { !it.selected } <= 1 && !item.selected) {
                        // never allow deleting every copy
                        toast(R.string.cleanup_keep_one)
                        box.isChecked = false
                        return@setOnCheckedChangeListener
                    } else {
                        item.selected = true
                    }
                    updateStatusAndButton()
                }
                list.addView(box)
            }
        }
    }

    private fun renderEmptyFolders() {
        val list = binding.cleanupEmptyList
        list.removeAllViews()
        val textColor = getProperTextColor()
        for (folder in emptyFolders) {
            val box = CheckBox(this).apply {
                text = folder.dir.path.removePrefix(root.path).trimStart('/')
                textSize = 14f
                setTextColor(textColor)
                buttonTintList = ColorStateList.valueOf(getProperPrimaryColor())
                isChecked = folder.selected
                setPadding(dp(8), dp(8), dp(16), dp(8))
                setOnCheckedChangeListener { _, checked ->
                    folder.selected = checked
                    updateStatusAndButton()
                }
            }
            list.addView(box)
        }
    }

    private fun updateStatusAndButton() {
        val primary = getProperPrimaryColor()
        binding.cleanupStatus.apply {
            if (isScanning) {
                text = getString(R.string.storage_scanning)
                visibility = View.VISIBLE
            } else {
                val count = if (showingDuplicates) groups.size else emptyFolders.size
                text = if (count == 0) {
                    getString(R.string.cleanup_nothing_found)
                } else if (showingDuplicates) {
                    getString(R.string.cleanup_duplicates_found, groups.size, wastedBytes().formatSize())
                } else {
                    getString(R.string.cleanup_empty_found, emptyFolders.size)
                }
                visibility = View.VISIBLE
            }
        }

        val selectedCount: Int
        val label: String
        if (showingDuplicates) {
            val selected = groups.flatMap { it.files }.filter { it.selected }
            selectedCount = selected.size
            label = getString(R.string.cleanup_delete_selected_size, selected.size, selected.sumOf { it.file.length() }.formatSize())
        } else {
            selectedCount = emptyFolders.count { it.selected }
            label = getString(R.string.cleanup_delete_selected, selectedCount)
        }

        binding.cleanupDeleteButton.apply {
            text = label
            setBackgroundColor(primary)
            setTextColor(Color.BLACK)
            alpha = if (selectedCount > 0 && !isScanning) 1f else 0.4f
            isEnabled = selectedCount > 0 && !isScanning
        }
    }

    private fun wastedBytes() = groups.sumOf { group -> group.size * (group.files.size - 1) }

    // endregion

    // region deleting

    private fun confirmDelete() {
        val message = if (showingDuplicates) {
            val selected = groups.flatMap { it.files }.count { it.selected }
            getString(R.string.cleanup_confirm_files, selected)
        } else {
            getString(R.string.cleanup_confirm_folders, emptyFolders.count { it.selected })
        }

        ConfirmationDialog(this, message) {
            if (showingDuplicates) deleteSelectedDuplicates() else deleteSelectedFolders()
        }
    }

    private fun deleteSelectedDuplicates() {
        val toDelete = groups.flatMap { it.files }.filter { it.selected }
        ensureBackgroundThread {
            val deleted = ArrayList<String>()
            toDelete.forEach {
                if (it.file.delete()) {
                    deleted.add(it.file.path)
                }
            }

            runOnUiThread {
                val deletedSet = deleted.toHashSet()
                groups = ArrayList(
                    groups.mapNotNull { group ->
                        val left = group.files.filter { it.file.path !in deletedSet }
                        if (left.size >= 2) DuplicateGroup(group.size, left) else null
                    }
                )
                renderDuplicates()
                updateStatusAndButton()
                toast(getString(R.string.cleanup_deleted, deleted.size))
            }
            rescanPaths(ArrayList(deleted))
        }
    }

    private fun deleteSelectedFolders() {
        val toDelete = emptyFolders.filter { it.selected }
        ensureBackgroundThread {
            val deleted = ArrayList<String>()
            toDelete.forEach { folder ->
                // delete deepest first; File.delete() refuses non-empty directories, so files are never removed
                folder.dir.walkBottomUp().filter { it.isDirectory }.forEach { it.delete() }
                if (!folder.dir.exists()) {
                    deleted.add(folder.dir.path)
                }
            }

            runOnUiThread {
                val deletedSet = deleted.toHashSet()
                emptyFolders = ArrayList(emptyFolders.filter { it.dir.path !in deletedSet })
                renderEmptyFolders()
                updateStatusAndButton()
                toast(getString(R.string.cleanup_deleted, deleted.size))
            }
            rescanPaths(ArrayList(deleted))
        }
    }

    // endregion

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ANDROID_DIR = "Android"
        private const val QUICK_HASH_BYTES = 64L * 1024
        private const val MAX_GROUPS = 200
    }
}
