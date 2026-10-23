package com.rustam.woche

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

/** Liest alle auf dem Handy synchronisierten Kalender direkt, ohne Login und ohne Token. */
object CalendarRepo {
    fun hasPermission(c: Context) =
        c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun load(c: Context, from: LocalDate, toExclusive: LocalDate): List<Ev> {
        if (!hasPermission(c)) return emptyList()
        val zone = ZoneId.systemDefault()
        val b = dayStartMs(from, zone)
        val e = dayStartMs(toExclusive, zone)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, b)
            android.content.ContentUris.appendId(it, e)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.VISIBLE,
        )
        val out = mutableListOf<Ev>()
        c.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cur ->
            while (cur.moveToNext()) {
                if (cur.getInt(4) == 1 || cur.getInt(6) == 0) continue
                val title = cur.getString(1) ?: "(ohne Titel)"
                out.add(Ev(cur.getLong(0), title, cur.getLong(2), cur.getLong(3), classify(title, cur.getString(5) ?: "")))
            }
        }
        return out
    }
}
