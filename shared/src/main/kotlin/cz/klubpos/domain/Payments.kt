package cz.klubpos.domain

/**
 * Pure payment rules (no Android, no DB) – covered by unit tests.
 */
object Payments {

    /** Today's open tab vs. debt from before. */
    data class Split(val todayUnpaid: Long, val olderDebt: Long)

    /**
     * Payments made today cover today's spending first.
     * A member who is not present any more ("Na futro" / left without paying) has no open tab – it is all debt.
     */
    fun split(balance: Long, chargesToday: Long, paymentsToday: Long, present: Boolean): Split {
        val owed = balance.coerceAtLeast(0)
        val today = if (!present) 0L else (chargesToday - paymentsToday).coerceAtLeast(0).coerceAtMost(owed)
        return Split(today, (owed - today).coerceAtLeast(0))
    }

    /** True when the member had spending today and today's payments covered it. */
    fun paidToday(chargesToday: Long, paymentsToday: Long) = chargesToday > 0 && paymentsToday >= chargesToday

    data class Option(val key: String, val label: String, val amount: Long)

    /** Quick choices in the pay sheet, in display order. Only those that make sense are offered. */
    fun options(todayUnpaid: Long, balance: Long, orderTotal: Long?): List<Option> = buildList {
        if (orderTotal != null && orderTotal > 0) add(Option("ORDER", "Objednávka", orderTotal))
        if (todayUnpaid > 0) add(Option("TODAY", "Dnešek", todayUnpaid))
        if (balance > 0 && none { it.amount == balance }) add(Option("ALL", "Celý dluh", balance))
    }

    /** Pre-selected choice: the order when paying "hned", otherwise today's tab, otherwise the whole debt. */
    fun defaultKey(options: List<Option>): String? =
        options.firstOrNull { it.key == "ORDER" }?.key ?: options.firstOrNull { it.key == "TODAY" }?.key ?: options.firstOrNull()?.key

    /** Amount to pay: typed text wins, otherwise the selected (or default) choice. */
    fun amount(options: List<Option>, selectedKey: String?, typed: String?): Long =
        typed?.let { it.filter(Char::isDigit).toLongOrNull() ?: 0 }
            ?: (options.firstOrNull { it.key == (selectedKey ?: defaultKey(options)) }?.amount ?: 0)

    /** Change to give back for cash; negative = still missing. */
    fun change(received: Long, amount: Long) = received - amount

    /**
     * Since when a member owes money: walking charges from the newest, the one at which the sum reaches the balance
     * is the oldest charge not covered yet. Entries are (createdAt, amount); payments are negative.
     */
    fun debtSince(entries: List<Pair<Long, Long>>): Long? {
        val balance = entries.sumOf { it.second }
        if (balance <= 0) return null
        var acc = 0L
        val charges = entries.filter { it.second > 0 }.sortedByDescending { it.first }
        for (c in charges) { acc += c.second; if (acc >= balance) return c.first }
        return charges.lastOrNull()?.first
    }
}
