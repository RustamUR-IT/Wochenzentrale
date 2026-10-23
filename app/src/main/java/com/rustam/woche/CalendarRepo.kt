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

    fun load(c: Context, from: LocalDate, toExclusive: LocalDate, includeAllDay: Boolean = false): List<Ev> {
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
            CalendarContract.Instances.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.CALENDAR_ID,
        )
        val out = mutableListOf<Ev>()
        c.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cur ->
            while (cur.moveToNext()) {
                val allDay = cur.getInt(4) == 1
                if ((allDay && !includeAllDay) || cur.getInt(6) == 0) continue
                val title = cur.getString(1) ?: "(ohne Titel)"
                val editable = cur.getInt(7) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                val recurring = !cur.getString(8).isNullOrBlank()
                out.add(Ev(cur.getLong(0), title, cur.getLong(2), cur.getLong(3), classify(baseTitle(title), cur.getString(5) ?: ""),
                    editable, recurring, allDay, cur.getLong(9)))
            }
        }
        return out
    }

    fun canWrite(c: Context) =
        c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Prüfungen und Fristen der nächsten 90 Tage (auch ganztägige Marker). */
    fun countdowns(c: Context): List<Countdown> {
        val today = LocalDate.now()
        return countdowns(load(c, today, today.plusDays(90), includeAllDay = true), System.currentTimeMillis(), today)
    }

    /** Sucht einen Nachhol-Platz in den nächsten 8 Tagen. */
    fun makeUpSlot(c: Context, ev: Ev): Pair<Long, Long>? {
        val today = LocalDate.now()
        val evs = load(c, today, today.plusDays(9), includeAllDay = false) +
            load(c, today, today.plusDays(20), includeAllDay = true).filter { it.allDay && isExamTitle(it.title) }
        return findMakeUpSlot(ev, evs, System.currentTimeMillis())
    }

    /** Legt den Nachholblock im selben Kalender an (mit [auto-plan], damit die Planung ihn kennt). */
    fun insertMakeUp(c: Context, ev: Ev, slot: Pair<Long, Long>): Boolean {
        if (!canWrite(c) || ev.calId == 0L) return false
        val day = java.time.Instant.ofEpochMilli(ev.startMs).atZone(ZoneId.systemDefault())
        val values = android.content.ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, ev.calId)
            put(CalendarContract.Events.TITLE, makeUpTitle(ev))
            put(CalendarContract.Events.DESCRIPTION, "Nachholen von \"${baseTitle(ev.title)}\" (verpasst am ${day.dayOfMonth}.${day.monthValue}.)\n[auto-plan]")
            put(CalendarContract.Events.DTSTART, slot.first)
            put(CalendarContract.Events.DTEND, slot.second)
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        }
        return c.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
    }

    /** Markiert einen Termin als erledigt/verpasst, indem das Titel-Präfix gesetzt wird. Google synchronisiert das zurück. */
    fun setStatus(c: Context, ev: Ev, st: Status): Boolean {
        if (!canWrite(c) || !ev.editable || ev.recurring) return false
        val values = android.content.ContentValues().apply { put(CalendarContract.Events.TITLE, withStatus(ev.title, st)) }
        val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ev.id)
        return c.contentResolver.update(uri, values, null, null) > 0
    }
}
