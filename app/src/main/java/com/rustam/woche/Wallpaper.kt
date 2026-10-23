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
            val evs = CalendarRepo.load(applicationContext, today, today.plusDays(1))
            val placed = placeDay(evs, today)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val left = 150f * s; val right = w - 50f * s
            val top = 470f * s; val bottom = h - 300f * s
            val t0 = 6; val t1 = 23
            val ph = (bottom - top) / (t1 - t0)
            p.color = Color.parseColor("#E8EAF0"); p.textSize = 80f * s; p.typeface = Typeface.DEFAULT_BOLD
            val dn = arrayOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
            c.drawText("${dn[today.dayOfWeek.value - 1]}, ${today.dayOfMonth}.${today.monthValue}.", 50f * s, 330f * s, p)
            p.typeface = Typeface.DEFAULT; p.strokeWidth = 1f
            for (hr in t0 until t1) {
                val y = top + (hr - t0) * ph
                p.color = Color.parseColor("#262A36"); c.drawLine(left, y, right, y, p)
                p.color = Color.parseColor("#8A91A5"); p.textSize = 28f * s; c.drawText("%02d".format(hr), 50f * s, y + 10f * s, p)
            }
            for (pl in placed) {
                val a = (pl.fromMin / 60f).coerceAtLeast(t0.toFloat()); val b = (pl.toMin / 60f).coerceAtMost(t1.toFloat())
                if (b <= a) continue
                val cw = (right - left) / pl.cols
                val r = RectF(left + pl.col * cw + 3f, top + (a - t0) * ph + 1f, left + (pl.col + 1) * cw - 3f, top + (b - t0) * ph - 1f)
                p.color = pl.ev.cat.bg.toInt(); c.drawRoundRect(r, 14f * s, 14f * s, p)
                p.color = pl.ev.cat.fg.toInt(); p.textSize = 32f * s; p.typeface = Typeface.DEFAULT_BOLD
                c.save(); c.clipRect(r)
                c.drawText(pl.ev.title, r.left + 18f * s, r.top + 44f * s, p)
                c.restore(); p.typeface = Typeface.DEFAULT
            }
            val n = LocalDateTime.now(); val m = n.hour + n.minute / 60f
            if (m in t0.toFloat()..t1.toFloat()) {
                val y = top + (m - t0) * ph
                p.color = Color.parseColor("#FF5D62"); p.strokeWidth = 4f * s
                c.drawLine(left, y, right, y, p); c.drawCircle(left, y, 9f * s, p)
            }
        }
    }
}
