package cz.klubpos.domain

import cz.klubpos.data.db.dao.MemberSummaryRow
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * A club night runs past midnight, so "today" means the business day,
 * which starts at [CUTOFF_HOUR] (e.g. a drink at 02:00 still belongs to yesterday evening).
 */
object BusinessDay {
    const val CUTOFF_HOUR = 6

    fun startMillis(now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val date: LocalDate = if (now.hour < CUTOFF_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()
        return date.atTime(CUTOFF_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    }
}

/**
 * Everything the medallion needs to show.
 *
 * Payments made today are first applied to today's spending, so we can tell apart:
 *  - today's spend still unpaid  ([todayUnpaid])
 *  - older debt from previous days ([olderDebt])
 *  - "paid today" = had spending today and today's payments covered it ([paidToday])
 */
data class MemberStatus(
    val memberId: Long,
    val name: String,
    val nickname: String?,
    val photoId: String?,
    val inClub: Boolean,
    val balance: Long,
    val todaySpend: Long,
    val todayUnpaid: Long,
    val olderDebt: Long,
    val paidToday: Boolean,
) {
    val hasDebt get() = balance > 0
    val hasCredit get() = balance < 0       // overpaid -> prepaid credit

    /** Single dominant state for colour coding of the medallion. */
    val headline: Headline
        get() = when {
            todayUnpaid > 0 -> Headline.OPEN_TAB_TODAY
            paidToday && olderDebt > 0 -> Headline.PAID_TODAY_OLD_DEBT
            paidToday -> Headline.PAID_TODAY
            olderDebt > 0 -> Headline.OLD_DEBT
            else -> Headline.CLEAR
        }

    enum class Headline(val label: String) {
        OPEN_TAB_TODAY("Dnešní útrata"),
        PAID_TODAY_OLD_DEBT("Dnes zaplaceno · starý dluh"),
        PAID_TODAY("Dnes zaplaceno"),
        OLD_DEBT("Dluh z minula"),
        CLEAR("Bez dluhu"),
    }

    companion object {
        fun from(r: MemberSummaryRow): MemberStatus {
            val todayUnpaid = (r.chargesToday - r.paymentsToday).coerceAtLeast(0)
                .coerceAtMost(r.balance.coerceAtLeast(0))
            val olderDebt = (r.balance - todayUnpaid).coerceAtLeast(0)
            return MemberStatus(
                memberId = r.id,
                name = r.name,
                nickname = r.nickname,
                photoId = r.photoId,
                inClub = r.openVisitId != null,
                balance = r.balance,
                todaySpend = r.chargesToday,
                todayUnpaid = todayUnpaid,
                olderDebt = olderDebt,
                paidToday = r.chargesToday > 0 && r.paymentsToday >= r.chargesToday,
            )
        }
    }
}

object Credits {
    private val fmt = NumberFormat.getIntegerInstance(Locale("cs", "CZ"))
    /** 1 credit = 1 Kč. Shown as "1 250 kr". */
    fun format(v: Long): String = fmt.format(v) + " kr"
}
