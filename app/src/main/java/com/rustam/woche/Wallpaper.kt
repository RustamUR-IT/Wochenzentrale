package com.rustam.woche

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Live-Hintergrundbild: heutiger Tagesplan, aktualisiert jede Minute. */
class WocheWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = object : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private val tick = object : Runnable { override fun run() { draw(); if (visible) handler.postDelayed(this, 60_000) } }
        override fun onVisibilityChanged(v: Boolean) { visible = v; handler.removeCallbacks(tick); if (v) tick.run() }
        override fun onSurfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) { draw() }
        override fun onDestroy() { handler.removeCallbacks(tick) }

        private fun draw() {
            val holder = surfaceHolder
            val c: Canvas = holder.lockCanvas() ?: return
            try { render(c) } finally { holder.unlockCanvasAndPost(c) }
        }

        private fun render(c: Canvas) {
            val w = c.width.toFloat(); val h = c.height.toFloat()
            val s = w / 1080f
            c.drawColor(Color.parseColor("#0F1117"))
            val today = LocalDate.now()
            val nDays = applicationContext.getSharedPreferences("woche", 0).getInt("wall_days", 1).coerceIn(1, 7)
            val days = if (nDays == 7) { val m = mondayOf(today); (0..6).map { m.plusDays(it.toLong()) } } else (0 until nDays).map { today.plusDays(it.toLong()) }
            val evs = CalendarRepo.load(applicationContext, days.first(), days.last().plusDays(1))
            val placedByDay = days.map { d -> placeDay(evs, d).filter { it.ev.cat != Cat.SLEEP || nDays == 1 } }
            // Zeitbereich an die Termine anpassen, damit die Blöcke so hoch wie möglich werden
            val all = placedByDay.flatten()
            val t0 = (all.minOfOrNull { it.fromMin / 60 } ?: 8).coerceIn(5, 8)
            val t1 = (all.maxOfOrNull { (it.toMin + 59) / 60 } ?: 21).coerceIn(21, 24)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val left = 64f * s; val right = w - 12f * s
            val headH = if (nDays > 1) 56f * s else 0f
            val top = 300f * s + headH; val bottom = h - 120f * s
            val ph = (bottom - top) / (t1 - t0)
            val dn = arrayOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
            p.strokeWidth = 1f
            for (hr in t0 until t1) {
                val y = top + (hr - t0) * ph
                p.color = Color.parseColor("#262A36"); c.drawLine(left, y, right, y, p)
                p.color = Color.parseColor("#8A91A5"); p.textSize = 26f * s; c.drawText("%02d".format(hr), 14f * s, y + 9f * s, p)
            }
            val colW = (right - left) / days.size
            val small = nDays > 3
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
            days.forEachIndexed { di, d ->
                val cx = left + di * colW
                if (nDays > 1) {
                    p.color = if (d == today) Color.parseColor("#FF5D62") else Color.parseColor("#C7CCD8")
                    p.textSize = (if (small) 28f else 34f) * s; p.typeface = Typeface.DEFAULT_BOLD
                    c.drawText("${dn[d.dayOfWeek.value - 1]} ${d.dayOfMonth}", cx + 6f * s, top - 18f * s, p)
                    p.typeface = Typeface.DEFAULT
                }
                for (pl in placedByDay[di]) {
                    val a2 = (pl.fromMin / 60f).coerceAtLeast(t0.toFloat()); val b2 = (pl.toMin / 60f).coerceAtMost(t1.toFloat())
                    if (b2 <= a2) continue
                    val cw = colW / pl.cols
                    val padX = 9f * s; val padY = 6f * s
                    val tw = (cw - 4f - padX - 4f * s).toInt()
                    if (tw < 12) continue
                    tp.color = pl.ev.cat.fg.toInt()
                    val title = shortTitle(pl.ev.title, small)
                    // Schrift so wählen, dass das längste Wort in eine Zeile passt (keine Trennung mitten im Wort)
                    var size = (if (small) 25f else 32f) * s
                    val minSize = (if (small) 17f else 22f) * s
                    val longest = title.split(Regex("\\s+")).maxByOrNull { it.length } ?: title
                    tp.textSize = size
                    while (size > minSize && tp.measureText(longest) > tw) { size -= 1f * s; tp.textSize = size }
                    val lineH = tp.textSize * 1.15f
                    // Mindesthöhe: eine Textzeile, damit kurze Blöcke lesbar bleiben
                    val y0 = top + (a2 - t0) * ph + 1.5f
                    val y1 = maxOf(top + (b2 - t0) * ph - 1.5f, y0 + lineH + 2 * padY)
                    val r = RectF(cx + pl.col * cw + 2f, y0, cx + (pl.col + 1) * cw - 2f, y1)
                    p.color = pl.ev.cat.bg.toInt(); c.drawRoundRect(r, 10f * s, 10f * s, p)
                    p.color = pl.ev.cat.fg.toInt(); c.drawRect(r.left, r.top + 4f * s, r.left + 4f * s, r.bottom - 4f * s, p)
                    val text = title + if (r.height() > lineH * 4) "\n${hm(pl.ev.startMs)}–${hm(pl.ev.endMs)}" else ""
                    val maxLines = ((r.height() - padY) / lineH).toInt().coerceAtLeast(1)
                    val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, tw)
                        .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
                        .setLineSpacing(0f, 1f).setIncludePad(false)
                        .setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE)
                        .setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE)
                        .build()
                    c.save(); c.clipRect(r); c.translate(r.left + padX, r.top + padY)
                    layout.draw(c); c.restore()
                }
            }
            val n = LocalDateTime.now(); val m = n.hour + n.minute / 60f
            val ti = days.indexOf(today)
            if (ti >= 0 && m in t0.toFloat()..t1.toFloat()) {
                val y = top + (m - t0) * ph
                p.color = Color.parseColor("#FF5D62"); p.strokeWidth = 4f * s
                c.drawLine(left + ti * colW, y, left + (ti + 1) * colW, y, p); c.drawCircle(left + ti * colW, y, 8f * s, p)
            }
        }
    }
}

private fun hm(ms: Long): String {
    val t = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return "%02d:%02d".format(t.hour, t.minute)
}
