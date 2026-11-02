package io.github.yorulmazsinan.kokpit.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RemindersTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun datesWithinThirtyDaysAndOverdue() {
        val r = Reminders(mapOf("muayene" to "2026-11-14", "sigorta" to "2026-10-30", "kasko" to "2027-05-01"))
        val due = dueReminders(r, today, null)
        assertEquals(listOf("Trafik sigortası 3 gün geçti (30.10.2026)", "Muayene 12 gün sonra (14.11.2026)"), due.map { it.text })
        assertEquals(listOf(true, false), due.map { it.overdue })
    }

    @Test
    fun oilByOdometer() {
        assertEquals("Yağ değişimine 650 km kaldı", dueReminders(Reminders(oilKm = 95_000), today, 94_350).single().text)
        assertTrue(dueReminders(Reminders(oilKm = 95_000), today, 90_000).isEmpty())
        assertTrue(dueReminders(Reminders(oilKm = 95_000), today, null).isEmpty())
        assertTrue(dueReminders(Reminders(oilKm = 95_000), today, 95_200).single().overdue)
    }

    @Test
    fun tyresBySeason() {
        assertEquals("Kışlık lastik zamanı", dueReminders(Reminders(tires = true), today, null).single().text)
        assertEquals("Yazlık lastik zamanı", dueReminders(Reminders(tires = true), LocalDate.of(2027, 4, 10), null).single().text)
        assertTrue(dueReminders(Reminders(tires = true), LocalDate.of(2027, 7, 1), null).isEmpty())
        assertTrue(dueReminders(Reminders(tires = false), today, null).isEmpty())
    }

    @Test
    fun panelChangesOnlyKnownKeys() {
        val r = applyReminders(Reminders(mapOf("kasko" to "2027-01-01")), """{"muayene":"2027-03-01","kasko":"","oilKm":"95000","tires":true}""")!!
        assertEquals(mapOf("muayene" to "2027-03-01"), r.dates)
        assertEquals(95_000, r.oilKm)
        assertTrue(r.tires)
        assertNull(applyReminders(Reminders(), """{"muayene":"yarın"}"""))
        assertNull(applyReminders(Reminders(), """{"egzoz":"2027-01-01"}"""))
        assertNull(applyReminders(Reminders(), """{"oilKm":"-5"}"""))
        assertNull(applyReminders(Reminders(), """{"tires":"evet"}"""))
        assertEquals(null, applyReminders(Reminders(oilKm = 5), """{"oilKm":""}""")!!.oilKm)
    }

    @Test
    fun unitDateField() {
        assertEquals("2026-11-14", dmyToIso("14.11.2026"))
        assertEquals("", dmyToIso(" "))
        assertNull(dmyToIso("31.02.2026"))
        assertNull(dmyToIso("2026-11-14"))
    }

    @Test
    fun driverFollowsConnectedPhone() {
        assertEquals("A", nextDriver("A", setOf("A", "B")))
        assertEquals("B", nextDriver("A", setOf("B")))
        assertEquals("A", nextDriver("A", emptySet()))
        assertNull(nextDriver(null, emptySet()))
        assertEquals("A", nextDriver(null, setOf("B", "A")))
    }
}
