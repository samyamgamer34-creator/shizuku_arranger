package com.shizuku.arranger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class FileNameParserTest {

    private fun utc(text: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(text)!!.time

    @Test
    fun parsesDefaultTemplate() {
        val p = FileNameParser.parse("20240131_154502.123+0530_in_+15551234567.ogg")
        assertEquals(Direction.INCOMING, p.direction)
        assertEquals("+15551234567", p.phoneNumber)
        assertEquals(utc("2024-01-31 10:15:02.123"), p.timestamp)
    }

    @Test
    fun parsesOutgoingM4a() {
        val p = FileNameParser.parse("20231105_080000.000-0400_out_5551234.m4a")
        assertEquals(Direction.OUTGOING, p.direction)
        assertEquals("5551234", p.phoneNumber)
        assertEquals(utc("2023-11-05 12:00:00.000"), p.timestamp)
    }

    @Test
    fun handlesEmptyNumber() {
        val p = FileNameParser.parse("20240131_154502.123+0000_in_.ogg")
        assertEquals(Direction.INCOMING, p.direction)
        assertEquals("", p.phoneNumber)
        assertNotNull(p.timestamp)
    }

    @Test
    fun unknownNameFallsBack() {
        val p = FileNameParser.parse("my voice note.mp3")
        assertEquals(Direction.UNKNOWN, p.direction)
        assertNull(p.timestamp)
    }
}
