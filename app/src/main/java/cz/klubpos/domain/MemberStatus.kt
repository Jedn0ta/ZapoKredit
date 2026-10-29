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
    val vip: Boolean = false,
    val createdAt: Long = 0,
    val page: Int = 0,
    val tileColor: Int = -1,
    val elder: Boolean = false,
    val avatar: String? = null,
    val jedi: Int = 0,
) {
    /** Newcomer = joined less than [days] days ago. */
    fun isNewbie(days: Int, now: Long = System.currentTimeMillis()) = days > 0 && now - createdAt < days * 86_400_000L

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
                vip = r.vip,
                page = r.page,
                tileColor = r.tileColor,
                elder = r.elder,
                avatar = r.avatar,
                jedi = r.jedi,
                createdAt = r.createdAt,
            )
        }
    }
}

object Credits {
    private val fmt = NumberFormat.getIntegerInstance(Locale("cs", "CZ"))
    /** The club's credit symbol (custom glyph in the app fonts at U+E000). 1 credit = 1 Kč. */
    const val SYMBOL = "\uE000"
    /** Shown as "1 250 <symbol>". */
    fun format(v: Long): String = fmt.format(v) + "\u00A0" + SYMBOL
}
