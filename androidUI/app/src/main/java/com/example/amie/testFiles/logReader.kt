package com.example.amie.testFiles

import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.io.File
import java.io.IOException
import android.util.Log.e
import kotlin.collections.takeLast

/**
 * Reads the entire contents of a specified file line-by-line into a list of strings.
 *
 * This function utilizes [File.useLines] internally, which opens a streaming reader and safely
 * closes the underlying resource pipeline automatically after the sequence evaluation completes.
 *
 * ### Architectural & Performance Warnings
 * > !! **Memory Saturation Risk:** This function reads the *entire* file into memory at once via `.toList()`.
 * > If the log file grows exceptionally large (tens or hundreds of megabytes), calling this from the UI thread
 * > will cause major frame drops or trigger an OutOfMemoryError (OOM). Consider using `takeLast(N)` directly on
 * > the stream instead of converting the whole sequence.
 *
 * @param path The absolute hardware file system path targeting the local log asset file.
 * @return A list containing all individual line records from the file, or a single-item error summary list.
 */
fun readLogFile(path: String): List<String> {
    val file = File(path)

    if (!file.exists()) {
        return listOf("Error reading file (Opening error)")
    }

    return try {
        file.useLines { lines ->
            lines.toList()
        }
    }catch (e: IOException) {
            listOf("Error reading file: ${e.localizedMessage}")
        }
}