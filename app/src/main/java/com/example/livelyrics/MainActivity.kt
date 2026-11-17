package com.example.livelyrics

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val timeMs: Long, val text: String)

class MainActivity : AppCompatActivity() {

    private lateinit var tvSongInfo: TextView
    private lateinit var tvLyrics: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnPermission: Button

    private var currentTrackKey = ""
    private var parsedLyrics: List<LyricLine> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            tvSongInfo = findViewById(R.id.tvSongInfo)
            tvLyrics = findViewById(R.id.tvLyrics)
            scrollView = findViewById(R.id.scrollView)
            btnPermission = findViewById(R.id.btnPermission)

            btnPermission.setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "افتح الإعدادات -> الإشعارات -> الوصول للإشعارات", Toast.LENGTH_LONG).show()
                }
            }

            startTrackingLoop()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
            btnPermission.visibility = if (enabled) View.GONE else View.VISIBLE
        } catch (e: Exception) {
            btnPermission.visibility = View.VISIBLE
        }
    }

    private fun startTrackingLoop() {
        lifecycleScope.launch {
            while (true) {
                try {
                    MediaNotificationListener.refreshCurrentPosition()
                    val song = MediaNotificationListener.nowPlaying.value

                    if (song.title.isNotEmpty()) {
                        tvSongInfo.text = "🎵 ${song.title}\n👤 ${song.artist}"
                        val key = "${song.title}-${song.artist}"
                        if (key != currentTrackKey) {
                            currentTrackKey = key
                            tvLyrics.text = "جاري البحث عن كلمات الأغنية..."
                            parsedLyrics = fetchSyncedLyrics(song.title, song.artist)
                        }
                        updateHighlightedLine(song.positionMs)
                    } else {
                        tvSongInfo.text = "في انتظار تشغيل أي أغنية..."
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(500)
            }
        }
    }

    private suspend fun fetchSyncedLyrics(title: String, artist: String): List<LyricLine> = withContext(Dispatchers.IO) {
        try {
            val qTitle = URLEncoder.encode(title, "UTF-8")
            val qArtist = URLEncoder.encode(artist, "UTF-8")
            val host = "lrclib.net"
            val url = URL("https://$host/api/get?track_name=$qTitle&artist_name=$qArtist")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "LiveLyricsAndroidApp/1.0")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val synced = json.optString("syncedLyrics", "")
                if (synced.isNotEmpty()) return@withContext parseLrc(synced)
                val plain = json.optString("plainLyrics", "")
                if (plain.isNotEmpty()) return@withContext listOf(LyricLine(0L, plain))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext listOf(LyricLine(0L, "لم يتم العثور على كلمات متزامنة لهذه الأغنية."))
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val regex = Regex("\\[(\\d+):(\\d+\\.\\d+)\\](.*)")
        return lrc.lines().mapNotNull { line ->
            val match = regex.find(line)
            if (match != null && match.groupValues.size >= 4) {
                val min = match.groupValues.get(1).toLongOrNull() ?: 0L
                val sec = match.groupValues.get(2).toDoubleOrNull() ?: 0.0
                val text = match.groupValues.get(3).trim()
                val ms = (min * 60 * 1000) + (sec * 1000).toLong()
                LyricLine(ms, text)
            } else null
        }
    }

    private fun updateHighlightedLine(positionMs: Long) {
        if (parsedLyrics.isEmpty()) return
        val activeIndex = parsedLyrics.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)

        val displayBuilder = StringBuilder()
        parsedLyrics.forEachIndexed { index, line ->
            if (index == activeIndex) {
                displayBuilder.append("▶ ${line.text} ◀\n\n")
            } else {
                displayBuilder.append("${line.text}\n\n")
            }
        }
        tvLyrics.text = displayBuilder.toString()
    }
}
