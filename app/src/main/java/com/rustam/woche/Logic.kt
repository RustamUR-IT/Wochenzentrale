package com.rustam.woche

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Cat(val label: String, val bg: Long, val fg: Long) {
    SLEEP("Schlaf", 0xFF232845, 0xFFAAB3F0),
    FH("FH", 0xFF1B2F55, 0xFF9CC3FF),
    WORK("Arbeit", 0xFF17372A, 0xFF8FE0AE),
    GYM("Training", 0xFF4A2419, 0xFFFFB39F),
    STUDY("Lernen", 0xFF4A3D10, 0xFFFFD966),
    PEOPLE("Familie/Freundin", 0xFF3A2547, 0xFFE0B4F5),
    PROJ("Projekt", 0xFF123F44, 0xFF7FE0E8),
    MISC("Sonstiges", 0xFF262A36, 0xFFB8BECE),
}

data class Ev(val id: Long, val title: String, val startMs: Long, val endMs: Long, val cat: Cat)

/** Ein Termin, auf einen Tag zugeschnitten und mit Spalte bei Überlappung. */
data class Placed(val ev: Ev, val fromMin: Int, val toMin: Int, val col: Int, val cols: Int)

private val sleepRe = Regex("🛏|😴|Bett|Schlaf|Nap|Wecker|Aufstehen", RegexOption.IGNORE_CASE)
private val gymRe = Regex("🏋|🏃|Gym|Lauf|Run|Stretch|🧘", RegexOption.IGNORE_CASE)
private val studyRe = Regex("📚|🧠|Anki|Lern|Mathe|Security|Prüfung|Study", RegexOption.IGNORE_CASE)
private val peopleRe = Regex("👨‍👩‍👧|Family|Familie|❤|💑|Freundin", RegexOption.IGNORE_CASE)
private val projRe = Regex("💻|Claude|Projekt|Coding", RegexOption.IGNORE_CASE)
private val fhRe = Regex("MACS1|DMNMT|INFRA|PROZD|WTBIF|KOKO|ENG1|BIF|FH ")
private val workRe = Regex("Arbeit|Dienst|Schicht|T-Systems|Raben", RegexOption.IGNORE_CASE)

fun classify(title: String, calendarName: String = ""): Cat = when {
    sleepRe.containsMatchIn(title) -> Cat.SLEEP
    gymRe.containsMatchIn(title) -> Cat.GYM
    studyRe.containsMatchIn(title) && !fhRe.containsMatchIn(title) -> Cat.STUDY
    peopleRe.containsMatchIn(title) -> Cat.PEOPLE
    projRe.containsMatchIn(title) -> Cat.PROJ
    fhRe.containsMatchIn(title) -> Cat.FH
    workRe.containsMatchIn(title) || workRe.containsMatchIn(calendarName) -> Cat.WORK
    else -> Cat.MISC
}

fun dayStartMs(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    day.atStartOfDay(zone).toInstant().toEpochMilli()

fun minutesOfDay(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    return t.hour * 60 + t.minute
}

/** Termine eines Tages, zugeschnitten auf 00:00 bis 24:00 und in Spalten verteilt. */
fun placeDay(events: List<Ev>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Placed> {
    val a = dayStartMs(day, zone)
    val b = dayStartMs(day.plusDays(1), zone)
    val items = events.filter { it.endMs > a && it.startMs < b }
        .sortedWith(compareBy<Ev> { it.startMs }.thenByDescending { it.endMs })
        .map { Triple(it, if (it.startMs <= a) 0 else minutesOfDay(it.startMs, zone),
            if (it.endMs >= b) 24 * 60 else minutesOfDay(it.endMs, zone).let { m -> if (m == 0) 24 * 60 else m }) }
    val out = mutableListOf<Placed>()
    var cluster = mutableListOf<Triple<Ev, Int, Int>>()
    var clusterEnd = 0
    fun flush() {
        val colEnds = mutableListOf<Int>()
        val cols = IntArray(cluster.size)
        cluster.forEachIndexed { i, (_, s, e) ->
            var c = colEnds.indexOfFirst { it <= s }
            if (c < 0) { c = colEnds.size; colEnds.add(e) } else colEnds[c] = e
            cols[i] = c
        }
        cluster.forEachIndexed { i, (ev, s, e) -> out.add(Placed(ev, s, e, cols[i], colEnds.size)) }
        cluster = mutableListOf()
    }
    for (item in items) {
        if (cluster.isNotEmpty() && item.second >= clusterEnd) flush()
        cluster.add(item)
        clusterEnd = if (cluster.size == 1) item.third else maxOf(clusterEnd, item.third)
    }
    if (cluster.isNotEmpty()) flush()
    return out
}

fun mondayOf(d: LocalDate): LocalDate = d.minusDays(((d.dayOfWeek.value + 6) % 7).toLong())

/** Kürzt Titel für schmale Spalten: Emoji, Haken und Füllwörter weg. */
fun shortTitle(title: String, compact: Boolean): String {
    var t = title.trim()
    t = t.replace(Regex("^[^\\p{L}\\p{N}]+"), "")
    // FH-Termine: "MACS1-ILV HS_A3.14 - BIF-1L2" -> "MACS1 ILV HS_A3.14" (Gruppe ist immer gleich)
    t = t.replace(Regex("\\s*-\\s*BIF-\\w+"), "")
        .replace(Regex("^([A-Z0-9]{3,})-(ILV|LAB|VO|UE|SE)\\b"), "$1 $2")
    if (compact) {
        t = t.replace(Regex("\\s*·\\s*"), " ")
            .replace("Mathematik für Computer Science", "Mathe")
            .replace("Mathe für Informatik", "Mathe")
            .replace("Thema ", "T")
            .replace("Prozedurale Sprachen", "C")
            .replace("Datenmanagement", "DB")
            .replace("Webtechnologien", "Web")
    }
    return t.ifBlank { title }
}

/** Fächer mit eigener Farbe. Erkennung über FH-Kürzel und typische Wörter im Titel. */
enum class Subject(val label: String, val color: Long, val pattern: Regex) {
    MATHE("Mathe", 0xFFFFB84D, Regex("MACS|Mathe", RegexOption.IGNORE_CASE)),
    DB("Datenmanagement", 0xFF4DD2FF, Regex("DMNMT|Datenmanagement|SQL|Datenbank", RegexOption.IGNORE_CASE)),
    INFRA("Infrastruktur", 0xFF7CFF8A, Regex("INFRA|Infrastruktur|Netzwerk", RegexOption.IGNORE_CASE)),
    C("C / Prozedural", 0xFFFF6B9E, Regex("PROZD|Prozedural|\\bC\\b", RegexOption.IGNORE_CASE)),
    WEB("Web", 0xFFB48CFF, Regex("WTBIF|Webtech|Web-|PHP|HTML", RegexOption.IGNORE_CASE)),
    KOKO("KoKo", 0xFFFF8F5A, Regex("KOKO|Kompetenz", RegexOption.IGNORE_CASE)),
    ENGLISCH("Englisch", 0xFF5AE6C8, Regex("ENG1|English|Englisch", RegexOption.IGNORE_CASE)),
    SECURITY("Security+", 0xFFFF5A5A, Regex("Security\\+|Sec\\+|CompTIA", RegexOption.IGNORE_CASE)),
}

fun subjectOf(title: String): Subject? = Subject.values().firstOrNull { it.pattern.containsMatchIn(title) }
