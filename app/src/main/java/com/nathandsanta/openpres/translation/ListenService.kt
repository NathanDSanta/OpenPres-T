package com.nathandsanta.openpres.translation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plays a translation track: a foreground service, so it keeps playing with the screen off
 * and the phone in a pocket, with the Wi-Fi and the processor kept awake while it does. It
 * reconnects by itself when the Wi-Fi drops, and waits while the track is off.
 */
class ListenService : Service() {
    /** What the screen shows about it. */
    data class Status(val listening: Boolean, val language: String?, val name: String?, val message: String)

    @Volatile private var running = false
    @Volatile private var connection: HttpURLConnection? = null
    private var thread: Thread? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val server = intent?.getStringExtra(EXTRA_SERVER) ?: return START_NOT_STICKY
        val language = intent.getStringExtra(EXTRA_LANGUAGE) ?: return START_NOT_STICKY
        val name = intent.getStringExtra(EXTRA_NAME) ?: language
        foreground(name, server)
        keepAwake()
        // Another track (or server): the old one stops first.
        stopPlaying()
        running = true
        status = Status(true, language, name, getString(R.string.connecting))
        thread = Thread({ play(server, language, name) }, "openpres-listen").apply { start() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPlaying()
        wifiLock?.release()
        wakeLock?.release()
        status = Status(false, null, null, "")
        super.onDestroy()
    }

    private fun stopPlaying() {
        running = false
        connection?.disconnect()
        thread?.interrupt()
        thread?.join(2_000)
        thread = null
    }

    /** Listens until stopped: connects, plays, and after a problem tries again. */
    private fun play(server: String, language: String, name: String) {
        while (running) {
            val retryIn = try {
                stream(server, language, name)
                2_000L
            } catch (e: Off) {
                if (running) status = Status(true, language, name, getString(R.string.track_off))
                5_000L
            } catch (e: IOException) {
                if (running) status = Status(true, language, name, getString(R.string.reconnecting))
                2_000L
            } catch (e: InterruptedException) {
                return
            }
            try {
                Thread.sleep(retryIn)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private class Off : Exception()

    private fun stream(server: String, language: String, name: String) {
        val c = URL(Tracks.listenUrl(server, language)).openConnection() as HttpURLConnection
        connection = c
        c.connectTimeout = 5_000
        // The server sends every 50 ms: silence for this long means the connection is gone.
        c.readTimeout = 5_000
        try {
            when (c.responseCode) {
                200 -> {}
                404, 409 -> throw Off()
                else -> throw IOException("the server answered ${c.responseCode}")
            }
            val format = PcmFormat.parse(c.contentType) ?: throw IOException("unknown sound format ${c.contentType}")
            val track = player(format)
            try {
                track.play()
                status = Status(true, language, name, getString(R.string.listening_to, name))
                val catchup = Catchup(format)
                val buffer = ByteArray(8_192)
                var carry = 0
                c.inputStream.use { input ->
                    while (running) {
                        val n = input.read(buffer, carry, buffer.size - carry)
                        if (n < 0) break
                        // Whole frames only; a frame split between reads waits for the rest.
                        val total = carry + n
                        val whole = total - total % format.frameBytes
                        if (whole > 0 && catchup.keep(whole, track.playbackHeadPosition.toLong() and 0xFFFFFFFFL)) {
                            track.write(buffer, 0, whole)
                        }
                        carry = total - whole
                        if (carry > 0) System.arraycopy(buffer, whole, buffer, 0, carry)
                    }
                }
                // The server ended it: the track went off or moved to another bus.
                if (running) throw IOException("the server stopped sending")
            } finally {
                track.stop()
                track.release()
            }
        } finally {
            c.disconnect()
            connection = null
        }
    }

    private fun player(format: PcmFormat): AudioTrack {
        val channels = if (format.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minimum = AudioTrack.getMinBufferSize(format.rate, channels, AudioFormat.ENCODING_PCM_16BIT)
        // About 0.5 s: room for Wi-Fi hiccups without adding much delay.
        val size = maxOf(minimum, format.rate * format.frameBytes / 2)
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(format.rate).setChannelMask(channels).build())
            .setBufferSizeInBytes(size)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun keepAwake() {
        if (wifiLock == null) {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wifi?.createWifiLock(mode, "openpres:listen")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wakeLock == null) {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Held while listening, however long the service lasts; released when it stops.
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "openpres:listen").apply {
                setReferenceCounted(false)
                @Suppress("WakelockTimeout")
                acquire()
            }
        }
    }

    private fun foreground(name: String, server: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, ListenActivity::class.java).putExtra(ListenActivity.EXTRA_ADDRESS, server), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, ListenService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.listening_to, name))
            .setContentText(server.removePrefix("http://"))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null as Icon?, getString(R.string.stop), stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION, notification)
        }
    }

    companion object {
        const val EXTRA_SERVER = "server"
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_NAME = "name"
        private const val ACTION_STOP = "stop"
        private const val CHANNEL = "listening"
        private const val NOTIFICATION = 1

        /** Read by the screen while it's open. */
        @Volatile
        var status = Status(false, null, null, "")
            private set

        fun start(context: Context, server: String, track: Track) {
            val intent = Intent(context, ListenService::class.java)
                .putExtra(EXTRA_SERVER, server)
                .putExtra(EXTRA_LANGUAGE, track.language)
                .putExtra(EXTRA_NAME, track.name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListenService::class.java))
        }
    }
}
