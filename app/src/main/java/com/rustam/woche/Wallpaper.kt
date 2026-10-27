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
        private var tapPages = 0          // per Tippen geblättert
        private var launcherPage = 0      // per Homescreen-Seite geblättert
        private val resetToToday = Runnable { if (tapPages != 0) { tapPages = 0; draw() } }
        private var visible = false
        // Kachel in den Schnelleinstellungen geändert -> sofort neu zeichnen, nach Ablauf zurück auf heute
        private val prefs = applicationContext.getSharedPreferences("woche", 0)
        private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "tile_ts" || key == "tile_pages" || key == "wall_days") {
                draw()
                handler.removeCallbacks(tileExpired)
                handler.postDelayed(tileExpired, Paging.TILE_TTL_MS + 1_000)
            }
        }
        private val tileExpired = Runnable { draw() }
        private val tick = object : Runnable { override fun run() { draw(); if (visible) handler.postDelayed(this, 60_000) } }
        override fun onVisibilityChanged(v: Boolean) { visible = v; handler.removeCallbacks(tick); if (v) tick.run() }
        override fun onSurfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) { draw() }
        private val observer = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { if (visible) draw() }
        }
        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            if (android.os.Build.VERSION.SDK_INT >= 30) setOffsetNotificationsEnabled(true)
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            try { contentResolver.registerContentObserver(android.provider.CalendarContract.CONTENT_URI, true, observer) } catch (_: Exception) {}
        }
        // Tippen auf freie Stelle des Startbildschirms: rechts vor, links zurück, Mitte = heute
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: android.os.Bundle?, resultRequested: Boolean): android.os.Bundle? {
            if (action == android.app.WallpaperManager.COMMAND_TAP && !isPreview) {
                val w = surfaceHolder.surfaceFrame.width().coerceAtLeast(1)
                tapPages = when {
                    x > w * 2 / 3 -> tapPages + 1
                    x < w / 3 -> tapPages - 1
                    else -> 0
                }
                handler.removeCallbacks(resetToToday)
                handler.postDelayed(resetToToday, 45_000)
                draw()
            }
            return null
        }

        // Wischen zwischen Homescreen-Seiten: jede Seite eine Woche weiter
        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            val page = if (xStep > 0f && xStep < 1f) Math.round(xOffset / xStep) else 0
            if (page != launcherPage) { launcherPage = page; draw() }
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick); handler.removeCallbacks(tileExpired)
            contentResolver.unregisterContentObserver(observer)
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        }

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
            val manual = tapPages + launcherPage + Paging.tilePages(applicationContext)
            val pages = if (manual != 0) manual else Paging.autoNextWeek(java.time.LocalDateTime.now(), nDays)
            val shift = (pages * nDays).toLong()
            val days = if (nDays == 7) { val m = mondayOf(today).plusDays(shift); (0..6).map { m.plusDays(it.toLong()) } }
                else (0 until nDays).map { today.plusDays(shift + it) }
            val evs = CalendarRepo.load(applicationContext, days.first(), days.last().plusDays(1))
            val skip = nDays > 1
            val (t0, t1) = Renderer.hourRange(evs, days, skip)
            val strip = Renderer.countdownStrip(c, 20f * s, 290f * s, w - 40f * s, CalendarRepo.countdowns(applicationContext), look, s)
            val y0 = 300f * s + strip
            Renderer.schedule(c, 0f, y0, w - 12f * s, h - 120f * s - y0, days, evs, t0, t1, look, s,
                compact = nDays > 3, headH = if (nDays > 1) 56f * s else 0f, skipSleep = skip)
        }
    }
}
