package cz.klubpos.lan

import cz.klubpos.AppServices
import cz.klubpos.data.db.entity.EntryType
import cz.klubpos.data.db.entity.PrepOrder
import cz.klubpos.data.repo.ClubRepository
import cz.klubpos.domain.BusinessDay
import cz.klubpos.domain.displayName
import cz.klubpos.domain.kindEnum
import cz.klubpos.net.Json
import cz.klubpos.net.Lan
import cz.klubpos.net.LineDto
import cz.klubpos.net.MemberDetailDto
import cz.klubpos.net.MemberDto
import cz.klubpos.net.OrderDto
import cz.klubpos.net.OrderItemDto
import cz.klubpos.net.PaymentDto
import cz.klubpos.net.ProductDto
import cz.klubpos.net.Snapshot
import cz.klubpos.net.arr
import cz.klubpos.net.bool
import cz.klubpos.net.int
import cz.klubpos.net.long
import cz.klubpos.net.obj
import cz.klubpos.net.str
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/** Settings of the LAN link (encrypted). */
data class LanConfig(val enabled: Boolean = false, val key: String = "")

/**
 * The register as a server for waiter phones and the PC register.
 * HTTP on port 8770 (body AES-GCM encrypted with the club code), UDP 8771 answers discovery.
 */
class LanHub(private val app: AppServices) {
    private val prefs = cz.klubpos.platform.securePrefs("lan_config")
    private val _config = MutableStateFlow(LanConfig(prefs.getBoolean("enabled", false),
        prefs.getString("key", null) ?: Lan.newKey().also { prefs.edit().putString("key", it).apply() }))
    val config: StateFlow<LanConfig> = _config
    /** device name → last request time (shown in Settings). */
    val devices = MutableStateFlow<Map<String, Long>>(emptyMap())
    val running = MutableStateFlow(false)

