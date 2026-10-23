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

data class Ev(
    val id: Long, val title: String, val startMs: Long, val endMs: Long, val cat: Cat,
    val editable: Boolean = false, val recurring: Boolean = false, val allDay: Boolean = false,
    val calId: Long = 0,
)

enum class Status { OPEN, DONE, MISSED }

const val DONE_MARK = "✓ "
const val MISSED_MARK = "✗ "

fun statusOf(title: String): Status = when {
    title.startsWith("✓") -> Status.DONE
    title.startsWith("✗") -> Status.MISSED
    else -> Status.OPEN
}

/** Titel ohne Status-Präfix. */
fun baseTitle(title: String): String = title.removePrefix(DONE_MARK).removePrefix(MISSED_MARK).removePrefix("✓").removePrefix("✗").trimStart()

fun withStatus(title: String, st: Status): String = when (st) {
    Status.OPEN -> baseTitle(title)
    Status.DONE -> DONE_MARK + baseTitle(title)
    Status.MISSED -> MISSED_MARK + baseTitle(title)
}

private val examRe = Regex("(?i)prüfung|pruefung|klausur|\\btest\\b|abschlusstest|teilprüfung|zwischentest|minitest|abgabe|endet|deadline|peer review|🎯")
private val notExamRe = Regex("(?i)^📚|probetest|mock|vorbereitung|nachbereitung")

/** Prüfungen und Fristen: Kalendertitel mit Prüfungswörtern, aber keine eigenen Lernblöcke. */
fun isExamTitle(title: String): Boolean {
    val t = baseTitle(title)
    return examRe.containsMatchIn(t) && !notExamRe.containsMatchIn(t)
}

data class Countdown(val title: String, val days: Long, val startMs: Long)

/** Die nächsten Prüfungen/Fristen ab jetzt, jede nur einmal, nach Datum sortiert. */
fun countdowns(evs: List<Ev>, nowMs: Long, today: java.time.LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault(), max: Int = 3): List<Countdown> {
    val seen = mutableSetOf<String>()
    return evs.filter { isExamTitle(it.title) && it.endMs >= nowMs }
        .sortedBy { it.startMs }
        .mapNotNull { e ->
            val name = shortTitle(e.title.replace("🎯", "").trim(), false)
            if (!seen.add(name.lowercase())) return@mapNotNull null
            val d = java.time.Instant.ofEpochMilli(e.startMs).atZone(zone).toLocalDate()
            Countdown(name, java.time.temporal.ChronoUnit.DAYS.between(today, d), e.startMs)
        }.take(max)
}

fun daysLabel(days: Long): String = when {
    days <= 0L -> "heute"
    days == 1L -> "morgen"
    else -> "in $days Tagen"
}

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

// ---------------------------------------------------------------- Nachholen verpasster Blöcke

enum class MakeUpKind { STUDY, GYM, RUN, NONE }

private val runRe = Regex("🏃|\\brun\\b|lauf|zone ?2", RegexOption.IGNORE_CASE)
private val legRe = Regex("leg|bein", RegexOption.IGNORE_CASE)

fun makeUpKind(ev: Ev): MakeUpKind = when {
    ev.cat == Cat.STUDY -> MakeUpKind.STUDY
    ev.cat == Cat.GYM && runRe.containsMatchIn(ev.title) -> MakeUpKind.RUN
    ev.cat == Cat.GYM && !Regex("stretch|🧘", RegexOption.IGNORE_CASE).containsMatchIn(ev.title) -> MakeUpKind.GYM
    else -> MakeUpKind.NONE
}

/** Titel des Nachholblocks: "📚 Mathe: Make-up Thema 2" bzw. gleicher Titel fürs Training. */
fun makeUpTitle(ev: Ev): String {
    val base = baseTitle(ev.title)
    if (makeUpKind(ev) != MakeUpKind.STUDY || base.contains("Make-up")) return base
    val i = base.indexOf(": ")
    return if (i > 0) base.substring(0, i + 2) + "Make-up " + base.substring(i + 2) else "Make-up: $base"
}

