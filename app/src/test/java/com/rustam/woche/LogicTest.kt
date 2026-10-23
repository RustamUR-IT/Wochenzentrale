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
}
