package com.rustam.woche

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

private const val T0 = 5
private const val T1 = 24
private val HOUR_H = 56.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private fun fontFamily(l: Look): FontFamily = when (l.font) {
    FontStyle.SANS -> FontFamily.SansSerif
    FontStyle.SERIF -> FontFamily.Serif
    FontStyle.MONO -> FontFamily.Monospace
}

private fun cap(l: Look, t: String) = if (l.caps) t.uppercase() else t

@Composable
fun App() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(CalendarRepo.hasPermission(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var look by remember { mutableStateOf(LookStore.load(ctx)) }
    var screen by remember { mutableStateOf("main") }
    var weekView by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }
    // sofort neu laden, sobald Android eine Kalenderänderung meldet
    DisposableEffect(granted) {
        val obs = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { tick++ }
        }
        if (granted) {
            try { ctx.contentResolver.registerContentObserver(android.provider.CalendarContract.CONTENT_URI, true, obs) } catch (_: Exception) {}
            CalendarChangeJob.schedule(ctx)
        }
        onDispose { ctx.contentResolver.unregisterContentObserver(obs) }
    }
    val countdown = remember(granted, tick) { if (granted) CalendarRepo.countdowns(ctx) else emptyList() }
    var pick by remember { mutableStateOf<Ev?>(null) }
    var pendingStatus by remember { mutableStateOf<Pair<Ev, Status>?>(null) }
    var offer by remember { mutableStateOf<Pair<Ev, Pair<Long, Long>>?>(null) }
    var wantMakeUp by remember { mutableStateOf(false) }
    fun proposeMakeUp(ev: Ev) {
        if (makeUpKind(ev) == MakeUpKind.NONE) return
        val slot = CalendarRepo.makeUpSlot(ctx, ev)
        if (slot != null) offer = ev to slot
        else android.widget.Toast.makeText(ctx, if (makeUpKind(ev) == MakeUpKind.STUDY)
            "Kein passender Platz in den nächsten Tagen. Die Sonntagsplanung holt es nach."
            else "Diese Woche passt kein sinnvoller Platz mehr. Lieber ausfallen lassen als quetschen.",
            android.widget.Toast.LENGTH_LONG).show()
    }
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val p = pendingStatus
        if (ok && p != null) { CalendarRepo.setStatus(ctx, p.first, p.second); tick++; if (wantMakeUp) proposeMakeUp(p.first) }
        pendingStatus = null; wantMakeUp = false
    }
    fun mark(ev: Ev, st: Status) {
        if (!ev.editable || ev.recurring) {
            android.widget.Toast.makeText(ctx, if (ev.recurring) "Wiederkehrende Termine kann ich nicht einzeln markieren" else "Dieser Kalender ist nur lesbar (FH, Dienstplan, Moodle)", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (CalendarRepo.canWrite(ctx)) { CalendarRepo.setStatus(ctx, ev, st); tick++; if (wantMakeUp) { wantMakeUp = false; proposeMakeUp(ev) } }
        else { pendingStatus = ev to st; askWrite.launch(Manifest.permission.WRITE_CALENDAR) }
    }
    val s = LocalDensity.current.density / 2.4f

    val days = if (weekView) { val m = mondayOf(anchor); (0..6).map { m.plusDays(it.toLong()) } } else listOf(anchor)
    val events = remember(granted, days.first(), days.size, tick) {
        if (granted) CalendarRepo.load(ctx, days.first(), days.last().plusDays(1)) else emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val t = tick
            drawIntoCanvas { Renderer.background(it.nativeCanvas, size.width, size.height, look, s) }
        }
        offer?.let { (ev, slot) ->
            val st = java.time.Instant.ofEpochMilli(slot.first).atZone(ZoneId.systemDefault())
            val dn = st.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
            AlertDialog(onDismissRequest = { offer = null },
                title = { Text("Nachholen?", fontFamily = fontFamily(look)) },
                text = { Text("${shortTitle(makeUpTitle(ev), false)}\n$dn ${st.dayOfMonth}.${st.monthValue}., ${hm(slot.first)}–${hm(slot.second)}\n\nMit 1 h Abstand zu anderen Terminen.") },
                confirmButton = { TextButton(onClick = {
                    val ok = CalendarRepo.insertMakeUp(ctx, ev, slot); offer = null; tick++
                    android.widget.Toast.makeText(ctx, if (ok) "Eingetragen" else "Konnte nicht eingetragen werden", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Eintragen") } },
                dismissButton = { TextButton(onClick = { offer = null }) { Text("Nein") } })
        }
        pick?.let { ev ->
            AlertDialog(onDismissRequest = { pick = null },
                title = { Text(shortTitle(ev.title, false), fontFamily = fontFamily(look)) },
                text = { Text("${hm(ev.startMs)}–${hm(ev.endMs)}" + when (statusOf(ev.title)) {
                    Status.DONE -> " · erledigt"; Status.MISSED -> " · verpasst"; else -> "" }) },
                confirmButton = { TextButton(onClick = { mark(ev, Status.DONE); pick = null }) { Text("✓ Erledigt") } },
                dismissButton = {
                    Row {
                        TextButton(onClick = { mark(ev, Status.MISSED); pick = null }) { Text("✗ Verpasst") }
                        if (makeUpKind(ev) != MakeUpKind.NONE)
                            TextButton(onClick = { wantMakeUp = true; mark(ev, Status.MISSED); pick = null }) { Text("✗ + Nachholen") }
                        if (statusOf(ev.title) != Status.OPEN) TextButton(onClick = { mark(ev, Status.OPEN); pick = null }) { Text("Zurücksetzen") }
                    }
                })
        }
        if (screen == "look") {
            BackHandler { screen = "main" }
            LookScreen(look, s, onChange = { look = LookStore.load(ctx) }, onBack = { screen = "main" })
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Header(look, anchor, weekView,
                    onPrev = { anchor = anchor.minusDays(if (weekView) 7 else 1) },
                    onNext = { anchor = anchor.plusDays(if (weekView) 7 else 1) },
                    onToday = { anchor = LocalDate.now() },
                    onToggle = { weekView = !weekView },
                    onLook = { screen = "look" })
                if (!granted) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text(cap(look, "Kalender-Zugriff nötig"), color = Color(look.theme.text), fontSize = 20.sp,
                            fontWeight = FontWeight.Bold, fontFamily = fontFamily(look))
                        Spacer(Modifier.height(8.dp))
                        Text("Die App liest die Kalender, die dein Handy schon synchronisiert (Google, FH, Dienstplan). Es gibt keinen Login und nichts verlässt das Handy.",
                            color = Color(look.theme.mute))
                        Spacer(Modifier.height(16.dp))
                        Chip(look, "Zugriff erlauben", selected = true) { ask.launch(Manifest.permission.READ_CALENDAR) }
                    }
                } else {
                    CountdownRow(look, countdown)
                    if (!weekView) NowCard(look, events)
                    Timeline(days, events, weekView, look, s, tick, onTap = { pick = it })
                }
            }
        }
    }
}

@Composable
private fun Header(look: Look, anchor: LocalDate, weekView: Boolean, onPrev: () -> Unit, onNext: () -> Unit,
                   onToday: () -> Unit, onToggle: () -> Unit, onLook: () -> Unit) {
    val title = if (weekView) { val m = mondayOf(anchor); "${m.dayOfMonth}.${m.monthValue}. – ${m.plusDays(6).dayOfMonth}.${m.plusDays(6).monthValue}." }
        else "${anchor.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)}, ${anchor.dayOfMonth}.${anchor.monthValue}."
    val ctx = LocalContext.current
    var wd by remember { mutableIntStateOf(ctx.getSharedPreferences("woche", 0).getInt("wall_days", 1)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(cap(look, title), color = Color(look.theme.text), fontSize = 22.sp, fontWeight = FontWeight.Bold,
            fontFamily = fontFamily(look), maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Chip(look, "‹", onClick = onPrev); Chip(look, "Heute", onClick = onToday); Chip(look, "›", onClick = onNext)
            Chip(look, if (weekView) "Tag" else "Woche", onClick = onToggle)
            Chip(look, "Aussehen", selected = true, onClick = onLook)
            Chip(look, when (wd) { 1 -> "BG: 1 Tag"; 3 -> "BG: 3 Tage"; else -> "BG: Woche" }) {
                wd = when (wd) { 1 -> 3; 3 -> 7; else -> 1 }
                ctx.getSharedPreferences("woche", 0).edit().putInt("wall_days", wd).apply()
            }
            Chip(look, "Festlegen") { setWallpaper(ctx) }
        }
    }
}

@Composable
private fun Chip(look: Look, text: String, selected: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (look.frame == Frame.ROUND) 8.dp else 3.dp)
    Box(Modifier.padding(end = 6.dp).clip(shape)
        .background(if (selected) Color(alphaOf(look.theme.accent, 0.22f)) else Color(alphaOf(look.theme.panel, 0.85f)))
        .border(1.dp, Color(alphaOf(look.theme.accent, if (selected) 0.9f else 0.35f)), shape)
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(cap(look, text), color = Color(if (selected) look.theme.accent else look.theme.text), fontSize = 14.sp,
            fontFamily = fontFamily(look))
    }
}

@Composable
private fun CountdownRow(look: Look, items: List<Countdown>) {
    if (items.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
        items.forEach { cd ->
            val sub = subjectOf(cd.title)
            val col = if (sub != null) mixOf(sub.color, look.theme.text, 0.12f).toLong() and 0xFFFFFFFFL else look.theme.accent
            val urgent = cd.days <= 3
            val shape = RoundedCornerShape(if (look.frame == Frame.ROUND) 8.dp else 3.dp)
            Box(Modifier.padding(end = 6.dp).clip(shape).background(Color(alphaOf(col, if (urgent) 0.32f else 0.16f)))
                .border(if (urgent) 2.dp else 1.dp, Color(alphaOf(col, 0.9f)), shape).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(cap(look, "${cd.title} · ${daysLabel(cd.days)}"), color = Color(if (urgent) look.theme.text else col),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily(look), maxLines = 1)
            }
        }
    }
}

@Composable
private fun NowCard(look: Look, events: List<Ev>) {
    val nowMs = System.currentTimeMillis()
    val cur = events.filter { it.startMs <= nowMs && it.endMs > nowMs && it.cat != Cat.SLEEP }.maxByOrNull { it.startMs }
    val next = events.filter { it.startMs > nowMs }.minByOrNull { it.startMs }
    val ff = fontFamily(look)
    val shape = RoundedCornerShape(if (look.frame == Frame.ROUND) 10.dp else 4.dp)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(shape)
        .background(Color(alphaOf(look.theme.panel, 0.88f)))
        .border(1.dp, Color(alphaOf(look.theme.accent, 0.5f)), shape).padding(12.dp)) {
        Text(cap(look, if (look.caps) "[ Jetzt ]" else "Jetzt"), color = Color(look.theme.accent), fontSize = 11.sp,
            letterSpacing = 1.sp, fontFamily = ff)
        Text(cap(look, cur?.let { shortTitle(it.title, false) } ?: "Frei"), color = Color(look.theme.text), fontSize = 18.sp,
            fontWeight = FontWeight.Bold, fontFamily = ff)
        if (cur != null) {
            Text("${hm(cur.startMs)}–${hm(cur.endMs)}", color = Color(look.theme.mute), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(look.theme.line))) {
                Box(Modifier.fillMaxWidth(((nowMs - cur.startMs).toFloat() / (cur.endMs - cur.startMs)).coerceIn(0f, 1f))
                    .height(4.dp).background(Color(look.theme.now)))
            }
        }
        if (next != null) Text("Danach ${hm(next.startMs)}: ${shortTitle(next.title, false)}", color = Color(look.theme.mute),
            fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = ff)
    }
    Spacer(Modifier.height(8.dp))
}

