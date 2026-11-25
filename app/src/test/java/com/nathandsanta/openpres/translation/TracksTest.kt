package com.nathandsanta.openpres.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class TracksTest {
    @Test
    fun theServersTracks() {
        val json = """{"tracks": [
            {"language": "es", "name": "Español", "live": true, "captions": false},
            {"language": "ro", "live": false},
            {"name": "no language"}
        ]}"""
        assertEquals(listOf(Track("es", "Español", true), Track("ro", "ro", false)), Tracks.parse(json))
        assertEquals(emptyList<Track>(), Tracks.parse("{}"))
    }

    @Test
    fun speechAt24kHzMono() {
        assertEquals("http://192.168.1.20:8787/api/translation/tracks/es/listen?rate=24000&mono=1", Tracks.listenUrl("http://192.168.1.20:8787", "es"))
    }
}
