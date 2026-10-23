package com.rustam.woche

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val Bg = Color(0xFF0F1117)
private val Panel = Color(0xFF161922)
private val Line = Color(0xFF262A36)
private val Mute = Color(0xFF8A91A5)
private val Fg = Color(0xFFE8EAF0)
private val NowRed = Color(0xFFFF5D62)
private const val START_H = 5
private const val END_H = 24
private val HOUR_H = 48.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var granted by remember { mutableStateOf(CalendarRepo.hasPermission(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var weekView by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = LocalDateTime.now(); tick++ } }

    val days = if (weekView) { val m = mondayOf(anchor); (0..6).map { m.plusDays(it.toLong()) } } else listOf(anchor)
    val events = remember(granted, days.first(), days.size, tick) {
        if (granted) CalendarRepo.load(ctx, days.first(), days.last().plusDays(1)) else emptyList()
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        Header(anchor, weekView, onPrev = { anchor = anchor.minusDays(if (weekView) 7 else 1) },
            onNext = { anchor = anchor.plusDays(if (weekView) 7 else 1) },
            onToday = { anchor = LocalDate.now() }, onToggle = { weekView = !weekView })
        if (!granted) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Kalender-Zugriff nötig", color = Fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Die App liest die Kalender, die dein Handy schon synchronisiert (Google, FH, Dienstplan). Es gibt keinen Login und nichts verlässt das Handy.", color = Mute)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(NowRed)
                    .clickable { ask.launch(Manifest.permission.READ_CALENDAR) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("Zugriff erlauben", color = Color.White)
                }
            }
        } else {
            if (!weekView) NowCard(events, now)
            Timeline(days, events, now, weekView)
        }
    }
}

@Composable
private fun Header(anchor: LocalDate, weekView: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, onToggle: () -> Unit) {
    val title = if (weekView) { val m = mondayOf(anchor); "${m.dayOfMonth}.${m.monthValue}. – ${m.plusDays(6).dayOfMonth}.${m.plusDays(6).monthValue}." }
    else "${anchor.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)}, ${anchor.dayOfMonth}.${anchor.monthValue}."
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Chip("‹", onPrev); Chip("Heute", onToday); Chip("›", onNext); Chip(if (weekView) "Tag" else "Woche", onToggle)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var wd by remember { mutableIntStateOf(ctx.getSharedPreferences("woche", 0).getInt("wall_days", 1)) }
        Chip(when (wd) { 1 -> "BG: 1 Tag"; 3 -> "BG: 3 Tage"; else -> "BG: Woche" }) {
            wd = when (wd) { 1 -> 3; 3 -> 7; else -> 1 }
            ctx.getSharedPreferences("woche", 0).edit().putInt("wall_days", wd).apply()
        }
        Chip("Festlegen") { setWallpaper(ctx) }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Box(Modifier.padding(start = 6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
        .background(Panel).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(text, color = Fg, fontSize = 14.sp)
    }
}

@Composable
private fun NowCard(events: List<Ev>, now: LocalDateTime) {
    val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val cur = events.filter { it.startMs <= nowMs && it.endMs > nowMs && it.cat != Cat.SLEEP }.maxByOrNull { it.startMs }
    val next = events.filter { it.startMs > nowMs }.minByOrNull { it.startMs }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Panel).padding(12.dp)) {
        Text("JETZT", color = Mute, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(cur?.title ?: "Frei", color = Fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (cur != null) {
            Text("${hm(cur.startMs)}–${hm(cur.endMs)}", color = Mute, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(Line)) {
                Box(Modifier.fillMaxWidth(((nowMs - cur.startMs).toFloat() / (cur.endMs - cur.startMs)).coerceIn(0f, 1f)).height(4.dp).background(NowRed))
            }
        }
        if (next != null) Text("Danach ${hm(next.startMs)}: ${next.title}", color = Mute, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(8.dp))
}

private fun hm(ms: Long): String {
    val t = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return "%02d:%02d".format(t.hour, t.minute)
}

@Composable
private fun Timeline(days: List<LocalDate>, events: List<Ev>, now: LocalDateTime, weekView: Boolean) {
    val colW = if (weekView) 112.dp else 0.dp
    val vScroll = rememberScrollState(((LocalDateTime.now().hour - START_H - 1).coerceAtLeast(0) * 48 * 2.6f).toInt())
    val hScroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        if (weekView) {
            Row(Modifier.horizontalScroll(hScroll)) {
                Spacer(Modifier.width(40.dp))
                days.forEach { d ->
                    val today = d == LocalDate.now()
                    Text("${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)} ${d.dayOfMonth}",
                        color = if (today) NowRed else Mute, fontSize = 12.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.width(colW).padding(vertical = 6.dp, horizontal = 6.dp))
                }
            }
        }
        Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            Column(Modifier.width(40.dp)) {
                for (h in START_H until END_H) Box(Modifier.height(HOUR_H)) {
                    Text("%02d".format(h), color = Mute, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 8.dp))
                }
            }
            val body = if (weekView) Modifier.horizontalScroll(hScroll) else Modifier.weight(1f)
            Row(body) {
                days.forEach { d ->
                    DayColumn(d, events, now, if (weekView) Modifier.width(colW) else Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayColumn(day: LocalDate, events: List<Ev>, now: LocalDateTime, modifier: Modifier) {
    val placed = remember(day, events) { placeDay(events, day) }
    BoxWithConstraints(modifier.height(HOUR_H * (END_H - START_H)).background(if (day == LocalDate.now()) Color(0xFF13161F) else Bg)) {
        for (h in 0 until END_H - START_H) Box(Modifier.offset(y = HOUR_H * h).fillMaxWidth().height(1.dp).background(Line))
        val pxPerMin = HOUR_H / 60
        placed.forEach { p ->
            val from = (p.fromMin - START_H * 60).coerceAtLeast(0)
            val to = (p.toMin - START_H * 60).coerceAtMost((END_H - START_H) * 60)
            if (to <= from) return@forEach
            val w = maxWidth / p.cols
            Column(Modifier.offset(x = w * p.col, y = pxPerMin * from).width(w).height((pxPerMin * (to - from)).coerceAtLeast(16.dp))
                .padding(1.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(Color(p.ev.cat.bg)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                Text(p.ev.title, color = Color(p.ev.cat.fg), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                if (to - from >= 40) Text("${hm(p.ev.startMs)}–${hm(p.ev.endMs)}", color = Color(p.ev.cat.fg).copy(alpha = .75f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
        if (day == now.toLocalDate()) {
            val m = now.hour * 60 + now.minute - START_H * 60
            if (m in 0..(END_H - START_H) * 60) Box(Modifier.offset(y = pxPerMin * m).fillMaxWidth().height(2.dp).background(NowRed))
        }
    }
}

/** Öffnet das System-Fenster, in dem der Live-Hintergrund bestätigt wird. */
private fun setWallpaper(ctx: android.content.Context) {
    try {
        ctx.startActivity(android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, android.content.ComponentName(ctx, WocheWallpaper::class.java))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try {
            ctx.startActivity(android.content.Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: Exception) {
            android.widget.Toast.makeText(ctx, "Öffne Hintergrund-Einstellungen von Hand", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
