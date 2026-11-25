package com.nathandsanta.openpres.translation

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A language track the server offers: `live` while phones can listen to it. */
data class Track(val language: String, val name: String, val live: Boolean)

/** The server's translation tracks (/api/translation/tracks: no login needed). */
object Tracks {
    fun parse(json: String): List<Track> {
        val list = JSONObject(json).optJSONArray("tracks") ?: return emptyList()
        return (0 until list.length())
            .map { list.getJSONObject(it) }
            .map { Track(it.optString("language"), it.optString("name").ifEmpty { it.optString("language") }, it.optBoolean("live")) }
            .filter { it.language.isNotEmpty() }
    }

    /** Speech: 24 kHz mono, a quarter of what music would need on the Wi-Fi. */
    fun listenUrl(server: String, language: String): String =
        "$server/api/translation/tracks/${URLEncoder.encode(language, "UTF-8")}/listen?rate=24000&mono=1"

    /** Asks the server (on a background thread). Throws when it can't be reached. */
    fun fetch(server: String): List<Track> {
        val connection = URL("$server/api/translation/tracks").openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            val code = connection.responseCode
            if (code == 404) throw IllegalStateException("this server has no translation (it needs a newer OpenPres)")
            if (code != 200) throw IllegalStateException("the server answered $code")
            return parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
