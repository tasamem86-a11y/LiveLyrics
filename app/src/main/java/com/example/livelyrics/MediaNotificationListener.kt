package com.example.livelyrics

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false
)

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        private val _nowPlaying = MutableStateFlow(NowPlaying())
        val nowPlaying = _nowPlaying.asStateFlow()

        var activeController: MediaController? = null

        fun refreshCurrentPosition() {
            activeController?.let { controller ->
                val state = controller.playbackState
                val meta = controller.metadata
                if (meta != null && state != null) {
                    val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                    val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    _nowPlaying.value = NowPlaying(
                        title = title,
                        artist = artist,
                        positionMs = state.position,
                        isPlaying = state.state == PlaybackState.STATE_PLAYING
                    )
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, MediaNotificationListener::class.java)

        val updateSessions = { controllers: List<MediaController>? ->
            activeController = controllers?.firstOrNull()
            refreshCurrentPosition()
        }

        sessionManager.addOnActiveSessionsChangedListener(updateSessions, component)
        updateSessions(sessionManager.getActiveSessions(component))
    }
}
