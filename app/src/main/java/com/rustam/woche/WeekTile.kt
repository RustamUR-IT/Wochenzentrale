package com.rustam.woche

import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Blättern im Hintergrund, das auch auf dem Sperrbildschirm funktioniert (über die Schnelleinstellungen). */
object Paging {
    private const val P = "woche"
    const val TILE_TTL_MS = 120_000L

    /** Seiten, die per Kachel weitergeblättert wurden (verfällt nach 2 Minuten). */
    fun tilePages(c: Context): Int {
        val sp = c.getSharedPreferences(P, 0)
        val ts = sp.getLong("tile_ts", 0L)
        return if (System.currentTimeMillis() - ts < TILE_TTL_MS) sp.getInt("tile_pages", 0) else 0
    }

    fun nextTilePage(c: Context): Int {
        val next = (tilePages(c) + 1) % 3
        c.getSharedPreferences(P, 0).edit().putInt("tile_pages", next).putLong("tile_ts", System.currentTimeMillis()).apply()
        return next
    }

    /** Sonntag ab 18 Uhr zeigt die Wochenansicht schon die nächste Woche. */
    fun autoNextWeek(now: LocalDateTime, nDays: Int): Int =
        if (nDays == 7 && now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 18) 1 else 0

    fun label(pages: Int): String = when (pages) {
        0 -> "Diese Woche"
        1 -> "Nächste Woche"
        else -> "Übernächste Woche"
    }
}

class WeekTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh(Paging.tilePages(this))
    }

    override fun onClick() {
        super.onClick()
        refresh(Paging.nextTilePage(this))
    }

    private fun refresh(pages: Int) {
        val t = qsTile ?: return
        t.label = "Woche"
        if (android.os.Build.VERSION.SDK_INT >= 29) t.subtitle = Paging.label(pages)
        t.icon = Icon.createWithResource(this, R.drawable.ic_week)
        t.state = if (pages == 0) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        t.updateTile()
    }
}
