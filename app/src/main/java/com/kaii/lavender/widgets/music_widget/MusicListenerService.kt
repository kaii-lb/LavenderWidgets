package com.kaii.lavender.widgets.music_widget

import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.kaii.lavender.widgets.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "MUSIC_LISTENER_SERVICE"

class MusicListenerService : NotificationListenerService() {
    private var mediaSessionManager: MediaSessionManager? = null

    private val handler = Handler(Looper.getMainLooper())
    private var currentPosition = 0L
    private var isPolling = false
    private var statusBarIcon: Icon? = null

    private val componentName = ComponentName("com.kaii.lavender.widgets", "com.kaii.lavender.widgets.music_widget.MusicListenerService")
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    companion object {
        private var mediaController: MediaController? = null

        fun togglePlayback() =
            mediaController?.let { controller ->
                val state = controller.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
                    controller.transportControls.pause()
                } else if (state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED || state == PlaybackState.STATE_NONE) {
                    controller.transportControls.play()
                }
            }

        fun skipForward() = mediaController?.transportControls?.skipToNext()

        fun skipBackward() = mediaController?.transportControls?.skipToPrevious()
    }

    override fun onCreate() {
        super.onCreate()

        mediaSessionManager = applicationContext.getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val controllers = mediaSessionManager!!.getActiveSessions(componentName)
        mediaController = pickController(controllers)

        mediaSessionManager!!.addOnActiveSessionsChangedListener(
            sessionListener,
            componentName
        )

        if (mediaController != null) {
            mediaController!!.registerCallback(mediaControllerCallback)
            currentPosition = mediaController?.playbackState?.position ?: 0L

            if (mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING) {
                isPolling = true
                handler.removeCallbacks(updatePositionRunnable)
                handler.post(updatePositionRunnable)
            }
        }

        updateGlanceWidgetUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        mediaController?.unregisterCallback(mediaControllerCallback)

        mediaController = null
        mediaSessionManager = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) {
            if (sbn.notification?.hasImage() == true && sbn.notification?.contentIntent?.creatorPackage == mediaController?.packageName) {
                statusBarIcon = sbn.notification?.smallIcon
                updateGlanceWidgetUI()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.notification?.hasImage() == true && sbn.notification?.contentIntent?.creatorPackage == mediaController?.packageName) {
            statusBarIcon = null
            updateGlanceWidgetUI()
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        val newController = controllers?.let { pickController(it) }

        if (newController != null) {
            mediaController?.unregisterCallback(mediaControllerCallback)
            mediaController = newController
            mediaController?.registerCallback(mediaControllerCallback)

            currentPosition = mediaController?.playbackState?.position ?: 0L

            if (mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING) {
                isPolling = true
                handler.removeCallbacks(updatePositionRunnable)
                handler.post(updatePositionRunnable)
            }

            updateGlanceWidgetUI()
        } else {
            mediaController?.unregisterCallback(mediaControllerCallback)
            mediaController = null

            updateGlanceWidgetUIForNoPlayer()
        }
    }

    private fun pickController(controllers: List<MediaController>): MediaController? {
        for (controller in controllers) {
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING || controller.playbackState?.state == PlaybackState.STATE_BUFFERING) {
                return controller
            }
        }

        if (controllers.isNotEmpty()) return controllers[0]
        return null
    }

    private val updatePositionRunnable = object : Runnable {
        override fun run() {
            mediaController?.playbackState?.let { state ->
                if (state.state == PlaybackState.STATE_PLAYING) {
                    val elapsedMillis = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
                    val calculatedPosition = (state.position + (elapsedMillis * state.playbackSpeed)).toLong()
                    currentPosition = calculatedPosition

                    updateGlanceWidgetUI()
                    handler.postDelayed(this, 1000)
                } else {
                    isPolling = false
                }
            }
        }
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)

            currentPosition = state?.position ?: 0L
            updateGlanceWidgetUI()

            if (state?.state == PlaybackState.STATE_PLAYING && !isPolling) {
                isPolling = true
                handler.removeCallbacks(updatePositionRunnable)
                handler.post(updatePositionRunnable)
            } else {
                isPolling = false
                handler.removeCallbacks(updatePositionRunnable)
                updateGlanceWidgetUI()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)

            isPolling = false
            currentPosition = 0L
            handler.removeCallbacks(updatePositionRunnable)
            handler.post(updatePositionRunnable)
            updateGlanceWidgetUI()
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()

            currentPosition = 0L
            isPolling = false
            handler.removeCallbacks(updatePositionRunnable)
            updateGlanceWidgetUI()
        }

        override fun onSessionEvent(event: String, extras: Bundle?) {
            super.onSessionEvent(event, extras)

            updateGlanceWidgetUI()
        }
    }

    private fun updateGlanceWidgetUI() = coroutineScope.launch {
        val metadata = mediaController?.metadata
        val state = mediaController?.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "No Title"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "No Artist"
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING

        val albumArt =
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: BitmapFactory.decodeResource(
                applicationContext.resources,
                R.drawable.ic_launcher_foreground
            )
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 1L

        val widgetState = MusicWidgetState(
            title = title,
            artist = artist,
            albumArt = albumArt,
            isPlaying = isPlaying,
            duration = duration,
            position = currentPosition,
            packageName = mediaController?.packageName ?: "",
            statusBarIcon = statusBarIcon
        )

        MusicWidgetReceiver.setState(widgetState)
        Log.d(TAG, "Media State $widgetState")

        val intent = Intent(applicationContext, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.UPDATE_ACTION
        }
        applicationContext.sendBroadcast(intent)
    }

    private fun updateGlanceWidgetUIForNoPlayer() = coroutineScope.launch {
        MusicWidgetReceiver.setState(
            DefaultMusicWidgetState
        )
    }
}