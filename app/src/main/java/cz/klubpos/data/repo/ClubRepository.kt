package cz.klubpos.data.repo

import androidx.room.withTransaction
import cz.klubpos.core.crypto.ImageStore
import cz.klubpos.data.db.AppDatabase
import cz.klubpos.data.db.entity.EntryType
import cz.klubpos.data.db.entity.LedgerEntry
import cz.klubpos.data.db.entity.Member
import cz.klubpos.data.db.entity.Product
import cz.klubpos.data.db.entity.ShoppingItem
import cz.klubpos.data.db.entity.StockMovement
import cz.klubpos.data.db.entity.Visit
import cz.klubpos.domain.BusinessDay
import cz.klubpos.domain.MemberStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import cz.klubpos.data.db.entity.Bartender
import cz.klubpos.core.crypto.Pin
import kotlin.math.ceil

class ClubRepository(private val db: AppDatabase, val images: ImageStore) {

    // ---------- Bartenders / session ----------

    private val _bartender = kotlinx.coroutines.flow.MutableStateFlow<Bartender?>(null)
    /** Logged-in bartender; every ledger entry is stamped with them. */
    val bartender: kotlinx.coroutines.flow.StateFlow<Bartender?> = _bartender

    private suspend fun insertEntry(e: LedgerEntry): Long =
        db.ledger().insert(e.copy(bartenderId = _bartender.value?.id ?: e.bartenderId))

    fun bartenders() = db.bartenders().observeActive()
    fun allBartenders() = db.bartenders().observeAll()
    fun bartenderSales(since: Long) = db.bartenders().observeSales(since)

    suspend fun addBartender(name: String, pin: String): Long {
        val (hash, salt) = Pin.hash(pin)
        val count = db.bartenders().observeAll().first().size
        return db.bartenders().insert(Bartender(name = name.trim(), pinHash = hash, pinSalt = salt, colorIndex = count))
    }

    suspend fun setBartenderPin(b: Bartender, pin: String) {
        val (hash, salt) = Pin.hash(pin)
        db.bartenders().update(b.copy(pinHash = hash, pinSalt = salt))
    }

    suspend fun setBartenderActive(b: Bartender, active: Boolean) {
        db.bartenders().update(b.copy(active = active))
        if (!active && _bartender.value?.id == b.id) logout()
    }

    /** Returns true and starts the session when the PIN matches. */
    suspend fun login(b: Bartender, pin: String): Boolean {
        val fresh = db.bartenders().get(b.id) ?: return false
        if (!fresh.active || !Pin.verify(pin, fresh.pinHash, fresh.pinSalt)) return false
        _bartender.value = fresh
        return true
    }

    fun logout() { _bartender.value = null }

    // ---------- Members ----------

    /** [dayStart] = start of the current business day; the ViewModel refreshes it when the day rolls over. */
    fun memberStatuses(dayStart: Long = BusinessDay.startMillis()): Flow<List<MemberStatus>> =
        db.members().observeSummaries(dayStart).map { rows -> rows.map(MemberStatus::from) }

    fun member(id: Long) = db.members().observe(id)
    fun ledger(memberId: Long) = db.ledger().observeForMember(memberId)

    suspend fun addMember(name: String, nickname: String?, page: Int = 0): Long =
        db.members().insert(Member(name = name.trim(), nickname = nickname?.trim()?.ifBlank { null },
            page = page, sortOrder = db.members().maxOrder(page) + 1))

    /** Move a member to another card (placed at its end). */
    suspend fun setMemberPage(memberId: Long, page: Int) = db.members().setPage(memberId, page, db.members().maxOrder(page) + 1)

    /** A card was deleted: its members go to the first card, later cards shift left. */
    suspend fun removeMemberPage(index: Int) = db.withTransaction {
        db.members().movePage(index, 0)
        db.members().shiftPagesAfter(index)
    }

    suspend fun updateMember(m: Member) = db.members().update(m)

    suspend fun memberOnce(id: Long) = db.members().get(id)

    suspend fun removeMemberPhoto(memberId: Long) {
        val m = db.members().get(memberId) ?: return
        images.delete(m.photoId)
        db.members().update(m.copy(photoId = null))
    }

    suspend fun setVip(memberId: Long, vip: Boolean) { db.members().get(memberId)?.let { db.members().update(it.copy(vip = vip)) } }

