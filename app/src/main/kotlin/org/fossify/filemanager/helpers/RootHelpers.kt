package org.fossify.filemanager.helpers

import android.app.Activity
import com.stericson.RootShell.execution.Command
import com.stericson.RootTools.RootTools
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.models.FileDirItem
import org.fossify.filemanager.R
import org.fossify.filemanager.models.ListItem

class RootHelpers(val activity: Activity) {
    companion object {
        private val PSEUDO_FILE_SYSTEMS = listOf("/proc", "/sys", "/dev", "/acct")
    }

    fun askRootIfNeeded(callback: (success: Boolean) -> Unit) {
        val cmd = "ls -lA"
        val command = object : Command(0, cmd) {
            override fun commandOutput(id: Int, line: String) {
                callback(true)
                super.commandOutput(id, line)
            }
        }

        try {
            RootTools.getShell(true).add(command)
        } catch (exception: Exception) {
            activity.showErrorToast(exception)
            callback(false)
        }
    }

    /**
     * Lists [path] through the root shell. Blocking, call it from a background thread.
     * Sizes of files are always filled in; folder sizes and child counts are loaded separately.
     */
    fun listFolder(path: String, showHidden: Boolean): ArrayList<ListItem> {
        val folder = path.trimEnd('/').ifEmpty { "/" }
        val q = RootShell.quote(folder)
        val format = "%f|%s|%Y|%n"
        val cmd = "cd $q && { find . -mindepth 1 -maxdepth 1 -exec stat -L -c 'A|$format' {} + ; " +
            "find . -mindepth 1 -maxdepth 1 -type l -exec stat -c 'B|$format' {} + ; }"
        val lines = RootShell.listing.run(cmd, 20_000L) ?: return ArrayList()

        val resolved = LinkedHashMap<String, ListItem>()
        val links = HashMap<String, ListItem>()
        for (line in lines) {
            val parts = line.split('|', limit = 5)
            if (parts.size < 5) {
                continue
            }

            val name = parts[4].removePrefix("./")
            if (name.isEmpty() || name.contains('/') || (!showHidden && name.startsWith('.'))) {
                continue
            }

            val mode = parts[1].toIntOrNull(16) ?: continue
            val isDirectory = mode and 0xF000 == 0x4000
            val size = if (isDirectory) 0L else parts[2].toLongOrNull() ?: 0L
            val modified = (parts[3].toLongOrNull() ?: 0L) * 1000L
            val fullPath = if (folder == "/") "/$name" else "$folder/$name"
            val item = ListItem(fullPath, name, isDirectory, 0, size, modified, false, false)
            if (parts[0] == "A") resolved[name] = item else links[name] = item
        }

        // broken links can't be resolved, show them as plain entries
        links.forEach { (name, item) -> resolved.putIfAbsent(name, item) }
        return ArrayList(resolved.values)
    }

    /** Number of entries in every sub-folder of [path], as folder path to count. Blocking. */
    fun getChildCounts(path: String, showHidden: Boolean): Map<String, Int> {
        val folder = path.trimEnd('/').ifEmpty { "/" }
        val ls = if (showHidden) "ls -A" else "ls"
        val cmd = "cd ${RootShell.quote(folder)} && find . -mindepth 1 -maxdepth 1 -exec sh -c " +
            "'for d; do [ -d \"\$d\" ] && echo \"\$($ls \"\$d\" | wc -l)|\$d\"; done' sh {} +"
        val lines = RootShell.background.run(cmd, 30_000L) ?: return emptyMap()
        val counts = HashMap<String, Int>()
        for (line in lines) {
            val count = line.substringBefore('|').trim().toIntOrNull() ?: continue
            val name = line.substringAfter('|').removePrefix("./")
            counts[if (folder == "/") "/$name" else "$folder/$name"] = count
        }
        return counts
    }

    /** Size of the folder at [path] in bytes, without crossing into other file systems. Blocking. */
    fun getFolderSize(path: String): Long {
        if (PSEUDO_FILE_SYSTEMS.any { path == it || path.startsWith("$it/") }) {
            return 0L
        }

        val line = RootShell.background.run("du -skx ${RootShell.quote(path)}", 180_000L)?.lastOrNull() ?: return 0L
        val kb = line.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: return 0L
        return kb * 1024L
    }

