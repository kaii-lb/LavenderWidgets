package com.kaii.lavender.widgets.music_widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kaii.lavender.widgets.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class MusicWidgetState(
    val title: String,
    val artist: String,
    val albumArt: Bitmap?,
    val isPlaying: Boolean,
    val duration: Long,
    val position: Long,
    val packageName: String,
    val statusBarIcon: Icon?
)

// Default state when no music is playing
val DefaultMusicWidgetState = MusicWidgetState(
    title = "No Music Playing",
    artist = "Not Present",
    albumArt = null,
    isPlaying = false,
    duration = 1L,
    position = 0L,
    packageName = "",
    statusBarIcon = null
)

class MusicWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        actionStartService(Intent(context, MusicListenerService::class.java))

        provideContent {
            Content()
        }
    }

    @OptIn(ExperimentalGlancePreviewApi::class, ExperimentalUnitApi::class)
    @Preview(widthDp = 156, heightDp = 156)
    @Composable
    private fun Content() {
        val state by MusicWidgetReceiver.getState().collectAsState()

        Column(
            modifier = GlanceModifier
                .size(156.dp)
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = GlanceModifier
                    .height(78.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(78.dp)
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider =
                            if (state.albumArt == null) ImageProvider(R.drawable.music_note)
                            else ImageProvider(state.albumArt!!),
                        contentDescription = "Music Album Art",
                        modifier = GlanceModifier
                            .fillMaxSize()
                    )
                }

                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .padding(top = 2.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    val context = LocalContext.current

                    Image(
                        provider =
                            if (state.statusBarIcon == null) ImageProvider(R.drawable.speaker)
                            else ImageProvider(state.statusBarIcon!!),
                        contentDescription = "Music Album Art",
                        modifier = GlanceModifier
                            .size(24.dp)
                            .clickable {
                                launchMediaPlayer(
                                    packageName = state.packageName,
                                    context = context
                                )
                            }
                    )
                }
            }

            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = state.title,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = TextUnit(16f, TextUnitType.Sp),
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = GlanceModifier
                            .width(120.dp)
                            .padding(bottom = (-4).dp)
                    )

                    Text(
                        text = state.artist,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = TextUnit(14f, TextUnitType.Sp)
                        ),
                        modifier = GlanceModifier
                            .width(100.dp)
                            .padding(start = (0.5).dp)
                    )

                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxWidth()
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LinearProgressIndicator(
                            progress = if (state.duration == 0L) 0f else state.position.toFloat() / state.duration,
                            color = GlanceTheme.colors.primary,
                            backgroundColor = GlanceTheme.colors.primaryContainer,
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                    }
                }

                Row(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                ) {
                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()
                    var showSkipBack by remember { mutableStateOf(false) }
                    var showPlayPause by remember { mutableStateOf(false) }
                    var showSkipForward by remember { mutableStateOf(false) }

                    ControlButton(
                        context = context,
                        clicked = showSkipBack,
                        icon = R.drawable.fast_rewind,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(end = 4.dp)
                    ) {
                        coroutineScope.launch {
                            showSkipBack = true
                            MusicListenerService.skipBackward()
                            delay(800)
                            showSkipBack = false
                        }
                    }

                    Spacer(modifier = GlanceModifier.width(2.dp))

                    ControlButton(
                        context = context,
                        clicked = showPlayPause,
                        icon = if (state.isPlaying) R.drawable.pause else R.drawable.play_arrow,
                        modifier = GlanceModifier
                            .defaultWeight()
                    ) {
                        coroutineScope.launch {
                            showPlayPause = true
                            MusicListenerService.togglePlayback()
                            delay(800)
                            showPlayPause = false
                        }
                    }

                    Spacer(modifier = GlanceModifier.width(2.dp))

                    ControlButton(
                        context = context,
                        clicked = showSkipForward,
                        icon = R.drawable.fast_forward,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(start = 4.dp)
                    ) {
                        coroutineScope.launch {
                            showSkipForward = true
                            MusicListenerService.skipForward()
                            delay(800)
                            showSkipForward = false
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ControlButton(
        context: Context,
        clicked: Boolean,
        modifier: GlanceModifier,
        @DrawableRes icon: Int,
        onClick: () -> Unit
    ) {
        val color = GlanceTheme.colors.primary.getColor(context).copy(alpha = if (clicked) 0.6f else 0f)

        Box(
            modifier = modifier
                .fillMaxHeight()
                .cornerRadius(16.dp)
                .background(color)
                .clickable {
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(if (clicked) icon else R.drawable.music_button_ripple),
                contentDescription = "skip back",
                modifier = GlanceModifier
                    .size(32.dp)
            )
        }
    }
}

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()

    companion object {
        internal const val UPDATE_ACTION = "com.kaii.lavender.widgets.UPDATE_MUSIC_WIDGET"
        private var currentState = MutableStateFlow(DefaultMusicWidgetState)

        fun setState(newState: MusicWidgetState) {
            currentState.value = newState
        }

        fun getState() = currentState
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == UPDATE_ACTION) {
            MainScope().launch {
                val manager = GlanceAppWidgetManager(context)

                val glanceIDs = manager.getGlanceIds(MusicWidget::class.java)
                glanceIDs.forEach { id ->
                    glanceAppWidget.update(context, id)
                }
            }
        }
    }
}