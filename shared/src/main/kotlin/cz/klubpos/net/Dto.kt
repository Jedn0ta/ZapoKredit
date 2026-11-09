package cz.klubpos.net

/** Data exchanged over the LAN. Maps are built/parsed by hand (see [Json]). */

data class MemberDto(
    val id: Long, val name: String, val nickname: String?, val inClub: Boolean, val visitedToday: Boolean,
    val todayUnpaid: Long, val olderDebt: Long, val balance: Long, val paidToday: Boolean,
    val isTable: Boolean, val tileColor: Int, val page: Int,
) {
    val label get() = nickname?.takeIf { it.isNotBlank() } ?: name
    fun toMap() = mapOf("id" to id, "name" to name, "nickname" to nickname, "inClub" to inClub, "visitedToday" to visitedToday,
        "todayUnpaid" to todayUnpaid, "olderDebt" to olderDebt, "balance" to balance, "paidToday" to paidToday,
        "isTable" to isTable, "tileColor" to tileColor, "page" to page)
    companion object {
        fun from(m: Map<String, Any?>) = MemberDto(m.long("id") ?: 0, m.str("name") ?: "", m.str("nickname"), m.bool("inClub"),
            m.bool("visitedToday"), m.long("todayUnpaid") ?: 0, m.long("olderDebt") ?: 0, m.long("balance") ?: 0, m.bool("paidToday"),
            m.bool("isTable"), m.int("tileColor") ?: -1, m.int("page") ?: 0)
    }
}

data class ProductDto(val id: Long, val name: String, val section: String, val sectionOrder: Int, val price: Long, val basePrice: Long) {
    fun toMap() = mapOf("id" to id, "name" to name, "section" to section, "sectionOrder" to sectionOrder, "price" to price, "basePrice" to basePrice)
    companion object {
        fun from(m: Map<String, Any?>) = ProductDto(m.long("id") ?: 0, m.str("name") ?: "", m.str("section") ?: "",
            m.int("sectionOrder") ?: 0, m.long("price") ?: 0, m.long("basePrice") ?: 0)
    }
}

data class Snapshot(val club: String, val serverTime: Long, val members: List<MemberDto>, val products: List<ProductDto>, val pages: List<String>) {
    fun toMap() = mapOf("club" to club, "serverTime" to serverTime, "members" to members.map { it.toMap() },
        "products" to products.map { it.toMap() }, "pages" to pages)
    companion object {
        fun from(m: Map<String, Any?>) = Snapshot(m.str("club") ?: "", m.long("serverTime") ?: 0,
            m["members"].arr().map { MemberDto.from(it.obj()) }, m["products"].arr().map { ProductDto.from(it.obj()) },
            m["pages"].arr().mapNotNull { it as? String })
    }
}

data class LineDto(val name: String, val qty: Double, val unitPrice: Long, val amount: Long, val entryIds: List<Long>) {
    fun toMap() = mapOf("name" to name, "qty" to qty, "unitPrice" to unitPrice, "amount" to amount, "entryIds" to entryIds)
    companion object {
        fun from(m: Map<String, Any?>) = LineDto(m.str("name") ?: "", m.dbl("qty") ?: 0.0, m.long("unitPrice") ?: 0,
            m.long("amount") ?: 0, m["entryIds"].arr().mapNotNull { (it as? Number)?.toLong() })
    }
}

data class PaymentDto(val amount: Long, val method: String, val at: Long) {
    fun toMap() = mapOf("amount" to amount, "method" to method, "at" to at)
    companion object { fun from(m: Map<String, Any?>) = PaymentDto(m.long("amount") ?: 0, m.str("method") ?: "", m.long("at") ?: 0) }
}

