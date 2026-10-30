package com.shizuku.arranger.data

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses names produced by the ShizuCallRecorder default template
 * `{date}_{direction}_{phone_number}`, where `{date}` is `yyyyMMdd_HHmmss.SSSZ`, e.g.
 * `20240131_154502.123+0530_in_+15551234567.ogg`.
 *
 * Also copes with slightly different date formats (no millis / no zone) and falls back
 * to the file's modification time when the date can't be read.
 */
object FileNameParser {

    data class Parsed(val timestamp: Long?, val direction: Direction, val phoneNumber: String)

    private val DIRECTION_REGEX = Regex("""^(.*)_(in|out|incoming|outgoing)_(.*)$""", RegexOption.IGNORE_CASE)

    /** ".ogg", ".m4a", ".3gp"... but not the ".123+0530_in_..." millis part of the date. */
    private val EXTENSION_REGEX = Regex("""\.(?=[A-Za-z0-9]*[A-Za-z])[A-Za-z0-9]{2,5}$""")

    private val DATE_PATTERNS = listOf(
        "yyyyMMdd_HHmmss.SSSZ",
        "yyyyMMdd_HHmmss.SSS",
        "yyyyMMdd_HHmmssZ",
        "yyyyMMdd_HHmmss",
        "yyyy-MM-dd_HH-mm-ss",
        "yyyyMMddHHmmss",
        "yyyyMMdd",
    )

    fun parse(fileName: String): Parsed {
        val base = fileName.replace(EXTENSION_REGEX, "")
        val match = DIRECTION_REGEX.find(base)
            ?: return Parsed(parseDate(base), Direction.UNKNOWN, "")
        val (datePart, dirPart, numberPart) = match.destructured
        val direction = if (dirPart.lowercase().startsWith("in")) Direction.INCOMING else Direction.OUTGOING
        return Parsed(parseDate(datePart), direction, numberPart.trim())
    }

    private fun parseDate(text: String): Long? {
        for (pattern in DATE_PATTERNS) {
            val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val pos = ParsePosition(0)
            val date = format.parse(text, pos) ?: continue
            if (pos.index == text.length) return date.time
        }
        return null
    }
}
