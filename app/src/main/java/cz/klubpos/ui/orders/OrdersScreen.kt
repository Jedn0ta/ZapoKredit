package cz.klubpos.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.lan.toDto
import cz.klubpos.net.OrderDto
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.member.fmtQty
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Orders from waiters / the PC that the bar prepares. Items are already on the member's / table's account. */
@Composable
fun OrdersScreen(onOpenMember: (Long) -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = System.currentTimeMillis() } }
    val since = remember { System.currentTimeMillis() - 3 * 3600_000L }
    val raw by remember { repo.prepOrders(since) }.collectAsState(initial = emptyList())
    val orders = remember(raw) {
        raw.map { it.toDto() }.sortedWith(compareBy<OrderDto>({ rank(it.status) }, { if (it.status == "DONE") -it.updatedAt else it.createdAt }))
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp, 12.dp, 12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Objednávky k přípravě", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            val open = orders.count { it.status == "NEW" || it.status == "PREPARING" }
            Text(if (open > 0) "$open otevřených" else "vše hotovo", color = if (open > 0) KlubColors.OpenTab else KlubColors.Muted)
        }
        if (orders.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Zatím nic. Objednávky od číšníků a z PC se ukážou tady.", color = KlubColors.Muted)
        }
        LazyVerticalGrid(GridCells.Adaptive(300.dp), contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(orders, key = { it.id }) { o ->
                OrderCard(o, now, onOpen = { o.memberId?.let(onOpenMember) },
                    onStatus = { st -> scope.launch { repo.setPrepStatus(o.id, st) } })
            }
        }
    }
}

private fun rank(s: String) = when (s) { "NEW" -> 0; "PREPARING" -> 1; "DONE" -> 2; else -> 3 }

fun ago(t: Long, now: Long): String {
    val m = ((now - t) / 60_000).coerceAtLeast(0)
    return when { m < 1 -> "teď"; m < 60 -> "před $m min"; else -> "před ${m / 60} h ${m % 60} min" }
}

@Composable
private fun OrderCard(o: OrderDto, now: Long, onOpen: () -> Unit, onStatus: (String) -> Unit) {
    val color = when (o.status) { "NEW" -> KlubColors.OpenTab; "PREPARING" -> KlubColors.Gold; "DONE" -> KlubColors.PaidToday; else -> KlubColors.Muted }
    val done = o.status == "DONE" || o.status == "CANCELLED"
    Column(Modifier.fillMaxWidth().alpha(if (done) 0.55f else 1f).glass(RoundedCornerShape(20.dp))
        .border(if (o.status == "NEW") 2.dp else 0.dp, color, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen)) {
                Text(o.target, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(if (o.isTable) "stůl" else "člen", o.waiter, ago(o.createdAt, now)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted)
            }
            Text(OrderDto.statusLabel(o.status), Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelMedium)
        }
        o.items.forEach { i ->
            Row(verticalAlignment = Alignment.Top) {
                Text("${fmtQty(i.qty)}×", Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Column(Modifier.weight(1f)) {
                    Text(i.name, fontSize = 18.sp)
                    i.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = KlubColors.Gold, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        o.note?.let { Text("Pozn.: $it", color = KlubColors.Gold) }
        Row {
            when (o.status) {
                "NEW" -> { TextButton({ onStatus("PREPARING") }) { Text("Připravuji") }; TextButton({ onStatus("DONE") }) { Text("Hotovo", color = KlubColors.PaidToday) } }
                "PREPARING" -> TextButton({ onStatus("DONE") }) { Text("Hotovo", color = KlubColors.PaidToday) }
                else -> TextButton({ onStatus("NEW") }) { Text("Vrátit mezi otevřené", color = KlubColors.Muted) }
            }
            Spacer(Modifier.weight(1f))
            if (!done) TextButton({ onStatus("CANCELLED") }) { Text("Zrušit", color = KlubColors.Muted) }
        }
    }
}
