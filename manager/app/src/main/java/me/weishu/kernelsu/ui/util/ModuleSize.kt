package me.weishu.kernelsu.ui.util

import java.io.File

/**
 * Total size of a module's folder, walked recursively, or null when it cannot
 * be read (the directory does not exist, or the app has no permission on it).
 *
 * Null rather than 0 so the caller can skip the row instead of claiming a
 * module weighs nothing. Symlinks are not followed: a module's tree can link
 * out to the system partition, and following one would report a size that has
 * nothing to do with the module.
 */
fun moduleDirectorySize(path: String): Long? {
    val root = File(path)
    if (!root.isDirectory || !root.canRead()) return null
    var total = 0L
    var ok = false
    val stack = ArrayDeque<File>()
    stack.addLast(root)
    while (stack.isNotEmpty()) {
        val dir = stack.removeLast()
        val children = dir.listFiles() ?: continue
        ok = true
        for (child in children) {
            if (child.isDirectory) {
                stack.addLast(child)
            } else {
                total += child.length()
            }
        }
    }
    return if (ok) total else null
}

/** Renders a byte count with one decimal place, e.g. `1.4 MB`. */
fun formatByteSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}