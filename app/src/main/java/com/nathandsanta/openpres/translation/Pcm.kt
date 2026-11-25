package com.nathandsanta.openpres.translation

/** The sound's format, from a Content-Type like "audio/L16; rate=24000; channels=1". */
data class PcmFormat(val rate: Int, val channels: Int) {
    /** Bytes per frame: 16-bit samples, one per channel. */
    val frameBytes: Int get() = 2 * channels

    companion object {
        fun parse(contentType: String?): PcmFormat? {
            val type = contentType?.trim() ?: return null
            if (!type.startsWith("audio/L16", ignoreCase = true)) return null
            fun param(name: String) = Regex("""$name=(\d+)""").find(type)?.groupValues?.get(1)?.toIntOrNull()
            val rate = param("rate") ?: return null
            val channels = param("channels") ?: 1
            if (rate !in 8_000..96_000 || channels !in 1..2) return null
            return PcmFormat(rate, channels)
        }
    }
}

/**
 * Keeps the sound close to live. When the Wi-Fi stalls and then delivers in a burst, more piles
 * up than plays; past `maxMs` queued, chunks are skipped rather than heard late.
 */
class Catchup(private val format: PcmFormat, private val maxMs: Int = 400) {
    private var written = 0L

    /** Whether to play a chunk of `bytes`, given how many frames the player has played. */
    fun keep(bytes: Int, played: Long): Boolean {
        val queued = written - played
        if (queued > format.rate.toLong() * maxMs / 1000) return false
        written += bytes / format.frameBytes
        return true
    }
}
