package com.nathandsanta.openpres.translation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nathandsanta.openpres.translation.Ui.dp

/**
 * A server's translation tracks: tap one to listen. It keeps playing with the screen off and
 * from the notification; the phone's volume buttons set the volume.
 */
class ListenActivity : Activity() {
    private lateinit var address: String
    private lateinit var list: LinearLayout
    private lateinit var info: TextView
    private lateinit var state: TextView
    private val main = Handler(Looper.getMainLooper())
    private var tracks: List<Track> = emptyList()
    private var problem = ""
    private var shown: ListenService.Status? = null
    private var visible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        address = intent.getStringExtra(EXTRA_ADDRESS) ?: return finish()
        volumeControlStream = AudioManager.STREAM_MUSIC

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        content.addView(Ui.text(this, getString(R.string.app_name), 26f, bold = true))
        content.addView(Ui.text(this, address.removePrefix("http://"), 14f, Ui.MUTED).apply { setPadding(0, dp(4), 0, dp(20)) })
        state = Ui.text(this, "", 16f, Ui.TEXT).apply { setPadding(0, 0, 0, dp(16)) }
        content.addView(state)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        info = Ui.text(this, getString(R.string.looking_for_tracks), 14f, Ui.MUTED).apply { setPadding(0, dp(8), 0, dp(8)) }
        content.addView(info)
        content.addView(Ui.text(this, getString(R.string.volume_note), 13f, Ui.MUTED).apply { setPadding(0, dp(24), 0, dp(16)) })
        content.addView(
            Ui.button(this, getString(R.string.other_server)) {
                ListenService.stop(this)
                finish()
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BACKGROUND)
            isFillViewport = true
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        visible = true
        Updates.resumed(this)
        refresh()
        tick()
    }

    override fun onPause() {
        visible = false
        main.removeCallbacksAndMessages(null)
        Updates.paused(this)
        super.onPause()
    }

    /** Asks the server for its tracks now, then every few seconds while the screen is open. */
    private fun refresh() {
        Thread {
            val result = runCatching { Tracks.fetch(address) }
            main.post {
                result.onSuccess {
                    tracks = it
                    problem = ""
                }.onFailure {
                    problem = getString(R.string.cant_reach, address.removePrefix("http://"), it.message ?: it.javaClass.simpleName)
                }
                show()
                if (visible) main.postDelayed({ refresh() }, 5_000)
            }
        }.start()
    }

    /** What the player is doing, twice a second. */
    private fun tick() {
        if (!visible) return
        if (ListenService.status != shown) show()
        main.postDelayed({ tick() }, 500)
    }

    private fun show() {
        val status = ListenService.status
        shown = status
        state.text = if (status.listening) status.message else ""
        state.visibility = if (status.listening) View.VISIBLE else View.GONE
        list.removeAllViews()
        val live = tracks.filter { it.live }
        for (track in live) {
            val playing = status.listening && status.language == track.language
            list.addView(
                Ui.button(this, if (playing) getString(R.string.stop_track, track.name) else getString(R.string.listen_to, track.name), primary = !playing) {
                    if (playing) ListenService.stop(this) else listen(track)
                }.apply {
                    textSize = 20f
                    minHeight = dp(72)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) },
            )
        }
        info.text = when {
            problem.isNotEmpty() -> problem
            live.isEmpty() && tracks.isNotEmpty() -> getString(R.string.no_translation_now)
            live.isEmpty() -> getString(R.string.no_tracks)
            else -> ""
        }
        info.setTextColor(if (problem.isNotEmpty()) Ui.ERROR else Ui.MUTED)
        info.visibility = if (info.text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun listen(track: Track) {
        // Android 13+: the notification (with its Stop button) needs permission; playing doesn't.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        ListenService.start(this, address, track)
        show()
    }

    companion object {
        const val EXTRA_ADDRESS = "address"
    }
}