    /** Persist drag & drop order of the main screen. */
    suspend fun saveMemberOrder(ids: List<Long>) = db.withTransaction { ids.forEachIndexed { i, id -> db.members().setOrder(id, i) } }
    suspend fun saveProductOrder(ids: List<Long>) = db.withTransaction { ids.forEachIndexed { i, id -> db.products().setOrder(id, i) } }

    suspend fun setMemberPhoto(memberId: Long, raw: ByteArray) {
        val m = db.members().get(memberId) ?: return
        val newId = images.save(raw)
        db.members().update(m.copy(photoId = newId))
        images.delete(m.photoId)
    }

    // ---------- Visits ("otevřený účet") ----------

    suspend fun openVisit(memberId: Long): Long =
        db.visits().openFor(memberId)?.id ?: db.visits().insert(Visit(memberId = memberId))

    suspend fun closeVisit(memberId: Long) {
        db.visits().openFor(memberId)?.let { db.visits().close(it.id, System.currentTimeMillis()) }
    }

    /** End of night: everybody leaves (debts stay on the tab). */
    suspend fun closeAllVisits() = db.visits().closeAll(System.currentTimeMillis())

    // ---------- Tab ----------

    /** Write a product to the member's tab. Opens a visit automatically and lowers stock. */
    suspend fun chargeProduct(memberId: Long, productId: Long, qty: Double = 1.0, priceOverride: Long? = null) =
        db.withTransaction {
            val p = db.products().get(productId) ?: error("Produkt nenalezen")
            val unit = priceOverride ?: p.currentPrice()
            // "do vyprodání": count down the pieces sold at the sale price
            if (p.saleLeft != null && p.saleActive() && !p.promoActive() && unit == p.salePrice) {
                db.products().update(p.copy(saleLeft = (p.saleLeft - qty).coerceAtLeast(0.0)))
            }
            val visitId = openVisit(memberId)
            val entryId = insertEntry(
                LedgerEntry(
                    memberId = memberId, type = EntryType.CHARGE, description = p.name,
                    productId = p.id, quantity = qty, unitPrice = unit,
                    amount = Math.round(unit * qty), visitId = visitId,
                )
            )
            if (p.trackStock) {
                db.products().adjustStock(p.id, -qty)
                db.stock().insert(StockMovement(productId = p.id, delta = -qty, reason = "SALE", ledgerEntryId = entryId))
                suggestIfLow(p.id)
            }
            entryId
        }

    /** Free-form item: bartender types a description and a price. */
    suspend fun chargeCustom(memberId: Long, description: String, amount: Long) = db.withTransaction {
        require(amount > 0) { "Částka musí být kladná" }
        val visitId = openVisit(memberId)
        insertEntry(
            LedgerEntry(
                memberId = memberId, type = EntryType.CHARGE,
                description = description.ifBlank { "Vlastní položka" },
                unitPrice = amount, amount = amount, visitId = visitId,
            )
        )
    }

    suspend fun pay(memberId: Long, amount: Long, closeVisitAfter: Boolean, method: String = "CASH") = db.withTransaction {
        require(amount > 0) { "Částka musí být kladná" }
        insertEntry(
            LedgerEntry(
                memberId = memberId, type = EntryType.PAYMENT,
                description = if (method == "QR") "Platba QR" else "Platba hotově",
                unitPrice = amount, amount = -amount, method = method,
                visitId = db.visits().openFor(memberId)?.id,
            )
        )
        if (closeVisitAfter) closeVisit(memberId)
    }

    /** One line of a not-yet-written order. productId == null → custom amount. */
    data class CartItem(val productId: Long?, val description: String, val unitPrice: Long, val qty: Double)

    /** Write the whole order to the member's tab at once ("na futro"). */
    suspend fun chargeCart(memberId: Long, items: List<CartItem>) = db.withTransaction {
        items.forEach { i ->
            if (i.productId != null) {
                val p = db.products().get(i.productId)
                val free = p?.freeOf(i.qty) ?: 0
                if (i.qty - free > 0) chargeProduct(memberId, i.productId, i.qty - free, i.unitPrice)
                // free pieces of an X+Y deal: written at 0 so stock / kegs stay correct
                if (free > 0) chargeProduct(memberId, i.productId, free.toDouble(), 0)
            } else chargeCustom(memberId, i.description, Math.round(i.unitPrice * i.qty))
        }
    }

