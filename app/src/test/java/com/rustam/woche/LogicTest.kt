package com.rustam.woche

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LogicTest {
    private val z = ZoneId.of("Europe/Vienna")
    private val d = LocalDate.of(2026, 10, 26)
    private fun ev(id: Long, sh: Int, eh: Int, title: String = "x") =
        Ev(id, title, d.atTime(sh, 0).atZone(z).toInstant().toEpochMilli(), d.atTime(eh, 0).atZone(z).toInstant().toEpochMilli(), Cat.MISC)

    @Test fun classifies() {
        assertEquals(Cat.GYM, classify("🏋️ Gym A: upper body"))
        assertEquals(Cat.STUDY, classify("📚 Mathe: Thema 4 Relationen"))
        assertEquals(Cat.PEOPLE, classify("👨‍👩‍👧 Family time"))
        assertEquals(Cat.FH, classify("MACS1 Vorlesung"))
        assertEquals(Cat.WORK, classify("Dienst", "Arbeit"))
    }

    @Test fun overlapGetsTwoColumns() {
        val p = placeDay(listOf(ev(1, 9, 11), ev(2, 10, 12)), d, z)
        assertEquals(2, p.size)
        assertEquals(setOf(0, 1), p.map { it.col }.toSet())
        assertTrue(p.all { it.cols == 2 })
    }

    @Test fun separateEventsShareNoColumns() {
        val p = placeDay(listOf(ev(1, 9, 10), ev(2, 11, 12)), d, z)
        assertTrue(p.all { it.cols == 1 && it.col == 0 })
    }

    @Test fun eventEndingAtMidnightRunsTo24() {
        val e = Ev(1, "n", d.atTime(22, 0).atZone(z).toInstant().toEpochMilli(), d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli(), Cat.SLEEP)
        assertEquals(24 * 60, placeDay(listOf(e), d, z).single().toMin)
    }

    @Test fun mondayOfWorks() {
        assertEquals(LocalDate.of(2026, 10, 26), mondayOf(LocalDate.of(2026, 10, 25 + 1)))
        assertEquals(LocalDate.of(2026, 10, 26), mondayOf(LocalDate.of(2026, 11, 1)))
    }

    @Test fun shortensTitles() {
        assertEquals("Mathe: T2 Zahlenmengen", shortTitle("📚 Mathe: Thema 2 Zahlenmengen", true))
        assertEquals("Run 3.7 km", shortTitle("✓ Run 3.7 km", false))
        assertEquals("MACS1 ILV", shortTitle("MACS1-ILV", true))
        assertEquals("MACS1 ILV HS_A3.14", shortTitle("MACS1-ILV HS_A3.14 - BIF-1L2", true))
        assertEquals("PROZD LAB EDV_F4.24", shortTitle("PROZD-LAB EDV_F4.24 - BIF-1L2", false))
    }

    @Test fun themesAreComplete() {
        assertEquals(THEMES.size, THEMES.map { it.id }.toSet().size)
        THEMES.forEach { assertEquals(it.name, Cat.values().size, it.palette.size) }
    }

    @Test fun detectsSubjects() {
        assertEquals(Subject.MATHE, subjectOf("📚 Mathe: Thema 2"))
        assertEquals(Subject.DB, subjectOf("DMNMT-ILV EDV_A2.07"))
        assertEquals(Subject.C, subjectOf("PROZD-LAB EDV_F4.24"))
        assertEquals(Subject.WEB, subjectOf("Webtech: Nachbereitung PHP Basics"))
        assertEquals(Subject.SECURITY, subjectOf("Security+: Domain 1"))
        assertEquals(null, subjectOf("Pull Day"))
    }

    @Test fun statusRoundTrip() {
        assertEquals(Status.DONE, statusOf(withStatus("📚 Mathe: T2", Status.DONE)))
        assertEquals(Status.MISSED, statusOf(withStatus("✓ 📚 Mathe: T2", Status.MISSED)))
        assertEquals("📚 Mathe: T2", withStatus("✗ 📚 Mathe: T2", Status.OPEN))
        assertEquals(Status.OPEN, statusOf("Pull Day"))
    }

    @Test fun examsAndCountdown() {
        assertTrue(isExamTitle("🎯 Mathe Test 1"))
        assertTrue(isExamTitle("Minitest 6 (Elementare Funktionen) (MACS1) endet"))
        assertTrue(isExamTitle("Peer Review Zwischentest 2 Abgabebeginn"))
        assertFalse(isExamTitle("📚 Mathe: Mock test before Mathe test"))
        assertFalse(isExamTitle("MACS1-ILV HS_A3.14"))
        val today = LocalDate.of(2026, 10, 23)
        val now = today.atTime(20, 0).atZone(z).toInstant().toEpochMilli()
        val e1 = Ev(1, "🎯 Mathe Test 1", LocalDate.of(2026, 11, 2).atStartOfDay(z).toInstant().toEpochMilli(),
            LocalDate.of(2026, 11, 3).atStartOfDay(z).toInstant().toEpochMilli(), Cat.MISC, allDay = true)
        val e2 = Ev(2, "Minitest 6 endet", LocalDate.of(2026, 11, 9).atStartOfDay(z).toInstant().toEpochMilli(),
            LocalDate.of(2026, 11, 9).atStartOfDay(z).toInstant().toEpochMilli(), Cat.MISC)
        val cd = countdowns(listOf(e2, e1, e1.copy(id = 3)), now, today, z)
        assertEquals(2, cd.size)
        assertEquals("Mathe Test 1", cd[0].title)
        assertEquals(10L, cd[0].days)
        assertEquals("in 10 Tagen", daysLabel(cd[0].days))
    }

    private fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).atZone(z).toInstant().toEpochMilli()

    @Test fun makeUpStudyKeepsOneHourBuffer() {
        val fri = LocalDate.of(2026, 10, 23)
        val missed = Ev(1, "✗ 📚 Mathe: Thema 2", at(fri, 8), at(fri, 9), Cat.STUDY)
        val fh = Ev(2, "MACS1-ILV", at(fri, 10), at(fri, 12), Cat.FH)
        val slot = findMakeUpSlot(missed, listOf(missed, fh), at(fri, 9, 5), z)!!
        assertTrue(slot.first >= at(fri, 13))          // 1 h nach FH-Ende
        assertEquals(60L * 60_000L, slot.second - slot.first)
        assertEquals("📚 Mathe: Make-up Thema 2", makeUpTitle(missed))
    }

    @Test fun makeUpStudyStopsBeforeExam() {
        val mon = LocalDate.of(2026, 10, 26)
        val missed = Ev(1, "✗ 📚 Mathe: Thema 5", at(mon, 8), at(mon, 9), Cat.STUDY)
        val exam = Ev(3, "🎯 PRÜFUNG Mathe: Test 1", at(mon.plusDays(1), 8), at(mon.plusDays(1), 9), Cat.MISC)
        val blockers = (9..20).map { h -> Ev(100L + h, "Termin", at(mon, h), at(mon, h, 30), Cat.MISC) }
        assertNull(findMakeUpSlot(missed, listOf(exam) + blockers, at(mon, 9), z))
    }

    @Test fun makeUpGymRespectsRecovery() {
        val mon = LocalDate.of(2026, 10, 26)
        val missed = Ev(1, "✗ 🏋️ Gym A", at(mon, 13), at(mon, 15, 30), Cat.GYM)
        val wedGym = Ev(2, "🏋️ Gym B: HARD LEGS", at(mon.plusDays(2), 13), at(mon.plusDays(2), 15, 30), Cat.GYM)
        val slot = findMakeUpSlot(missed, listOf(missed, wedGym), at(mon, 16), z)!!
        val day = java.time.Instant.ofEpochMilli(slot.first).atZone(z).toLocalDate()
        assertTrue(day != mon.plusDays(1) && day != mon.plusDays(2) && day != mon.plusDays(3))   // nicht neben dem Beintag
        assertEquals(150L * 60_000L, slot.second - slot.first)
        assertTrue(!day.isAfter(LocalDate.of(2026, 11, 1)))                                        // nur diese Woche
    }

    @Test fun runNotAfterLegDay() {
        val mon = LocalDate.of(2026, 10, 26)
        val missed = Ev(1, "✗ 🏃 Run #1", at(mon, 15), at(mon, 16), Cat.GYM)
        val legs = Ev(2, "🏋️ Gym B: HARD LEGS", at(mon, 17), at(mon, 19, 30), Cat.GYM)
        val slot = findMakeUpSlot(missed, listOf(missed, legs), at(mon, 16, 30), z)!!
        val day = java.time.Instant.ofEpochMilli(slot.first).atZone(z).toLocalDate()
        assertTrue(day != mon.plusDays(1))
        assertEquals(MakeUpKind.RUN, makeUpKind(missed))
    }

    @Test fun importantBlocks() {
        assertTrue(isImportant("🎯 PRÜFUNG Mathe: Test 1"))
        assertTrue(isImportant("📚 Mathe: Make-up Thema 2"))
        assertTrue(isImportant("📚 Mathe: Mock test before Mathe test"))
        assertFalse(isImportant("📚 Mathe: Thema 4 Relationen"))
        assertFalse(isImportant("🏋️ Gym A"))
    }

    @Test fun sundayEveningShowsNextWeek() {
        val sun = java.time.LocalDateTime.of(2026, 11, 1, 18, 30)
        assertEquals(1, Paging.autoNextWeek(sun, 7))
        assertEquals(0, Paging.autoNextWeek(sun.withHour(17), 7))
        assertEquals(0, Paging.autoNextWeek(sun, 3))
        assertEquals(0, Paging.autoNextWeek(sun.minusDays(1), 7))
        assertEquals("Nächste Woche", Paging.label(1))
    }
}
