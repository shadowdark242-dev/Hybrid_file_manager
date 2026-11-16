package org.fossify.filemanager.helpers

import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache of folder sizes used by the "sort by size" mode.
 *
 * A folder is scanned once; entering it again reuses the stored value. Entries are dropped only when a
 * delete, copy or move touches the folder (or anything inside it), on pull-to-refresh, and when the app
 * is started again.
 */
object FolderSizeCache {
    private val sizes = ConcurrentHashMap<String, Long>()

    private fun key(path: String, countHidden: Boolean) = (if (countHidden) "1" else "0") + normalize(path)

    private fun normalize(path: String) = path.trimEnd('/').ifEmpty { "/" }

    fun get(path: String, countHidden: Boolean): Long? = sizes[key(path, countHidden)]

    fun put(path: String, countHidden: Boolean, size: Long) {
        sizes[key(path, countHidden)] = size
    }

    fun clear() = sizes.clear()

    /** Forgets [paths], everything inside them and every folder that contains them. */
    fun invalidate(paths: Collection<String>) {
        if (paths.isEmpty()) {
            return
        }

        val targets = paths.map { normalize(it) }
        sizes.keys.removeAll { stored ->
            val path = stored.substring(1)
            targets.any { target ->
                path == target ||
                    path == "/" ||
                    target.startsWith("$path/") ||
                    path.startsWith("$target/")
            }
        }
    }

    fun invalidate(vararg paths: String) = invalidate(paths.toList())

    /**
     * Size of [dir] in bytes, or null if [isCancelled] became true. Symbolic links are not followed, and every
     * sub-folder that is fully scanned is cached too, so opening it later is instant.
     */
    fun computeSize(dir: File, countHidden: Boolean, isCancelled: () -> Boolean): Long? {
        get(dir.absolutePath, countHidden)?.let { return it }
        if (isCancelled()) {
            return null
        }

        var size = 0L
        val children = dir.listFiles() ?: return 0L.also { put(dir.absolutePath, countHidden, it) }
        val hiddenDir = dir.name.startsWith('.')
        for (child in children) {
            if (isCancelled()) {
                return null
            }

            val isLink = try {
                Files.isSymbolicLink(child.toPath())
            } catch (e: Exception) {
                false
            }

            if (!isLink && child.isDirectory) {
                size += computeSize(child, countHidden, isCancelled) ?: return null
            } else if (countHidden || (!hiddenDir && !child.name.startsWith('.'))) {
                size += child.length()
            }
        }

        put(dir.absolutePath, countHidden, size)
        return size
    }
}