    suspend fun lastChargeEntry(memberId: Long): LedgerEntry? =
        db.ledger().lastCharge(memberId)?.takeIf { db.ledger().voidCount(it.id) == 0 }

    /** Round: the same product written to several members at once. */
    suspend fun chargeRound(memberIds: Collection<Long>, productId: Long) = db.withTransaction {
        memberIds.forEach { chargeProduct(it, productId) }
    }

    /** "To samé" – repeat the member's last (non-voided) charge. Returns its description or null. */
    suspend fun repeatLast(memberId: Long): String? {
        val e = db.ledger().lastCharge(memberId) ?: return null
        if (db.ledger().voidCount(e.id) > 0) return null
        if (e.productId != null) chargeProduct(memberId, e.productId, e.quantity, e.unitPrice)
        else chargeCustom(memberId, e.description, e.amount)
        return e.description
    }

    suspend fun lastChargeLabel(memberId: Long): String? =
        db.ledger().lastCharge(memberId)?.takeIf { db.ledger().voidCount(it.id) == 0 }?.description

    /** Move one charge to another member's tab (storno here, same item there). */
    suspend fun transfer(entryId: Long, toMemberId: Long) = db.withTransaction {
        val e = db.ledger().get(entryId) ?: return@withTransaction
        if (e.type != EntryType.CHARGE || e.memberId == toMemberId) return@withTransaction
        insertEntry(e.copy(id = 0, amount = -e.amount, description = "Přesun: ${e.description}",
            voidsEntryId = e.id, createdAt = System.currentTimeMillis()))
        insertEntry(e.copy(id = 0, memberId = toMemberId, visitId = openVisit(toMemberId),
            createdAt = System.currentTimeMillis()))
        // stock unchanged – the item was sold, only the payer changed
    }

    /** Best sellers of the last [days] days, for the "Oblíbené" row. */
    fun topProducts(days: Int = 30, limit: Int = 8) =
        db.ledger().observeTopProducts(System.currentTimeMillis() - days * 86_400_000L, limit)

    fun memberFavorites(memberId: Long, limit: Int = 8) = db.ledger().observeMemberTopProducts(memberId, limit)

    fun topProductsSince(since: Long, limit: Int) = db.ledger().observeTopProducts(since, limit)

    suspend fun daySummary(dayStart: Long) = db.ledger().daySummary(dayStart)
    suspend fun totalOutstanding() = db.ledger().totalOutstanding()

    suspend fun balance(memberId: Long) = db.ledger().balance(memberId)

    /** Storno: adds a reversing entry (ledger stays auditable) and returns stock. */
    suspend fun void(entryId: Long) = db.withTransaction {
        val e = db.ledger().get(entryId) ?: return@withTransaction
        if (e.voidsEntryId != null || db.ledger().voidCount(e.id) > 0) return@withTransaction
        insertEntry(
            e.copy(
                id = 0, amount = -e.amount, description = "Storno: ${e.description}",
                voidsEntryId = e.id, createdAt = System.currentTimeMillis(),
            )
        )
        val pid = e.productId
        if (pid != null && db.products().get(pid)?.trackStock == true) {
            db.products().adjustStock(pid, e.quantity)
            db.stock().insert(StockMovement(productId = pid, delta = e.quantity, reason = "VOID", ledgerEntryId = e.id))
        }
    }

    // ---------- Products & stock ----------

    fun activeProducts() = db.products().observeActive()
    fun allProducts() = db.products().observeAll()

    suspend fun saveProduct(p: Product): Long =
        if (p.id == 0L) db.products().insert(p) else { db.products().update(p); p.id }

    suspend fun setProductImage(productId: Long, raw: ByteArray) {
        val p = db.products().get(productId) ?: return
        val newId = images.save(raw, maxSide = 256)
        db.products().update(p.copy(imageId = newId))
        images.delete(p.imageId)
    }

    /** Goods arrived (naskladnění). */
    suspend fun receiveStock(productId: Long, qty: Double) = db.withTransaction {
        db.products().adjustStock(productId, qty)
        db.stock().insert(StockMovement(productId = productId, delta = qty, reason = "PURCHASE"))
    }

    /** Physical count (inventura): sets the absolute amount and logs the difference. */
    suspend fun setCountedStock(productId: Long, counted: Double) = db.withTransaction {
        val p = db.products().get(productId) ?: return@withTransaction
        val diff = counted - p.stockQty
        if (diff != 0.0) {
            db.products().adjustStock(productId, diff)
            db.stock().insert(StockMovement(productId = productId, delta = diff, reason = "INVENTORY"))
        }
        suggestIfLow(productId)
    }

