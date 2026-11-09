package cz.klubpos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentsTest {

    // ---------- split: today vs older debt ----------

    @Test fun nothing_owed() = assertEquals(Payments.Split(0, 0), Payments.split(0, 0, 0, present = true))

    @Test fun only_today_while_present() = assertEquals(Payments.Split(120, 0), Payments.split(120, 120, 0, true))

    @Test fun futro_today_becomes_debt() = assertEquals(Payments.Split(0, 120), Payments.split(120, 120, 0, present = false))

    @Test fun old_debt_plus_today() = assertEquals(Payments.Split(80, 200), Payments.split(280, 80, 0, true))

    @Test fun payment_today_covers_today_first() = assertEquals(Payments.Split(0, 150), Payments.split(150, 80, 130, true).let {
        // 80 today paid, 50 extra went to the old debt (200 -> 150)
        Payments.Split(it.todayUnpaid, it.olderDebt) })

    @Test fun partial_payment_today() = assertEquals(Payments.Split(30, 200), Payments.split(230, 80, 50, true))

    @Test fun overpaid_is_credit_not_debt() = assertEquals(Payments.Split(0, 0), Payments.split(-50, 100, 150, true))

    @Test fun today_never_bigger_than_balance() = assertEquals(Payments.Split(40, 0), Payments.split(40, 100, 0, true))

    @Test fun paid_today_flag() {
        assertTrue(Payments.paidToday(100, 100))
        assertTrue(Payments.paidToday(100, 150))
        assertFalse(Payments.paidToday(100, 99))
        assertFalse(Payments.paidToday(0, 50))
    }

    // ---------- pay sheet options ----------

    @Test fun options_today_and_all() {
        val o = Payments.options(todayUnpaid = 80, balance = 280, orderTotal = null)
        assertEquals(listOf("TODAY", "ALL"), o.map { it.key })
        assertEquals("TODAY", Payments.defaultKey(o))
        assertEquals(80, Payments.amount(o, null, null))
    }

    @Test fun options_only_today_no_duplicate_all() {
        val o = Payments.options(80, 80, null)
        assertEquals(listOf("TODAY"), o.map { it.key })
    }

    @Test fun options_only_old_debt() {
        val o = Payments.options(0, 300, null)
        assertEquals(listOf("ALL"), o.map { it.key })
        assertEquals(300, Payments.amount(o, null, null))
    }

    @Test fun pay_now_preselects_order() {
        val o = Payments.options(todayUnpaid = 150, balance = 450, orderTotal = 60)
        assertEquals("ORDER", Payments.defaultKey(o))
        assertEquals(60, Payments.amount(o, null, null))
        assertEquals(450, Payments.amount(o, "ALL", null))
    }

    @Test fun pay_now_without_other_debt() {
        val o = Payments.options(todayUnpaid = 60, balance = 60, orderTotal = 60)
        assertEquals(listOf("ORDER", "TODAY"), o.map { it.key })
        assertEquals(60, Payments.amount(o, null, null))
    }

    @Test fun nothing_to_pay() {
        val o = Payments.options(0, 0, null)
        assertTrue(o.isEmpty())
        assertNull(Payments.defaultKey(o))
        assertEquals(0, Payments.amount(o, null, null))
    }

    @Test fun typed_amount_wins() {
        val o = Payments.options(80, 280, null)
        assertEquals(50, Payments.amount(o, "ALL", "50"))
        assertEquals(0, Payments.amount(o, "ALL", ""))
    }

    @Test fun cash_change() {
        assertEquals(20, Payments.change(200, 180))
        assertEquals(-30, Payments.change(150, 180))
    }

    // ---------- debt since ----------

    @Test fun debt_since_oldest_uncovered_charge() {
        // day1 100, day2 50, payment 100, day3 30 -> balance 80 -> covered from newest: 30 (day3) + 50 (day2) = 80
        val e = listOf(1L to 100L, 2L to 50L, 3L to -100L, 4L to 30L)
        assertEquals(2L, Payments.debtSince(e))
    }

    @Test fun debt_since_none_when_paid() = assertNull(Payments.debtSince(listOf(1L to 100L, 2L to -100L)))

    @Test fun debt_since_single() = assertEquals(5L, Payments.debtSince(listOf(5L to 70L)))
}
