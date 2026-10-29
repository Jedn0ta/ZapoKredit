package cz.klubpos.ui.members

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.klubpos.domain.BusinessDay
import cz.klubpos.domain.Credits
import cz.klubpos.domain.MemberStatus
import cz.klubpos.domain.MemberStatus.Headline
import cz.klubpos.ui.components.EncryptedImage
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.theme.KlubColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Filter(val label: String) { ALL("Všichni"), IN_CLUB("V klubu"), DEBT("S dluhem") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(onOpenMember: (Long) -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()

    // Re-query when the business day rolls over (06:00), so "today" stays correct overnight.
    var dayStart by remember { mutableLongStateOf(BusinessDay.startMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); dayStart = BusinessDay.startMillis() } }
    val members by remember(dayStart) { repo.memberStatuses(dayStart) }.collectAsState(initial = emptyList())

    var filter by remember { mutableStateOf(Filter.ALL) }
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmCloseNight by remember { mutableStateOf(false) }

    val shown = members
        .filter { when (filter) { Filter.ALL -> true; Filter.IN_CLUB -> it.inClub; Filter.DEBT -> it.hasDebt } }
        .filter { query.isBlank() || it.name.contains(query, true) || (it.nickname?.contains(query, true) == true) }
        // People in the club first, then alphabetically (already sorted by DB).
        .sortedByDescending { it.inClub }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) OutlinedTextField(query, { query = it }, singleLine = true,
                        placeholder = { Text("Hledat člena") }, modifier = Modifier.fillMaxWidth())
                    else Text("Členové · ${members.count { it.inClub }} v klubu")
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) query = "" }) { Icon(Icons.Default.Search, "Hledat") }
                    IconButton(onClick = { confirmCloseNight = true }) { Icon(Icons.Default.NightsStay, "Ukončit večer") }
                },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Nový člen") } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { f -> FilterChip(filter == f, { filter = f }, label = { Text(f.label) }) }
            }
            Legend()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { it.memberId }) { m -> Medallion(m) { onOpenMember(m.memberId) } }
            }
        }
    }

    if (showAdd) AddMemberDialog(
        onDismiss = { showAdd = false },
        onAdd = { name, nick -> scope.launch { repo.addMember(name, nick) }; showAdd = false },
    )
    if (confirmCloseNight) AlertDialog(
        onDismissRequest = { confirmCloseNight = false },
        title = { Text("Ukončit večer?") },
        text = { Text("Všem se zavře otevřený účet (odchod z klubu). Dluhy zůstávají zapsané.") },
        confirmButton = { TextButton({ scope.launch { repo.closeAllVisits() }; confirmCloseNight = false }) { Text("Ukončit") } },
        dismissButton = { TextButton({ confirmCloseNight = false }) { Text("Zpět") } },
    )
}

fun headlineColor(h: Headline): Color = when (h) {
    Headline.OPEN_TAB_TODAY -> KlubColors.OpenTab
    Headline.PAID_TODAY -> KlubColors.PaidToday
    Headline.PAID_TODAY_OLD_DEBT -> KlubColors.PaidToday
    Headline.OLD_DEBT -> KlubColors.OldDebt
    Headline.CLEAR -> KlubColors.Muted
}

@Composable
private fun Legend() {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        LegendDot(KlubColors.InClub, "v klubu")
        LegendDot(KlubColors.OpenTab, "dnešní útrata")
        LegendDot(KlubColors.PaidToday, "dnes zaplaceno")
        LegendDot(KlubColors.OldDebt, "starý dluh")
    }
}

@Composable
private fun LegendDot(c: Color, label: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
    Spacer(Modifier.size(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Member tile: photo, name, presence, and the tab state split into today / older. */
@Composable
fun Medallion(m: MemberStatus, onClick: () -> Unit) {
    val accent = headlineColor(m.headline)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = if (m.inClub) BorderStroke(3.dp, KlubColors.InClub) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (m.inClub) KlubColors.SurfaceHigh else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            EncryptedImage(m.photoId, m.name, Modifier.fillMaxWidth().aspectRatio(1f), textSize = 40)
            if (m.inClub) Text("V KLUBU", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(KlubColors.InClub).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        // Colour strip = dominant state
        Box(Modifier.fillMaxWidth().height(5.dp).background(accent))
        Column(Modifier.padding(10.dp)) {
            Text(m.nickname ?: m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.nickname != null) Text(m.name, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            when {
                m.todayUnpaid > 0 -> StatusLine("Dnes", Credits.format(m.todayUnpaid), KlubColors.OpenTab)
                m.paidToday -> StatusLine("Dnes", "zaplaceno ✓", KlubColors.PaidToday)
            }
            if (m.olderDebt > 0) StatusLine("Dluh", Credits.format(m.olderDebt), KlubColors.OldDebt)
            if (m.hasCredit) StatusLine("Přeplatek", Credits.format(-m.balance), KlubColors.PaidToday)
            if (m.balance == 0L && !m.paidToday) Text("Bez dluhu", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, color: Color) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }

@Composable
private fun AddMemberDialog(onDismiss: () -> Unit, onAdd: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var nick by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nový člen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Jméno a příjmení") }, singleLine = true)
                OutlinedTextField(nick, { nick = it }, label = { Text("Přezdívka (nepovinné)") }, singleLine = true)
                Text("Fotku přidáš v detailu člena přes QR kód.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name, nick) }) { Text("Přidat") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
}