    // ---------- Shopping list ----------

    fun shoppingList() = db.shopping().observeAll()

    private fun suggestedQty(p: Product): Double =
        if (p.reorderQty > 0) p.reorderQty
        else ceil((p.minStock * 2 - p.stockQty).coerceAtLeast(1.0))

    private suspend fun suggestIfLow(productId: Long) {
        val p = db.products().get(productId) ?: return
        if (!p.active || !p.trackStock || p.stockQty > p.minStock) return
        if (db.shopping().openForProduct(p.id) != null) return
        db.shopping().insert(ShoppingItem(productId = p.id, name = p.name, quantity = suggestedQty(p), unit = p.unit))
    }

    /** Adds every product under its minimum that isn't on the list yet. Returns how many were added. */
    suspend fun generateShoppingList(): Int = db.withTransaction {
        var added = 0
        for (p in db.products().lowStock()) {
            if (db.shopping().openForProduct(p.id) == null) {
                db.shopping().insert(ShoppingItem(productId = p.id, name = p.name, quantity = suggestedQty(p), unit = p.unit))
                added++
            }
        }
        added
    }

    /** Put a product on the shopping list (no-op if it is already there). Returns false if it was already listed. */
    suspend fun addProductToShopping(productId: Long): Boolean {
        val p = db.products().get(productId) ?: return false
        if (db.shopping().openForProduct(p.id) != null) return false
        db.shopping().insert(ShoppingItem(productId = p.id, name = p.name, quantity = suggestedQty(p), unit = p.unit))
        return true
    }

    suspend fun addShoppingItem(name: String, qty: Double, unit: String) =
        db.shopping().insert(ShoppingItem(name = name.trim(), quantity = qty, unit = unit))

    /**
     * Ticking an item as bought; when [receive] is true and it is linked to a product,
     * the purchased quantity is added to stock straight away.
     */
    suspend fun setShoppingDone(item: ShoppingItem, done: Boolean, receive: Boolean) = db.withTransaction {
        db.shopping().update(item.copy(done = done))
        if (done && receive && item.productId != null) receiveStock(item.productId, item.quantity)
    }

    suspend fun updateShoppingQty(item: ShoppingItem, qty: Double) = db.shopping().update(item.copy(quantity = qty))
    suspend fun deleteShoppingItem(id: Long) = db.shopping().delete(id)
    suspend fun clearBought() = db.shopping().clearDone()

    // ---------- Kegs ----------

    fun activeKegs() = db.kegs().observeActive()
    fun kegGroups() = db.kegs().observeGroups()

    fun kegStock() = db.kegs().observeStock()

    private suspend fun stockOf(group: String) = db.kegs().stock(group) ?: cz.klubpos.data.db.entity.KegStock(group)

    /** Set how many full kegs are in the cellar (and optionally min / usual size). */
    suspend fun setKegStock(group: String, count: Int? = null, minCount: Int? = null, sizeLiters: Double? = null) {
        val s = stockOf(group)
        val n = s.copy(count = (count ?: s.count).coerceAtLeast(0), minCount = (minCount ?: s.minCount).coerceAtLeast(0),
            sizeLiters = sizeLiters ?: s.sizeLiters)
        db.kegs().upsertStock(n)
        suggestKegIfLow(n)
    }

    private suspend fun suggestKegIfLow(s: cz.klubpos.data.db.entity.KegStock) {
        if (s.count > s.minCount) return
        val name = "Sud ${s.kegGroup}"
        if (db.shopping().observeAll().first().any { !it.done && it.name == name }) return
        db.shopping().insert(ShoppingItem(name = name, quantity = 1.0, unit = "sud ${fmtL(s.sizeLiters)} l"))
    }

