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

/** Gold strip on Home while any promo runs: "Happy hour · Pivo 12° 39 kr (55) · do 20:00". */
@Composable
fun HappyHourStrip(modifier: Modifier = Modifier) {
    val repo = rememberRepo()
    val products by repo.activeProducts().collectAsState(initial = emptyList())
    val now = rememberMinuteClock()
    val active = products.filter { it.promoActive(now) }
    if (active.isEmpty()) return
    Row(modifier.tourTarget("happy_hour").fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(GoldMetal.map { it.copy(alpha = 0.9f) }))
        .horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("🍻 HAPPY HOUR", color = KlubColors.Ink, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
        active.forEach { p -> PromoChip(p) }
    }
}

@Composable
private fun PromoChip(p: Product) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(p.name, color = KlubColors.Ink, fontWeight = FontWeight.SemiBold)
        Text("${p.promoPrice} kr", color = KlubColors.Ink, fontWeight = FontWeight.ExtraBold)
        Text("${p.price}", color = KlubColors.Ink.copy(alpha = 0.6f), textDecoration = TextDecoration.LineThrough,
            style = MaterialTheme.typography.bodySmall)
        Text("do ${fmtTime(p.promoTo)}", color = KlubColors.Ink.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
    }
}