    private var http: Http? = null
    private var udp: Thread? = null
    @Volatile private var udpSocket: DatagramSocket? = null
    private var multicast: AutoCloseable? = null

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("enabled", on).apply()
        _config.value = _config.value.copy(enabled = on)
        cz.klubpos.platform.lanBackground(on, { start() }, { stop() })
    }

    /** New code: every phone/PC must scan it again (use when a phone gets lost). */
    fun newKey() {
        val k = Lan.newKey()
        prefs.edit().putString("key", k).apply()
        _config.value = _config.value.copy(key = k)
        if (running.value) { stop(); start() }
    }

    fun code(): Lan.Code? = Lan.localIPv4()?.let { Lan.Code(it, Lan.PORT, _config.value.key) }

    @Synchronized fun start() {
        if (http != null) return
        val h = Http(); runCatching { h.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }.onFailure { return }
        http = h
        multicast = cz.klubpos.platform.multicastLock()
        udp = Thread {
            runCatching {
                DatagramSocket(null).use { s ->
                    s.reuseAddress = true; s.broadcast = true; s.bind(InetSocketAddress(Lan.DISCOVERY_PORT)); udpSocket = s
                    val buf = ByteArray(64)
                    while (!Thread.currentThread().isInterrupted) {
                        val p = DatagramPacket(buf, buf.size); s.receive(p)
                        if (String(p.data, 0, p.length) == "ZK?") {
                            val r = Lan.discoveryReply(Lan.PORT, Lan.keyId(_config.value.key), app.settings.current.clubName).toByteArray()
                            s.send(DatagramPacket(r, r.size, p.address, p.port))
                        }
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        running.value = true
    }

    @Synchronized fun stop() {
        http?.stop(); http = null
        udp?.interrupt(); udpSocket?.close(); udp = null
        runCatching { multicast?.close() }
        running.value = false
    }

    private val repo: ClubRepository get() = app.repository

    private inner class Http : NanoHTTPD(Lan.PORT) {
        override fun serve(session: IHTTPSession): Response {
            if (session.method != Method.POST || session.uri != Lan.PATH)
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "ZapoKredit")
            val len = session.headers["content-length"]?.toIntOrNull() ?: 0
            if (len <= 0 || len > 512_000) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "size")
            val body = ByteArray(len).also { b -> var r = 0; while (r < len) { val n = session.inputStream.read(b, r, len - r); if (n < 0) break; r += n } }
            val crypto = Lan.Crypto(_config.value.key)
            val plain = crypto.open(String(body, Charsets.UTF_8))
                ?: return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "key")
            val req = Json.parse(plain).obj()
            val ts = req.long("ts") ?: 0
            val res: Map<String, Any?> = if (kotlin.math.abs(System.currentTimeMillis() - ts) > Lan.MAX_SKEW_MS)
                mapOf("ok" to false, "error" to "Rozdílný čas na zařízení a pokladně – zkontroluj hodiny")
            else try {
                req.str("device")?.let { d -> devices.value = devices.value + (d to System.currentTimeMillis()) }
                runBlocking { handle(req) } + ("ok" to true)
            } catch (e: Exception) {
                mapOf("ok" to false, "error" to (e.message ?: e.javaClass.simpleName))
            }
            return newFixedLengthResponse(Response.Status.OK, "text/plain", crypto.seal(Json.write(res)))
        }
    }

    // ---------------- operations ----------------

    private suspend fun handle(r: Map<String, Any?>): Map<String, Any?> = when (r.str("op")) {
        "hello" -> mapOf("club" to app.settings.current.clubName, "version" to Lan.VERSION)
        "snapshot" -> snapshot().toMap()
        "member" -> mapOf("detail" to memberDetail(r.long("memberId") ?: error("memberId")).toMap())
        "table" -> mapOf("memberId" to repo.tableMember(r.int("table") ?: error("Číslo stolu")))
        "order" -> mapOf("orderId" to order(r))
        "orders" -> mapOf("orders" to repo.prepOrdersNow(System.currentTimeMillis() - 3 * 3600_000L).map { it.toDto().toMap() })
        "orderStatus" -> { repo.setPrepStatus(r.long("id") ?: error("id"), r.str("status") ?: "DONE"); emptyMap() }
        "pay" -> {
            val amount = r.long("amount") ?: 0
            require(amount > 0) { "Částka musí být kladná" }
            repo.pay(r.long("memberId") ?: error("memberId"), amount, r.bool("leave"), r.str("method") ?: "CASH"); emptyMap()
        }
        "visit" -> {
            val id = r.long("memberId") ?: error("memberId")
            if (r.bool("open")) repo.openVisit(id) else repo.closeVisit(id); emptyMap()
        }
        "void" -> { repo.void(r.long("entryId") ?: error("entryId")); emptyMap() }
        else -> error("Neznámá operace")
    }

    private suspend fun snapshot(): Snapshot {
        val statuses = repo.memberStatuses(BusinessDay.startMillis()).first()
        val products = repo.activeProducts().first()
        return Snapshot(app.settings.current.clubName, System.currentTimeMillis(),
            statuses.map { s ->
                MemberDto(s.memberId, s.name, s.nickname, s.inClub, s.visitedToday, s.todayUnpaid, s.olderDebt, s.balance,
                    s.paidToday, s.isTable, s.tileColor, s.page)
            },
            products.map { p -> ProductDto(p.id, p.displayName, p.kindEnum.label, p.kindEnum.ordinal, p.currentPrice(), p.price) },
            app.settings.current.memberPages)
    }

    private suspend fun memberDetail(id: Long): MemberDetailDto {
        val s = repo.memberStatuses(BusinessDay.startMillis()).first().firstOrNull { it.memberId == id } ?: error("Člen nenalezen")
        val dayStart = BusinessDay.startMillis()
        val ledger = repo.ledgerOnce(id)
        val voided = ledger.mapNotNull { it.voidsEntryId }.toSet()
        val today = ledger.filter { it.createdAt >= dayStart && it.voidsEntryId == null && it.id !in voided }
        val lines = today.filter { it.type == EntryType.CHARGE }.groupBy { "${it.productId ?: it.description}|${it.unitPrice}" }
            .map { (_, es) -> LineDto(es.first().description, es.sumOf { it.quantity }, es.first().unitPrice, es.sumOf { it.amount },
                es.sortedByDescending { it.createdAt }.map { it.id }) }
        val pays = today.filter { it.type == EntryType.PAYMENT }.map { PaymentDto(-it.amount, it.method ?: "CASH", it.createdAt) }
        val since = cz.klubpos.domain.Payments.debtSince(ledger.filter { it.voidsEntryId == null && it.id !in voided }.map { it.createdAt to it.amount })
        return MemberDetailDto(MemberDto(s.memberId, s.name, s.nickname, s.inClub, s.visitedToday, s.todayUnpaid, s.olderDebt,
            s.balance, s.paidToday, s.isTable, s.tileColor, s.page), lines, pays, since)
    }

    /** Charges the items to the member/table account; with prep=true also puts the order into the bar queue. */
    private suspend fun order(r: Map<String, Any?>): Long {
        val table = r.int("table")
        val memberId = r.long("memberId") ?: table?.let { repo.tableMember(it) } ?: error("Vyber člena nebo stůl")
        val member = repo.memberOnce2(memberId) ?: error("Člen nenalezen")
        val products = repo.activeProducts().first().associateBy { it.id }
        val items = r["items"].arr().map { OrderItemDto.from(it.obj()) }.filter { it.qty > 0 }
        if (items.isNotEmpty()) {
            repo.chargeCart(memberId, items.map { i ->
                val p = i.productId?.let { products[it] }
                // products are charged at the register's current price, custom items at the given price
                ClubRepository.CartItem(p?.id, p?.displayName ?: i.name, p?.currentPrice() ?: i.unitPrice, i.qty)
            })
        } else repo.openVisit(memberId)
        if (r.bool("leave")) repo.closeVisit(memberId)
        if (!r.bool("prep") || items.isEmpty()) return 0
        val label = member.nickname?.takeIf { it.isNotBlank() } ?: member.name
        return repo.addPrepOrder(PrepOrder(memberId = memberId, target = label, isTable = member.isTable,
            itemsJson = Json.write(items.map { i -> (products[i.productId ?: -1]?.let { i.copy(name = it.displayName, unitPrice = it.currentPrice()) } ?: i).toMap() }),
            note = r.str("note")?.takeIf { it.isNotBlank() }, waiter = r.str("device")))
    }
}

fun PrepOrder.toDto() = OrderDto(id, target, memberId, isTable, Json.parse(itemsJson).arr().map { OrderItemDto.from(it.obj()) },
    note, waiter, status, createdAt, updatedAt)