private fun hm(ms: Long): String {
    val t = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return "%02d:%02d".format(t.hour, t.minute)
}

@Composable
private fun Timeline(days: List<LocalDate>, events: List<Ev>, weekView: Boolean, look: Look, s: Float, tick: Int, onTap: (Ev) -> Unit) {
    val hits = remember { mutableListOf<Pair<android.graphics.RectF, Ev>>() }
    val density = LocalDensity.current
    val vScroll = rememberScrollState()
    LaunchedEffect(Unit) {
        val px = with(density) { HOUR_H.toPx() } * (LocalDateTime.now().hour - T0 - 1).coerceAtLeast(0)
        vScroll.scrollTo(px.toInt())
    }
    val headDp = if (weekView) 26.dp else 0.dp
    val headPx = with(density) { headDp.toPx() }
    val totalH = HOUR_H * (T1 - T0) + headDp
    Box(Modifier.fillMaxSize().verticalScroll(vScroll)) {
        val draw: Modifier = if (weekView) Modifier.width(30.dp + 130.dp * 7).height(totalH) else Modifier.fillMaxWidth().height(totalH)
        Box(if (weekView) Modifier.horizontalScroll(rememberScrollState()) else Modifier) {
            Canvas(draw.pointerInput(events) {
                detectTapGestures { o -> hits.lastOrNull { it.first.contains(o.x, o.y) }?.let { onTap(it.second) } }
            }) {
                val t = tick
                hits.clear()
                drawIntoCanvas {
                    Renderer.schedule(it.nativeCanvas, 0f, 0f, size.width - 8f, size.height, days, events, T0, T1, look, s,
                        compact = weekView, headH = headPx, skipSleep = false, hits = hits)
                }
            }
        }
    }
}

