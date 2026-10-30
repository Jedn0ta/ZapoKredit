package cz.klubpos.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cz.klubpos.ui.theme.KlubColors
import java.time.LocalDate
import java.time.YearMonth

/** Simple framed dialog used by every tool. */
@Composable
fun ToolFrame(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onDismiss) { Text("Zavřít", color = KlubColors.Muted) }
            }
            content()
        }
    }
}

/** List of tools; each one opens in its own dialog. */
@Composable
fun ToolsDialog(onDismiss: () -> Unit) {
    var tool by remember { mutableStateOf<String?>(null) }
    when (tool) {
        "calc" -> CalculatorDialog { tool = null }
        "cal" -> CalendarDialog { tool = null }
        else -> ToolFrame("Nástroje", onDismiss) {
            ToolRow(Icons.Default.Calculate, "Kalkulačka") { tool = "calc" }
            ToolRow(Icons.Default.CalendarMonth, "Kalendář") { tool = "cal" }
        }
    }
}

@Composable
private fun ToolRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = KlubColors.Gold)
        Text(label, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleMedium)
    }
}

// ---------------- Calculator ----------------

private fun evaluate(expr: String): Double? = runCatching {
    // numbers and + − × ÷ with usual precedence
    val tokens = Regex("\\d+(?:[.,]\\d+)?|[+−×÷]").findAll(expr).map { it.value }.toList()
    if (tokens.isEmpty()) return null
    val terms = mutableListOf<Double>(); val ops = mutableListOf<Char>()
    var cur = tokens[0].replace(',', '.').toDouble()
    var i = 1
    while (i + 1 < tokens.size) {
        val op = tokens[i][0]; val n = tokens[i + 1].replace(',', '.').toDouble()
        when (op) { '×' -> cur *= n; '÷' -> cur /= n; else -> { terms += cur; ops += op; cur = n } }
        i += 2
    }
    terms += cur
    var r = terms[0]
    ops.forEachIndexed { k, op -> r = if (op == '+') r + terms[k + 1] else r - terms[k + 1] }
    r
}.getOrNull()

private fun fmt(d: Double): String =
    if (d.isNaN() || d.isInfinite()) "chyba"
    else if (d % 1.0 == 0.0 && kotlin.math.abs(d) < 1e15) d.toLong().toString()
    else "%.4f".format(java.util.Locale("cs", "CZ"), d).trimEnd('0').trimEnd(',')

@Composable
fun CalculatorDialog(onDismiss: () -> Unit) {
    var expr by remember { mutableStateOf("") }
    var last by remember { mutableStateOf<String?>(null) }
    val preview = evaluate(expr)
    fun press(k: String) {
        when (k) {
            "C" -> { expr = ""; last = null }
            "⌫" -> expr = expr.dropLast(1)
            "=" -> preview?.let { last = expr; expr = fmt(it).replace(" ", "").replace(" ", "") }
            "+", "−", "×", "÷" -> expr = if (expr.isEmpty()) "" else if (expr.last() in "+−×÷") expr.dropLast(1) + k else expr + k
            "," -> { val tail = expr.takeLastWhile { it.isDigit() || it == ',' }; if (',' !in tail) expr += if (tail.isEmpty()) "0," else "," }
            else -> expr += k
        }
    }
    ToolFrame("Kalkulačka", onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text(last ?: " ", color = KlubColors.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(expr.ifEmpty { "0" }, style = MaterialTheme.typography.headlineMedium, maxLines = 2, textAlign = TextAlign.End)
            Text(if (preview != null && expr.any { it in "+−×÷" }) "= ${fmt(preview)}" else " ", color = KlubColors.Gold)
        }
        val rows = listOf(listOf("C", "⌫", "÷", "×"), listOf("7", "8", "9", "−"), listOf("4", "5", "6", "+"), listOf("1", "2", "3", "="), listOf("0", ",", "00", ""))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    if (k.isEmpty()) Box(Modifier.weight(1f)) else
                    Box(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(14.dp))
                        .background(when (k) { "=" -> KlubColors.Gold; "C", "⌫", "÷", "×", "−", "+" -> KlubColors.SurfaceHigh; else -> KlubColors.SurfaceHigh.copy(alpha = 0.5f) })
                        .clickable { press(k) }, contentAlignment = Alignment.Center) {
                        Text(k, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                            color = when (k) { "=" -> KlubColors.Ink; "C" -> KlubColors.OldDebt; "÷", "×", "−", "+" -> KlubColors.Gold; else -> MaterialTheme.colorScheme.onSurface })
                    }
                }
            }
        }
    }
}

// ---------------- Calendar ----------------

private val MONTHS = listOf("leden", "únor", "březen", "duben", "květen", "červen", "červenec", "srpen", "září", "říjen", "listopad", "prosinec")

/** Month calendar with a note per day (stored on this device). */
@Composable
fun CalendarDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("calendar_notes", android.content.Context.MODE_PRIVATE) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var version by remember { mutableStateOf(0) }
    var note by remember(selected) { mutableStateOf(prefs.getString(selected.toString(), "") ?: "") }
    val today = LocalDate.now()
    ToolFrame("Kalendář", onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ month = month.minusMonths(1) }) { Text("‹", fontSize = 22.sp) }
            Text("${MONTHS[month.monthValue - 1]} ${month.year}", Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium)
            TextButton({ month = month.plusMonths(1) }) { Text("›", fontSize = 22.sp) }
        }
        Row { listOf("po", "út", "st", "čt", "pá", "so", "ne").forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = KlubColors.Muted) } }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        version.let { }
        (0 until ((offset + days + 6) / 7)).forEach { w ->
            Row {
                (0 until 7).forEach { d ->
                    val n = w * 7 + d - offset + 1
                    if (n !in 1..days) Box(Modifier.weight(1f).aspectRatio(1f)) else {
                        val date = month.atDay(n)
                        val sel = date == selected
                        val has = !prefs.getString(date.toString(), null).isNullOrBlank()
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                            .then(if (sel) Modifier.background(KlubColors.Gold) else if (date == today) Modifier.border(1.dp, KlubColors.Gold, CircleShape) else Modifier)
                            .clickable { selected = date }, contentAlignment = Alignment.Center) {
                            Text("$n", color = if (sel) KlubColors.Ink else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            if (has) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).clip(CircleShape)
                                .background(if (sel) KlubColors.Ink else KlubColors.Gold))
                        }
                    }
                }
            }
        }
        OutlinedTextField(note, { note = it; prefs.edit().putString(selected.toString(), it).apply(); version++ },
            label = { Text("Poznámka ${selected.dayOfMonth}. ${selected.monthValue}.") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
    }
}