data class MemberDetailDto(val member: MemberDto, val lines: List<LineDto>, val payments: List<PaymentDto>, val debtSince: Long?) {
    fun toMap() = mapOf("member" to member.toMap(), "lines" to lines.map { it.toMap() }, "payments" to payments.map { it.toMap() }, "debtSince" to debtSince)
    companion object {
        fun from(m: Map<String, Any?>) = MemberDetailDto(MemberDto.from(m["member"].obj()), m["lines"].arr().map { LineDto.from(it.obj()) },
            m["payments"].arr().map { PaymentDto.from(it.obj()) }, m.long("debtSince"))
    }
}

data class OrderItemDto(val productId: Long?, val name: String, val qty: Double, val unitPrice: Long, val note: String? = null) {
    fun toMap() = mapOf("productId" to productId, "name" to name, "qty" to qty, "unitPrice" to unitPrice, "note" to note)
    companion object {
        fun from(m: Map<String, Any?>) = OrderItemDto(m.long("productId"), m.str("name") ?: "", m.dbl("qty") ?: 1.0, m.long("unitPrice") ?: 0, m.str("note"))
    }
}

/** An order to prepare at the bar. status: NEW → PREPARING → DONE (or CANCELLED). */
data class OrderDto(
    val id: Long, val target: String, val memberId: Long?, val isTable: Boolean, val items: List<OrderItemDto>,
    val note: String?, val waiter: String?, val status: String, val createdAt: Long, val updatedAt: Long,
) {
    val total get() = items.sumOf { Math.round(it.unitPrice * it.qty) }
    fun toMap() = mapOf("id" to id, "target" to target, "memberId" to memberId, "isTable" to isTable, "items" to items.map { it.toMap() },
        "note" to note, "waiter" to waiter, "status" to status, "createdAt" to createdAt, "updatedAt" to updatedAt)
    companion object {
        fun from(m: Map<String, Any?>) = OrderDto(m.long("id") ?: 0, m.str("target") ?: "", m.long("memberId"), m.bool("isTable"),
            m["items"].arr().map { OrderItemDto.from(it.obj()) }, m.str("note"), m.str("waiter"), m.str("status") ?: "NEW",
            m.long("createdAt") ?: 0, m.long("updatedAt") ?: 0)
        fun statusLabel(s: String) = when (s) { "NEW" -> "Nová"; "PREPARING" -> "Připravuje se"; "DONE" -> "Hotovo"; "CANCELLED" -> "Zrušeno"; else -> s }
    }
}

/** Typed calls of the protocol (client side). */
class LanApi(val client: LanClient) {
    fun snapshot() = Snapshot.from(client.call("snapshot"))
    fun member(id: Long) = MemberDetailDto.from(client.call("member", mapOf("memberId" to id))["detail"].obj())
    /** [table] = table number (creates "Stůl N" if needed). [prep] = put into the bar's preparation queue. */
    fun order(memberId: Long?, table: Int?, items: List<OrderItemDto>, note: String?, prep: Boolean, leave: Boolean = false): Long =
        client.call("order", mapOf("memberId" to memberId, "table" to table, "items" to items.map { it.toMap() },
            "note" to note, "prep" to prep, "leave" to leave)).long("orderId") ?: 0
    fun orders() = client.call("orders")["orders"].arr().map { OrderDto.from(it.obj()) }
    fun setOrderStatus(id: Long, status: String) { client.call("orderStatus", mapOf("id" to id, "status" to status)) }
    fun pay(memberId: Long, amount: Long, method: String, leave: Boolean) {
        client.call("pay", mapOf("memberId" to memberId, "amount" to amount, "method" to method, "leave" to leave))
    }
    fun visit(memberId: Long, open: Boolean) { client.call("visit", mapOf("memberId" to memberId, "open" to open)) }
    fun void(entryId: Long) { client.call("void", mapOf("entryId" to entryId)) }
    fun hello() = client.call("hello")
    /** Account of table [no] (created and marked occupied if needed). */
    fun table(no: Int): Long = client.call("table", mapOf("table" to no)).long("memberId") ?: 0
}
