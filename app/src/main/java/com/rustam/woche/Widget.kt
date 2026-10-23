package com.rustam.woche

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.LocalDate
import java.time.ZoneId

/** Homescreen-Widget: die nächsten Termine von heute. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val look = LookStore.load(context)
        val evs = CalendarRepo.load(context, today, today.plusDays(1))
            .filter { it.endMs > now && it.cat != Cat.SLEEP }.sortedBy { it.startMs }.take(8)
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(ColorProvider(Color(look.theme.bgBottom))).padding(10.dp)) {
                Text(if (look.caps) "[ HEUTE ]" else "Heute", style = TextStyle(color = ColorProvider(Color(look.theme.accent)), fontSize = 11.sp))
                if (evs.isEmpty()) Text("Nichts mehr geplant", style = TextStyle(color = ColorProvider(Color(look.theme.text)), fontSize = 14.sp))
                evs.forEach {
                    Text("${hmz(it.startMs)}  ${shortTitle(it.title, false)}", maxLines = 1,
                        style = TextStyle(color = ColorProvider(Color(look.colorFor(it.title, it.cat))), fontSize = 13.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

private fun hmz(ms: Long): String {
    val t = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return "%02d:%02d".format(t.hour, t.minute)
}

class TodayReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