    private fun runCommand(command: Command) {
        try {
            RootTools.getShell(true).add(command)
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    fun createFileFolder(path: String, isFile: Boolean, callback: (success: Boolean) -> Unit) {
        if (!RootTools.isRootAvailable()) {
            activity.toast(R.string.rooted_device_only)
            return
        }

        tryMountAsRW(path) {
            val mountPoint = it
            val targetPath = path.trim('/')
            val mainCommand = if (isFile) "touch" else "mkdir"
            val cmd = "$mainCommand \"/$targetPath\""
            val command = object : Command(0, cmd) {
                override fun commandCompleted(id: Int, exitcode: Int) {
                    callback(exitcode == 0)
                    mountAsRO(mountPoint)
                    super.commandCompleted(id, exitcode)
                }
            }

            runCommand(command)
        }
    }

    private fun mountAsRO(mountPoint: String?) {
        if (mountPoint != null) {
            val cmd = "umount -r \"$mountPoint\""
            val command = object : Command(0, cmd) {}
            runCommand(command)
        }
    }

    // inspired by Amaze File Manager
    private fun tryMountAsRW(path: String, callback: (mountPoint: String?) -> Unit) {
        val mountPoints = ArrayList<String>()
        val cmd = "mount"
        val command = object : Command(0, cmd) {
            override fun commandOutput(id: Int, line: String) {
                mountPoints.add(line)
                super.commandOutput(id, line)
            }

            override fun commandCompleted(id: Int, exitcode: Int) {
                var mountPoint = ""
                var types: String? = null
                for (line in mountPoints) {
                    val words = line.split(" ").filter { it.isNotEmpty() }

                    if (path.contains(words[2])) {
                        if (words[2].length > mountPoint.length) {
                            mountPoint = words[2]
                            types = words[5]
                        }
                    }
                }

                if (mountPoint.isNotEmpty() && types != null) {
                    if (types.contains("rw")) {
                        callback(null)
                    } else if (types.contains("ro")) {
                        val mountCommand = "mount -o rw,remount $mountPoint"
                        mountAsRW(mountCommand) {
                            callback(it)
                        }
                    }
                }

                super.commandCompleted(id, exitcode)
            }
        }

        runCommand(command)
    }

    private fun mountAsRW(cmd: String, callback: (mountPoint: String) -> Unit) {
        val command = object : Command(0, cmd) {
            override fun commandOutput(id: Int, line: String) {
                callback(line)
                super.commandOutput(id, line)
            }
        }

        runCommand(command)
    }

    fun copyMoveFiles(fileDirItems: ArrayList<FileDirItem>, destination: String, isCopyOperation: Boolean, successes: Int = 0, callback: (Int) -> Unit) {
        if (!RootTools.isRootAvailable()) {
            activity.toast(R.string.rooted_device_only)
            return
        }

        val fileDirItem = fileDirItems.first()
        val mainCommand = if (isCopyOperation) {
            if (fileDirItem.isDirectory) "cp -R" else "cp"
        } else {
            "mv"
        }

        val cmd = "$mainCommand \"${fileDirItem.path}\" \"$destination\""
        // no timeout: copying a big folder can take much longer than RootTools' default 20 seconds
        val command = object : Command(0, 0, cmd) {
            override fun commandCompleted(id: Int, exitcode: Int) {
                next(exitcode == 0)
                super.commandCompleted(id, exitcode)
            }

            override fun commandTerminated(id: Int, reason: String?) {
                next(false)
                super.commandTerminated(id, reason)
            }

            private fun next(success: Boolean) {
                val newSuccesses = successes + (if (success) 1 else 0)
                if (fileDirItems.size == 1) {
                    callback(newSuccesses)
                } else {
                    fileDirItems.removeAt(0)
                    copyMoveFiles(fileDirItems, destination, isCopyOperation, newSuccesses, callback)
                }
            }
        }
        runCommand(command)
    }
}
