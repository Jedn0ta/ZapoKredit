package cz.klubpos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cz.klubpos.data.db.entity.Product
import cz.klubpos.ui.theme.GoldMetal
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.tour.tourTarget
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Current time, refreshed every minute (for happy hour start / end). */
@Composable
fun rememberMinuteClock(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000L - System.currentTimeMillis() % 60_000L + 200); now = LocalDateTime.now() }
    }
    return now
}

fun fmtTime(min: Int) = "%d:%02d".format((min / 60) % 24, min % 60)

/** Strip on Home while anything is on offer: happy hour (gold), sales and X+Y deals. */
@Composable
fun HappyHourStrip(modifier: Modifier = Modifier) {
    val repo = rememberRepo()
    val products by repo.activeProducts().collectAsState(initial = emptyList())
    val now = rememberMinuteClock()
    val hh = products.filter { it.promoActive(now) }
    val sale = products.filter { !it.promoActive(now) && it.saleActive() }
    val deals = products.filter { it.dealActive() }
    if (hh.isEmpty() && sale.isEmpty() && deals.isEmpty()) return
    Row(modifier.tourTarget("happy_hour").fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(if (hh.isNotEmpty()) Brush.linearGradient(GoldMetal.map { it.copy(alpha = 0.9f) })
            else Brush.linearGradient(listOf(KlubColors.SurfaceHigh, KlubColors.Surface)))
        .horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val ink = if (hh.isNotEmpty()) KlubColors.Ink else KlubColors.Cream
        if (hh.isNotEmpty()) {
            Text("🍻 HAPPY HOUR", color = ink, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
            hh.forEach { p -> PriceChip(p.name, p.promoPrice!!, p.price, "do ${fmtTime(p.promoTo)}", ink) }
        }
        if (sale.isNotEmpty()) {
            Text("🏷 AKCE", color = ink, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
            sale.forEach { p ->
                val until = when {
                    p.saleLeft != null -> "zbývá ${p.saleLeft.toInt()} ks"
                    p.saleTo != null -> "do " + java.text.SimpleDateFormat("d. M.", java.util.Locale("cs", "CZ")).format(java.util.Date(p.saleTo - 1))
                    else -> ""
                }
                PriceChip(p.name, p.salePrice!!, p.price, until, ink)
            }
        }
        if (deals.isNotEmpty()) {
            Text("🎁", style = MaterialTheme.typography.labelLarge)
            deals.forEach { p ->
                Text("${p.name} ${p.dealBuy}+${p.dealFree} zdarma", color = ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PriceChip(name: String, price: Long, old: Long, note: String, ink: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, color = ink, fontWeight = FontWeight.SemiBold)
        Text("$price kr", color = ink, fontWeight = FontWeight.ExtraBold)
        Text("$old", color = ink.copy(alpha = 0.6f), textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodySmall)
        if (note.isNotEmpty()) Text(note, color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
    }
}
