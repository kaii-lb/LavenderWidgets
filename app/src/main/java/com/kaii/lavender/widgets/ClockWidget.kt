package com.kaii.lavender.widgets

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

class ClockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Content()
        }
    }

    @OptIn(ExperimentalUnitApi::class)
    @Composable
    private fun Content() {
        val currentTime = System.currentTimeMillis()
        val dateFormatter = SimpleDateFormat("hh:mm", Locale.getDefault())
        val formatted = dateFormatter.format(Date(currentTime))

        Row(
            modifier = GlanceModifier
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatted[0].toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = TextUnit(52f, TextUnitType.Sp),
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = formatted[1].toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = TextUnit(52f, TextUnitType.Sp),
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = formatted[2].toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = TextUnit(52f, TextUnitType.Sp),
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = formatted[3].toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = TextUnit(52f, TextUnitType.Sp),
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = formatted[4].toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = TextUnit(52f, TextUnitType.Sp),
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private var isDisabled = false

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        isDisabled = true
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(createBroadcastPendingIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (!isDisabled) {
            scope.launch {
                ClockWidget().updateAll(context)
            }
            scheduleUpdate(context)
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleUpdate(context: Context) {
        val pendingIntent = createBroadcastPendingIntent(context)
        val calendar = (Calendar.getInstance().clone() as Calendar).apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1)
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.setExact(AlarmManager.RTC, calendar.timeInMillis, pendingIntent)
    }

    private fun createBroadcastPendingIntent(context: Context): PendingIntent {
        val intent = Intent(
            context,
            ClockWidgetReceiver::class.java
        ).setAction("${context.packageName}.tick")
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}