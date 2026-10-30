package com.shizuku.arranger.data

import java.io.File

enum class Direction { INCOMING, OUTGOING, UNKNOWN }

data class Recording(
    val file: File,
    /** Epoch millis of the call start (from the file name, or the file's mtime as a fallback). */
    val timestamp: Long,
    val direction: Direction,
    /** Phone number as written in the file name; empty for hidden / unknown numbers. */
    val phoneNumber: String,
    val sizeBytes: Long,
) {
    val displayNumber: String get() = phoneNumber.ifEmpty { "Unknown number" }

    /** Digits only, so "+1 (555) 123" matches a search for "1555123". */
    val numberDigits: String = phoneNumber.filter { it.isDigit() }
}
