package com.rustam.woche

import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.time.LocalDate

/** Live-Hintergrundbild im gewählten Stil: 1 Tag, 3 Tage oder Woche, aktualisiert jede Minute. */
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
            val look = LookStore.load(applicationContext)
            Renderer.background(c, w, h, look, s)
            val today = LocalDate.now()
            val nDays = applicationContext.getSharedPreferences("woche", 0).getInt("wall_days", 1).coerceIn(1, 7)
            val days = if (nDays == 7) { val m = mondayOf(today); (0..6).map { m.plusDays(it.toLong()) } }
                else (0 until nDays).map { today.plusDays(it.toLong()) }
            val evs = CalendarRepo.load(applicationContext, days.first(), days.last().plusDays(1))
            val skip = nDays > 1
            val (t0, t1) = Renderer.hourRange(evs, days, skip)
            val y0 = 300f * s
            Renderer.schedule(c, 0f, y0, w - 12f * s, h - 120f * s - y0, days, evs, t0, t1, look, s,
                compact = nDays > 3, headH = if (nDays > 1) 56f * s else 0f, skipSleep = skip)
        }
    }
}
