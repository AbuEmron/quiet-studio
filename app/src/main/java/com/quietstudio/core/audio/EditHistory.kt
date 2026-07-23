package com.quietstudio.core.audio

import java.io.File

/**
 * File-backed undo/redo for destructive audio edits. Each edit writes a new
 * numbered WAV snapshot; undo/redo just moves the cursor. Snapshots live in
 * the project's cache dir and are pruned beyond [maxDepth].
 */
class EditHistory(private val dir: File, private val maxDepth: Int = 12) {

    private val stack = ArrayList<File>()
    private var cursor = -1

    val canUndo: Boolean get() = cursor > 0
    val canRedo: Boolean get() = cursor < stack.size - 1
    val current: File? get() = stack.getOrNull(cursor)

    fun init(first: File) {
        stack.clear()
        stack.add(first)
        cursor = 0
    }

    /** Reserve the next snapshot file; caller writes into it, then commits. */
    fun nextSnapshotFile(): File {
        dir.mkdirs()
        return File(dir, "edit_${System.currentTimeMillis()}.wav")
    }

    fun commit(snapshot: File) {
        // drop redo branch
        while (stack.size - 1 > cursor) stack.removeAt(stack.size - 1).delete()
        stack.add(snapshot)
        cursor = stack.size - 1
        while (stack.size > maxDepth) {
            stack.removeAt(0).takeIf { it.parentFile == dir }?.delete()
            cursor--
        }
    }

    fun undo(): File? {
        if (!canUndo) return null
        cursor--
        return stack[cursor]
    }

    fun redo(): File? {
        if (!canRedo) return null
        cursor++
        return stack[cursor]
    }

    fun clear() {
        stack.forEachIndexed { i, f -> if (i > 0) f.delete() }
        stack.clear()
        cursor = -1
    }
}
