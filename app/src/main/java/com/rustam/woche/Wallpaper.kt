package com.rustam.woche

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val left = 120f * s; val right = w - 30f * s
            val top = 860f * s; val bottom = h - 240f * s
            val t0 = 7; val t1 = 23
            val ph = (bottom - top) / (t1 - t0)
            val dn = arrayOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
            p.color = Color.parseColor("#E8EAF0"); p.textSize = 80f * s; p.typeface = Typeface.DEFAULT_BOLD
            p.typeface = Typeface.DEFAULT; p.strokeWidth = 1f
            for (hr in t0 until t1) {
                val y = top + (hr - t0) * ph
                p.color = Color.parseColor("#262A36"); c.drawLine(left, y, right, y, p)
                p.color = Color.parseColor("#8A91A5"); p.textSize = 28f * s; c.drawText("%02d".format(hr), 40f * s, y + 10f * s, p)
            }
            val colW = (right - left) / days.size
            val small = nDays > 3
            days.forEachIndexed { di, d ->
                val cx = left + di * colW
                if (nDays > 1) {
                    p.color = if (d == today) Color.parseColor("#FF5D62") else Color.parseColor("#8A91A5")
                    p.textSize = (if (small) 24f else 30f) * s; p.typeface = Typeface.DEFAULT_BOLD
                    c.drawText("${dn[d.dayOfWeek.value - 1]} ${d.dayOfMonth}", cx + 6f * s, top - 14f * s, p)
                    p.typeface = Typeface.DEFAULT
                }
                for (pl in placeDay(evs, d)) {
                    val a2 = (pl.fromMin / 60f).coerceAtLeast(t0.toFloat()); val b2 = (pl.toMin / 60f).coerceAtMost(t1.toFloat())
                    if (b2 <= a2) continue
                    val cw = colW / pl.cols
                    val r = RectF(cx + pl.col * cw + 2f, top + (a2 - t0) * ph + 1f, cx + (pl.col + 1) * cw - 2f, top + (b2 - t0) * ph - 1f)
                    p.color = pl.ev.cat.bg.toInt(); c.drawRoundRect(r, 10f * s, 10f * s, p)
                    p.color = pl.ev.cat.fg.toInt(); p.textSize = (if (small) 20f else 30f) * s; p.typeface = Typeface.DEFAULT_BOLD
                    c.save(); c.clipRect(r)
                    c.drawText(pl.ev.title, r.left + 8f * s, r.top + (if (small) 26f else 40f) * s, p)
                    c.restore(); p.typeface = Typeface.DEFAULT
                }
            }
            val n = LocalDateTime.now(); val m = n.hour + n.minute / 60f
            val ti = days.indexOf(today)
            if (ti >= 0 && m in t0.toFloat()..t1.toFloat()) {
                val y = top + (m - t0) * ph
                p.color = Color.parseColor("#FF5D62"); p.strokeWidth = 4f * s
                c.drawLine(left + ti * colW, y, left + (ti + 1) * colW, y, p); c.drawCircle(left + ti * colW, y, 9f * s, p)
            }
        }
    }
}
