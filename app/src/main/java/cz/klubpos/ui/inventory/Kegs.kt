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
import androidx.compose.ui.graphics.drawscope.clipPath
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
    val stock by repo.kegStock().collectAsState(initial = emptyList())
    var editStock by remember { mutableStateOf<String?>(null) }
    var tapFor by remember { mutableStateOf<String?>(null) }
    var orderFor by remember { mutableStateOf<String?>(null) }
    val all = (groups + active.map { it.kegGroup }).distinct()
    if (all.isEmpty()) return

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(horizontal = 6.dp, vertical = 4.dp).tourTarget("kegs")) {
      val cols = (maxWidth / 360.dp).toInt().coerceIn(1, 4)
      Column {
       Text("SUDY  ·  vpravo plné ve sklepě", Modifier.padding(start = 4.dp, top = 4.dp), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp), color = KlubColors.Muted)
       all.chunked(cols).forEach { rowGroups ->
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        rowGroups.forEach { g ->
            val k = active.firstOrNull { it.kegGroup == g }
            val st = stock.firstOrNull { it.kegGroup == g }
            KegCard(g, k, st,
                onTap = { tapFor = g }, onOrder = { orderFor = g },
                onStockDelta = { d -> scope.launch { repo.setKegStock(g, count = (st?.count ?: 0) + d) } },
                onEditStock = { editStock = g },
                onFinish = { scope.launch { repo.finishKeg(g) } }, modifier = Modifier.weight(1f))
        }
        repeat(cols - rowGroups.size) { Box(Modifier.weight(1f)) }
        }
       }
      }
    }

    tapFor?.let { g ->
        val st = stock.firstOrNull { it.kegGroup == g }
        TapKegDialog(g, st?.count ?: 0, st?.sizeLiters ?: 50.0, onDismiss = { tapFor = null }) { size, fromStock ->
            scope.launch { repo.tapKeg(g, size, fromStock) }; tapFor = null
        }
    }
    editStock?.let { g ->
        val st = stock.firstOrNull { it.kegGroup == g }
        KegStockDialog(g, st?.count ?: 0, st?.minCount ?: 1, onDismiss = { editStock = null }) { c, m ->
            scope.launch { repo.setKegStock(g, count = c, minCount = m) }; editStock = null
        }
    }
    orderFor?.let { g ->
        OrderContactsSheet("Objednat: $g", "Dobrý den, objednávám sud $g. Děkuji.", onDismiss = { orderFor = null })
    }
}

@Composable
private fun KegCard(
    group: String, k: KegStatus?, stock: cz.klubpos.data.db.entity.KegStock?,
    onTap: () -> Unit, onOrder: () -> Unit, onStockDelta: (Int) -> Unit, onEditStock: () -> Unit, onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val df = remember { SimpleDateFormat("d. M.", Locale("cs", "CZ")) }
    var menu by remember { mutableStateOf(false) }
    val count = stock?.count ?: 0
    val low = count < (stock?.minCount ?: 1)
    val left = k?.let { (it.sizeLiters - it.pouredLiters).coerceAtLeast(0.0) } ?: 0.0
    val frac = k?.let { (left / it.sizeLiters).toFloat().coerceIn(0f, 1f) } ?: 0f
    val color = when { k == null -> KlubColors.Muted; frac < 0.1f -> KlubColors.OldDebt; frac < 0.25f -> KlubColors.OpenTab; else -> KlubColors.PaidToday }
    Column(modifier.fillMaxWidth().clickable { menu = !menu }.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(group, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(if (k == null) "nenaraženo" else "${fmtL(k.sizeLiters)} l sud · natočeno ${k.servings.toInt()}× · od ${df.format(Date(k.tappedAt))}",
                    style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted, maxLines = 1)
            }
            if (k != null) Text("${fmtL(left)} l", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = color)
            Text("−", Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = count > 0) { onStockDelta(-1) }
                .padding(horizontal = 8.dp), fontSize = 20.sp, color = KlubColors.Muted)
            Text("$count", Modifier.clickable(onClick = onEditStock), fontWeight = FontWeight.SemiBold,
                color = if (low) KlubColors.OpenTab else MaterialTheme.colorScheme.onSurface)
            Text("+", Modifier.clip(RoundedCornerShape(8.dp)).clickable { onStockDelta(1) }.padding(horizontal = 8.dp), fontSize = 20.sp, color = KlubColors.Muted)
        }
        if (k != null) Box(Modifier.fillMaxWidth().height(2.dp).background(KlubColors.Hairline)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(color))
        }
        if (menu) Row {
            TextButton(onTap) { Text(if (k == null) "Narazit sud" else "Narazit nový", color = KlubColors.Gold) }
            TextButton(onOrder) { Text("Objednat", color = KlubColors.Muted) }
            if (k != null) TextButton(onFinish) { Text("Dopito", color = KlubColors.Muted) }
        }
    }
}

@Composable
private fun TapKegDialog(group: String, inCellar: Int, usualSize: Double, onDismiss: () -> Unit, onTap: (Double, Boolean) -> Unit) {
    var custom by remember { mutableStateOf("") }
    var fromStock by remember { mutableStateOf(inCellar > 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Narazit sud – $group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Velikost sudu:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (listOf(15.0, 30.0, 50.0) + usualSize).distinct().sorted().forEach { s ->
                        val usual = s == usualSize
                        Box(Modifier.width(76.dp).height(56.dp).glass(RoundedCornerShape(16.dp), alpha = if (usual) 0.14f else 0.05f)
                            .clickable { onTap(s, fromStock) }, contentAlignment = Alignment.Center) {
                            Text("${fmtL(s)} l", style = MaterialTheme.typography.titleMedium, color = if (usual) KlubColors.Gold else KlubColors.Cream)
                        }
                    }
                }
                OutlinedTextField(custom, { custom = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Jiná velikost (l)") }, singleLine = true)
                Row(Modifier.clickable { fromStock = !fromStock }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(fromStock, { fromStock = it })
                    Text("Vzít ze sklepa (zbývá $inCellar)")
                }
                Text("Předchozí sud se označí jako dopitý, čas naražení se uloží.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            val v = custom.replace(',', '.').toDoubleOrNull()
            TextButton(enabled = v != null && v > 0, onClick = { onTap(v!!, fromStock) }) { Text("Narazit") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

@Composable
private fun KegStockDialog(group: String, count: Int, minCount: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var c by remember { mutableStateOf(count) }
    var m by remember { mutableStateOf(minCount) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sudy ve sklepě – $group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Stepper("Plných sudů", c) { c = it.coerceAtLeast(0) }
                Stepper("Cílový stav", m) { m = it.coerceAtLeast(0) }
                Text("Kolik plných sudů chceš mít ve sklepě. Objednávka doplní rozdíl.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton({ onSave(c, m) }) { Text("Uložit") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Box(Modifier.width(48.dp).height(48.dp).glass(RoundedCornerShape(14.dp)).clickable { onChange(value - 1) }, contentAlignment = Alignment.Center) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$value", Modifier.width(56.dp), style = Display.copy(fontSize = 26.sp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.width(48.dp).height(48.dp).glass(RoundedCornerShape(14.dp)).clickable { onChange(value + 1) }, contentAlignment = Alignment.Center) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
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
        clipPath(body) {
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
