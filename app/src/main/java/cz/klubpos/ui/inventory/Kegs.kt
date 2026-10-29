package cz.klubpos.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.data.db.dao.KegStatus
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.suppliers.OrderContactsSheet
import cz.klubpos.ui.theme.Display
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import cz.klubpos.ui.tour.tourTarget
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Draught beer: one card per keg group with remaining volume, tapped time and actions. */
@Composable
fun KegsSection() {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val active by repo.activeKegs().collectAsState(initial = emptyList())
    val groups by repo.kegGroups().collectAsState(initial = emptyList())
    var tapFor by remember { mutableStateOf<String?>(null) }
    var orderFor by remember { mutableStateOf<String?>(null) }
    val all = (groups + active.map { it.kegGroup }).distinct()
    if (all.isEmpty()) return

    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).tourTarget("kegs"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        all.forEach { g ->
            val k = active.firstOrNull { it.kegGroup == g }
            KegCard(g, k, onTap = { tapFor = g }, onOrder = { orderFor = g })
        }
    }

    tapFor?.let { g ->
        TapKegDialog(g, onDismiss = { tapFor = null }) { size -> scope.launch { repo.tapKeg(g, size) }; tapFor = null }
    }
    orderFor?.let { g ->
        OrderContactsSheet("Objednat: $g", "Dobrý den, objednávám sud $g. Děkuji.", onDismiss = { orderFor = null })
    }
}

@Composable
private fun KegCard(group: String, k: KegStatus?, onTap: () -> Unit, onOrder: () -> Unit) {
    val df = remember { SimpleDateFormat("EEEE d. M. 'v' HH:mm", Locale("cs", "CZ")) }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (k != null) KegGauge(((k.sizeLiters - k.pouredLiters) / k.sizeLiters).toFloat(), Modifier.width(34.dp).height(44.dp).padding(end = 10.dp))
            Text(group, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (k != null) Text("${fmtL(k.sizeLiters)} l sud", color = KlubColors.Muted, style = MaterialTheme.typography.labelMedium)
        }
        if (k == null) {
            Text("Není naražen žádný sud.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val left = (k.sizeLiters - k.pouredLiters).coerceAtLeast(0.0)
            val frac = (left / k.sizeLiters).toFloat().coerceIn(0f, 1f)
            val color = when { frac < 0.1f -> KlubColors.OldDebt; frac < 0.25f -> KlubColors.OpenTab; else -> KlubColors.PaidToday }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmtL(left), style = Display.copy(fontSize = 34.sp), color = color)
                Text(" l zbývá", Modifier.padding(bottom = 6.dp), color = KlubColors.Muted)
                Box(Modifier.weight(1f))
                Text("≈ ${(left / 0.5).toInt()} velkých", Modifier.padding(bottom = 6.dp), color = KlubColors.Muted)
            }
            // Keg gauge
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(KlubColors.SurfaceHigh)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(7.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))))
            }
            Text("Naraženo ${df.format(Date(k.tappedAt))} · natočeno ${fmtL(k.pouredLiters)} l (${k.servings.toInt()}×)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (frac < 0.1f) Text("Sud dochází!", color = KlubColors.OldDebt, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onTap) { Text(if (k == null) "Narazit sud" else "Narazit nový", color = KlubColors.Gold) }
            TextButton(onOrder) { Text("Objednat") }
        }
    }
}

@Composable
private fun TapKegDialog(group: String, onDismiss: () -> Unit, onTap: (Double) -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Narazit sud – $group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Velikost sudu:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(15.0, 30.0, 50.0).forEach { s ->
                        Box(Modifier.width(80.dp).height(56.dp).glass(RoundedCornerShape(16.dp)).clickable { onTap(s) },
                            contentAlignment = Alignment.Center) { Text("${fmtL(s)} l", style = MaterialTheme.typography.titleMedium) }
                    }
                }
                OutlinedTextField(custom, { custom = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Jiná velikost (l)") }, singleLine = true)
                Text("Předchozí sud se označí jako dopitý. Čas naražení se uloží.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            val v = custom.replace(',', '.').toDoubleOrNull()
            TextButton(enabled = v != null && v > 0, onClick = { onTap(v!!) }) { Text("Narazit") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

private fun fmtL(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(Locale("cs", "CZ"), v)

/**
 * Keg drawn as a barrel with the estimated beer level inside (amber with a foam line).
 * [fraction] 0..1 = remaining.
 */
@Composable
fun KegGauge(fraction: Float, modifier: Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    val beer = when { f < 0.1f -> KlubColors.OldDebt; f < 0.25f -> KlubColors.OpenTab; else -> KlubColors.Gold }
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        val stroke = w * 0.07f
        // barrel outline: slightly bulging sides
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.18f, 0f); lineTo(w * 0.82f, 0f)
            quadraticBezierTo(w * 1.02f, h * 0.5f, w * 0.82f, h)
            lineTo(w * 0.18f, h)
            quadraticBezierTo(-w * 0.02f, h * 0.5f, w * 0.18f, 0f)
            close()
        }
        drawPath(body, KlubColors.SurfaceHigh)
        // beer level (clipped to barrel shape)
        androidx.compose.ui.graphics.drawscope.clipPath(body) {
            val top = h * (1f - f)
            drawRect(Brush.verticalGradient(listOf(beer, beer.copy(alpha = 0.75f)), startY = top, endY = h),
                topLeft = androidx.compose.ui.geometry.Offset(0f, top), size = androidx.compose.ui.geometry.Size(w, h - top))
            if (f > 0.02f) drawRect(KlubColors.Cream.copy(alpha = 0.9f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, top), size = androidx.compose.ui.geometry.Size(w, h * 0.05f))
        }
        // hoops
        listOf(0.22f, 0.78f).forEach { y ->
            drawLine(KlubColors.Muted.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(w * 0.04f, h * y),
                androidx.compose.ui.geometry.Offset(w * 0.96f, h * y), strokeWidth = stroke * 0.6f)
        }
        drawPath(body, KlubColors.Muted.copy(alpha = 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
    }
}

/** Compact strip for the main screen: one barrel per keg on tap. */
@Composable
fun KegStrip(modifier: Modifier = Modifier) {
    val repo = rememberRepo()
    val kegs by repo.activeKegs().collectAsState(initial = emptyList())
    if (kegs.isEmpty()) return
    Row(modifier.tourTarget("keg_strip"), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        kegs.forEach { k ->
            val frac = ((k.sizeLiters - k.pouredLiters) / k.sizeLiters).toFloat().coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                KegGauge(frac, Modifier.width(30.dp).height(38.dp))
                Column(Modifier.padding(start = 8.dp)) {
                    Text(k.kegGroup, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text("${(frac * 100).toInt()} % · ${fmtL((k.sizeLiters - k.pouredLiters).coerceAtLeast(0.0))} l",
                        style = MaterialTheme.typography.labelSmall,
                        color = when { frac < 0.1f -> KlubColors.OldDebt; frac < 0.25f -> KlubColors.OpenTab; else -> KlubColors.Muted })
                }
            }
        }
    }
}
