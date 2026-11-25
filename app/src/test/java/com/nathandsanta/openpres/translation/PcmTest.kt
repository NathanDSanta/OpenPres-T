package com.nathandsanta.openpres.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmTest {
    @Test
    fun formatFromTheContentType() {
        assertEquals(PcmFormat(24_000, 1), PcmFormat.parse("audio/L16; rate=24000; channels=1"))
        assertEquals(PcmFormat(48_000, 2), PcmFormat.parse("audio/L16; rate=48000; channels=2"))
        assertEquals(2, PcmFormat(24_000, 1).frameBytes)
        assertNull(PcmFormat.parse("application/json"))
        assertNull(PcmFormat.parse("audio/L16; channels=1"))
        assertNull(PcmFormat.parse(null))
    }

    @Test
    fun skipsWhatPilesUpBeyond400ms() {
        val catchup = Catchup(PcmFormat(24_000, 1))
        val chunk = 2400 // 50 ms of 24 kHz mono
        // Playing along: nothing is skipped.
        var played = 0L
        repeat(20) {
            assertTrue(catchup.keep(chunk, played))
            played += 1200
        }
        // The network stalled, then 1 s arrives at once: up to 400 ms queue, the rest is skipped.
        val kept = (1..20).count { catchup.keep(chunk, played) }
        assertEquals(9, kept)
        assertFalse(catchup.keep(chunk, played))
        // Once it has played, chunks are kept again.
        assertTrue(catchup.keep(chunk, played + 24_000))
    }
}