/**
 * Sucht den frühesten passenden Platz für einen verpassten Block.
 * Regeln: 1 h Abstand zu allen anderen Terminen (Weg), nie in den Schlaf (Ende spätestens 21:00 bzw. 20:00 fürs Gym),
 * Lernen max. 4 h pro Tag und vor der zugehörigen Prüfung, Gym nur an einem Tag ohne harte Einheit am Vortag/Folgetag
 * und nicht vor Prüfung oder Lauf, Lauf nicht nach dem Beintag; Training nur in der laufenden Woche.
 * Gibt Start und Ende (ms) zurück oder null.
 */
fun findMakeUpSlot(
    missed: Ev, others: List<Ev>, nowMs: Long,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    bufferMin: Int = 60, stepMin: Int = 15,
): Pair<Long, Long>? {
    val kind = makeUpKind(missed)
    if (kind == MakeUpKind.NONE) return null
    val durMin = when (kind) {
        MakeUpKind.GYM -> 150
        MakeUpKind.RUN -> 60
        else -> ((missed.endMs - missed.startMs) / 60_000L).toInt().coerceIn(30, 120)
    }
    val busy = others.filter { it.id != missed.id && statusOf(it.title) != Status.MISSED && !isPointDeadline(it) }
    val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val weekEnd = mondayOf(today).plusDays(6)
    val subject = subjectOf(missed.title)
    val examLimit = busy.filter { isExamTitle(it.title) && it.startMs > nowMs && subject != null && subjectOf(it.title) == subject }
        .minOfOrNull { it.startMs }
    val lastDay = when (kind) {
        MakeUpKind.STUDY -> today.plusDays(7)
        else -> weekEnd
    }
    val (winStart, winEnd) = when (kind) {
        MakeUpKind.STUDY -> 7 * 60 + 30 to 21 * 60
        MakeUpKind.GYM -> 10 * 60 + 30 to 20 * 60
        MakeUpKind.RUN -> 8 * 60 + 30 to 20 * 60
        else -> 0 to 0
    }
    fun dayOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun on(d: java.time.LocalDate, pred: (Ev) -> Boolean) = busy.any { dayOf(it.startMs) == d && pred(it) }
    fun isGym(e: Ev) = makeUpKind(e) == MakeUpKind.GYM
    fun isRun(e: Ev) = makeUpKind(e) == MakeUpKind.RUN
    fun nightShift(e: Ev) = e.cat == Cat.WORK && (e.endMs - e.startMs) >= 8 * 3_600_000L &&
        java.time.Instant.ofEpochMilli(e.startMs).atZone(zone).hour >= 17

    var d = today
    while (!d.isAfter(lastDay)) {
        val dayOk = when (kind) {
            MakeUpKind.STUDY -> {
                val studied = busy.filter { dayOf(it.startMs) == d && it.cat == Cat.STUDY }.sumOf { (it.endMs - it.startMs) / 60_000L }
                studied + durMin <= 240 && !on(d, ::nightShift) && !on(d.minusDays(1), ::nightShift)
            }
            MakeUpKind.GYM -> !on(d, ::isGym) && !on(d.minusDays(1), ::isGym) && !on(d.plusDays(1), ::isGym) &&
                !on(d, ::isRun) && !on(d.plusDays(1), ::isRun) && !on(d.plusDays(1)) { isExamTitle(it.title) } &&
                !on(d, ::nightShift) && !on(d.minusDays(1), ::nightShift)
            MakeUpKind.RUN -> !on(d, ::isRun) && !on(d.minusDays(1)) { isGym(it) && legRe.containsMatchIn(it.title) } &&
                !on(d, ::nightShift) && !on(d.minusDays(1), ::nightShift)
            else -> false
        }
        if (dayOk) {
            var m = winStart
            while (m + durMin <= winEnd) {
                val s = d.atStartOfDay(zone).plusMinutes(m.toLong()).toInstant().toEpochMilli()
                val e = s + durMin * 60_000L
                val free = busy.none { it.startMs < e + bufferMin * 60_000L && it.endMs > s - bufferMin * 60_000L }
                if (s >= nowMs + 30 * 60_000L && free && (examLimit == null || e <= examLimit - 12 * 3_600_000L)) return s to e
                m += stepMin
            }
        }
        d = d.plusDays(1)
    }
    return null
}

/** Moodle-Fristen ohne Dauer blockieren keine Zeit. */
fun isPointDeadline(e: Ev): Boolean = e.endMs - e.startMs <= 60_000L && isExamTitle(e.title)