    private fun fmtL(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    /** Tap a new keg: the previous one is marked finished and one full keg is taken from the cellar. */
    suspend fun tapKeg(group: String, sizeLiters: Double, fromStock: Boolean = true) = db.withTransaction {
        val now = System.currentTimeMillis()
        db.kegs().finishActive(group, now)
        db.kegs().insert(cz.klubpos.data.db.entity.Keg(kegGroup = group, sizeLiters = sizeLiters, tappedAt = now))
        val s = stockOf(group)
        val n = s.copy(count = if (fromStock) (s.count - 1).coerceAtLeast(0) else s.count, sizeLiters = sizeLiters)
        db.kegs().upsertStock(n)
        suggestKegIfLow(n)
    }

    suspend fun finishKeg(group: String) = db.kegs().finishActive(group, System.currentTimeMillis())
    suspend fun largestPour(group: String) = db.kegs().largestPour(group) ?: 500

    // ---------- Suppliers ----------

    fun suppliers() = db.suppliers().observeAll()
    suspend fun saveSupplier(s: cz.klubpos.data.db.entity.Supplier) =
        if (s.id == 0L) db.suppliers().insert(s) else { db.suppliers().update(s); s.id }
    suspend fun deleteSupplier(id: Long) = db.suppliers().delete(id)

    // ---------- Import ----------

    data class ImportResult(val added: Int, val updated: Int, val skipped: List<String>)

    /** Upsert products by name (case-insensitive). Stock is only changed when the row contains a value. */
    suspend fun importProducts(rows: List<cz.klubpos.core.io.ProductRow>): ImportResult = db.withTransaction {
        val existing = db.products().all().associateBy { it.name.trim().lowercase() }
        var added = 0; var updated = 0
        val skipped = mutableListOf<String>()
        for (r in rows) {
            if (r.name.isBlank() || r.price == null) { skipped += r.name.ifBlank { "(prázdný název)" }; continue }
            val old = existing[r.name.trim().lowercase()]
            val base = old ?: Product(name = r.name.trim(), price = r.price)
            val p = base.copy(
                name = r.name.trim(),
                category = r.category?.ifBlank { null } ?: base.category,
                price = r.price,
                unit = r.unit?.ifBlank { null } ?: base.unit,
                trackStock = r.trackStock ?: base.trackStock,
                stockQty = r.stock ?: base.stockQty,
                minStock = r.minStock ?: base.minStock,
                reorderQty = r.reorderQty ?: base.reorderQty,
                kegGroup = r.kegGroup?.ifBlank { null } ?: base.kegGroup,
                pourMl = r.pourMl ?: base.pourMl,
                active = true,
            )
            if (old == null) { db.products().insert(p); added++ } else { db.products().update(p); updated++ }
        }
        ImportResult(added, updated, skipped)
    }

    suspend fun allProductsOnce() = db.products().all()

    // ---------- Demo data ----------

    suspend fun seedIfEmpty() {
        if (db.members().count() > 0) return
        listOf(
            Product(name = "Pivo 12° velké", category = "Pivo", price = 55, trackStock = false, kegGroup = "Pivo 12°", pourMl = 500),
            Product(name = "Pivo 12° malé", category = "Pivo", price = 35, trackStock = false, kegGroup = "Pivo 12°", pourMl = 300),
            Product(name = "Pivo 10°", category = "Pivo", price = 45, trackStock = false, kegGroup = "Pivo 10°", pourMl = 500),
            Product(name = "Nealko pivo", category = "Pivo", price = 40, unit = "ks", stockQty = 8.0, minStock = 6.0),
            Product(name = "Víno bílé 0,2", category = "Víno", price = 60, unit = "l", trackStock = false),
            Product(name = "Rum 4 cl", category = "Lihoviny", price = 70, trackStock = false),
            Product(name = "Kofola", category = "Nealko", price = 35, unit = "ks", stockQty = 24.0, minStock = 12.0),
            Product(name = "Minerálka", category = "Nealko", price = 30, unit = "ks", stockQty = 5.0, minStock = 12.0),
            Product(name = "Brambůrky", category = "Jídlo", price = 40, unit = "ks", stockQty = 10.0, minStock = 5.0),
            Product(name = "Utopenec", category = "Jídlo", price = 65, unit = "ks", stockQty = 6.0, minStock = 4.0),
        ).forEach { db.products().insert(it) }
        listOf("Jan Novák" to "Honza", "Petr Svoboda" to null, "Marie Dvořáková" to "Mája",
            "Karel Černý" to "Kája", "Eva Procházková" to null, "Tomáš Veselý" to "Tom")
            .forEach { (n, nick) -> addMember(n, nick) }
        setKegStock("Pivo 12°", count = 3, minCount = 1, sizeLiters = 50.0)
        setKegStock("Pivo 10°", count = 2, minCount = 1, sizeLiters = 30.0)
        tapKeg("Pivo 12°", 50.0)
        tapKeg("Pivo 10°", 30.0)
    }
}