@Composable
private fun LookScreen(look: Look, s: Float, onChange: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ff = fontFamily(look)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(look, "‹ Zurück", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Text(cap(look, "Aussehen"), color = Color(look.theme.text), fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = ff)
        }
        Section(look, "Stil")
        THEMES.forEach { t ->
            val preview = Look.of(t, look.fill)
            val selected = t.id == look.theme.id
            val shape = RoundedCornerShape(10.dp)
            Column(Modifier.fillMaxWidth().clip(shape)
                .border(if (selected) 2.dp else 1.dp, Color(if (selected) t.accent else look.theme.line), shape)
                .clickable { LookStore.setTheme(ctx, t.id); onChange() }) {
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    drawIntoCanvas {
                        Renderer.background(it.nativeCanvas, size.width, size.height, preview, s)
                        Renderer.preview(it.nativeCanvas, size.width, size.height, preview, s)
                    }
                }
                Column(Modifier.fillMaxWidth().background(Color(t.panel)).padding(10.dp)) {
                    Text((if (selected) "✓ " else "") + cap(preview, t.name), color = Color(t.text), fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, fontFamily = fontFamily(preview))
                    Text(t.tagline, color = Color(t.mute), fontSize = 12.sp)
                }
            }
        }
        Section(look, "Rahmen")
        Options(look, Frame.values().map { it.label }, look.frame.ordinal) { LookStore.put(ctx, "frame", Frame.values()[it].name); onChange() }
        Section(look, "Schrift")
        Options(look, FontStyle.values().map { it.label }, look.font.ordinal) { LookStore.put(ctx, "font", FontStyle.values()[it].name); onChange() }
        Section(look, "Effekt im Hintergrund")
        Options(look, Fx.values().map { it.label }, look.fx.ordinal) { LookStore.put(ctx, "fx", Fx.values()[it].name); onChange() }
        Toggle(look, "Leuchten", look.glow) { LookStore.putBool(ctx, "glow", it); onChange() }
        Toggle(look, "Großbuchstaben", look.caps) { LookStore.putBool(ctx, "caps", it); onChange() }
        Toggle(look, "Farbe je Fach", look.bySubject) { LookStore.putBool(ctx, "subj", it); onChange() }
        if (look.bySubject) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Subject.values().forEach { sub ->
                    Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(mixOf(sub.color, look.theme.text, 0.12f))))
                        Spacer(Modifier.width(4.dp))
                        Text(sub.label, color = Color(look.theme.text), fontSize = 12.sp)
                    }
                }
            }
        }
        Section(look, "Farbstärke der Blöcke")
        Slider(value = look.fill, onValueChange = { LookStore.putFloat(ctx, "fill", it); onChange() }, valueRange = 0.08f..0.45f,
            colors = SliderDefaults.colors(thumbColor = Color(look.theme.accent), activeTrackColor = Color(look.theme.accent),
                inactiveTrackColor = Color(look.theme.line)))
        Chip(look, "Als Hintergrund festlegen", selected = true) { setWallpaper(ctx) }
        Text("Gilt für App, Hintergrund und Widget. Ein neuer Stil setzt Rahmen, Schrift, Effekt, Leuchten und Großbuchstaben auf seine Vorgaben zurück.",
            color = Color(look.theme.mute), fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(look: Look, text: String) {
    Text(cap(look, text), color = Color(look.theme.accent), fontSize = 12.sp, letterSpacing = 1.sp, fontFamily = fontFamily(look))
}

@Composable
private fun Options(look: Look, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        labels.forEachIndexed { i, l -> Chip(look, l, selected = i == selected) { onPick(i) } }
    }
}

@Composable
private fun Toggle(look: Look, label: String, value: Boolean, onSet: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(cap(look, label), color = Color(look.theme.text), fontSize = 15.sp, fontFamily = fontFamily(look), modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onSet, colors = SwitchDefaults.colors(
            checkedThumbColor = Color(look.theme.bgTop), checkedTrackColor = Color(look.theme.accent),
            uncheckedThumbColor = Color(look.theme.mute), uncheckedTrackColor = Color(look.theme.panel)))
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
