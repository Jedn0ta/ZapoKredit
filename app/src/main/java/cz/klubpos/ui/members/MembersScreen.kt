@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package cz.klubpos.ui.members

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import cz.klubpos.ui.components.categoryColor
import cz.klubpos.ui.tour.tourTarget
import cz.klubpos.ui.theme.glass
import cz.klubpos.ui.theme.Grotesk
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.domain.BusinessDay
import cz.klubpos.domain.Credits
import cz.klubpos.domain.MemberStatus
import cz.klubpos.domain.MemberStatus.Headline
import cz.klubpos.ui.components.EncryptedImage
import cz.klubpos.ui.components.QrUploadDialog
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.theme.KlubColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Filter(val label: String) { ALL("Vše"), IN_CLUB("V klubu"), DEBT("Dluh") }

/**
 * Main POS screen: grid of members.
 *  tap        → order screen
 *  long-press → quick actions (arrive/leave, pay all, photo, edit)
 */
@Composable
fun MembersScreen(onOpenMember: (Long) -> Unit, onEditMember: (Long) -> Unit = onOpenMember) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()

    var dayStart by remember { mutableLongStateOf(BusinessDay.startMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); dayStart = BusinessDay.startMillis() } }
    val members by remember(dayStart) { repo.memberStatuses(dayStart) }.collectAsState(initial = emptyList())

    var filter by remember { mutableStateOf(Filter.ALL) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<MemberStatus?>(null) }
    var photoFor by remember { mutableStateOf<MemberStatus?>(null) }
    var payAllFor by remember { mutableStateOf<MemberStatus?>(null) }
    // Round mode: select several members, then pick one product for all of them.
    var roundMode by remember { mutableStateOf(false) }
    val roundSel = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    var pickRoundProduct by remember { mutableStateOf(false) }
    val products by repo.activeProducts().collectAsState(initial = emptyList())

    val shown = members
        .filter { when (filter) { Filter.ALL -> true; Filter.IN_CLUB -> it.inClub; Filter.DEBT -> it.hasDebt } }
        .filter { query.isBlank() || it.name.contains(query, true) || (it.nickname?.contains(query, true) == true) }
        .sortedByDescending { it.inClub }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Minimal header: search + segmented filter
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchField(query, { query = it }, Modifier.weight(1f).tourTarget("search"))
            Segmented(Filter.entries.map { it.label }, filter.ordinal) { filter = Filter.entries[it] }
            Box(Modifier.size(48.dp)
                .then(if (roundMode) Modifier.clip(RoundedCornerShape(16.dp)).background(KlubColors.GoldBrush) else Modifier.glass(RoundedCornerShape(16.dp)))
                .tourTarget("round_button")
                .combinedClickable(onClick = { roundMode = !roundMode; roundSel.clear() }), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Groups, "Runda", tint = if (roundMode) Color.Black else MaterialTheme.colorScheme.onSurface)
            }
        }
        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(shown, key = { _, m -> m.memberId }) { i, m ->
                    val selected = m.memberId in roundSel
                    MemberTile(m,
                        modifier = if (i == 0) Modifier.tourTarget("member_tile") else Modifier,
                        selected = roundMode && selected,
                        onClick = {
                            if (roundMode) { if (selected) roundSel.remove(m.memberId) else roundSel.add(m.memberId) }
                            else onOpenMember(m.memberId)
                        },
                        onLongClick = { if (!roundMode) actionsFor = m })
                }
            }
            if (roundMode) {
                // Round bar
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (roundSel.isEmpty()) "Označ členy pro rundu" else "Runda · ${roundSel.size}",
                        Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton({ roundMode = false; roundSel.clear() }) { Text("Zrušit") }
                    Box(Modifier.height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (roundSel.isNotEmpty()) KlubColors.Amber else MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(enabled = roundSel.isNotEmpty(), onClick = { pickRoundProduct = true })
                        .padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
                        Text("Vybrat položku", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Small add button bottom-right
                Box(Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp)
                    .shadow(20.dp, CircleShape, ambientColor = KlubColors.Gold, spotColor = KlubColors.Gold)
                    .clip(CircleShape).background(KlubColors.GoldBrush).tourTarget("add_member")
                    .combinedClickable(onClick = { showAdd = true }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, "Nový člen", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }

    actionsFor?.let { m ->
        QuickActionsSheet(m,
            onDismiss = { actionsFor = null },
            onToggleVisit = { scope.launch { if (m.inClub) repo.closeVisit(m.memberId) else repo.openVisit(m.memberId) }; actionsFor = null },
            onPayAll = { payAllFor = m; actionsFor = null },
            onPhoto = { photoFor = m; actionsFor = null },
            onEdit = { onEditMember(m.memberId); actionsFor = null },
        )
    }
    payAllFor?.let { m ->
        AlertDialog(
            onDismissRequest = { payAllFor = null },
            title = { Text("${m.nickname ?: m.name} platí") },
            text = { Text(Credits.format(m.balance), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold) },
            confirmButton = { TextButton({ scope.launch { repo.pay(m.memberId, m.balance, closeVisitAfter = true) }; payAllFor = null }) { Text("Zaplaceno") } },
            dismissButton = { TextButton({ payAllFor = null }) { Text("Zpět") } },
        )
    }
    if (pickRoundProduct) ModalBottomSheet(onDismissRequest = { pickRoundProduct = false },
        containerColor = MaterialTheme.colorScheme.surface) {
        Text("Runda pro ${roundSel.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyVerticalGrid(GridCells.Adaptive(110.dp), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.height(420.dp)) {
            items(products, key = { it.id }) { p ->
                Column(Modifier.aspectRatio(1.2f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(onClick = {
                        val ids = roundSel.toList()
                        scope.launch { repo.chargeRound(ids, p.id) }
                        pickRoundProduct = false; roundMode = false; roundSel.clear()
                    }).padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(categoryColor(p.category)))
                    Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${p.price} × ${roundSel.size}", color = categoryColor(p.category), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    photoFor?.let { m -> QrUploadDialog("Fotka – ${m.nickname ?: m.name}", onImage = { repo.setMemberPhoto(m.memberId, it) }) { photoFor = null } }
    if (showAdd) AddMemberDialog(onDismiss = { showAdd = false }) { name, nick ->
        scope.launch { repo.addMember(name, nick) }; showAdd = false
    }
}

fun headlineColor(h: Headline): Color = when (h) {
    Headline.OPEN_TAB_TODAY -> KlubColors.OpenTab
    Headline.PAID_TODAY, Headline.PAID_TODAY_OLD_DEBT -> KlubColors.PaidToday
    Headline.OLD_DEBT -> KlubColors.OldDebt
    Headline.CLEAR -> KlubColors.Muted
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberTile(m: MemberStatus, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.fillMaxWidth().aspectRatio(0.82f).clip(shape)
            .then(when {
                selected -> Modifier.border(BorderStroke(4.dp, KlubColors.Amber), shape)
                m.inClub -> Modifier.border(BorderStroke(2.5.dp, Brush.linearGradient(listOf(KlubColors.InClub, KlubColors.Violet))), shape)
                else -> Modifier.border(1.dp, KlubColors.Hairline, shape)
            })
            .combinedClickable(onClick = onClick, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() })
    ) {
        EncryptedImage(m.photoId, m.name, Modifier.fillMaxSize(), textSize = 44)
        // Absent members are dimmed so present ones pop out.
        if (!m.inClub) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        // Name over gradient
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(10.dp, 24.dp, 10.dp, 10.dp)) {
            Column {
                Text(m.nickname ?: m.name, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m.inClub) Text("v klubu", color = KlubColors.InClub, style = MaterialTheme.typography.labelSmall)
            }
        }
        AmountBadge(m, Modifier.align(Alignment.TopEnd).padding(8.dp))
        if (selected) Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(28.dp).clip(CircleShape).background(KlubColors.Amber),
            contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp)) }
    }
}

/** One number only. Orange = today's tab, red = old debt, green ✓ = paid today. Nothing when clear. */
@Composable
private fun AmountBadge(m: MemberStatus, modifier: Modifier) {
    val (text, color) = when {
        m.balance > 0 -> Credits.format(m.balance) to (if (m.todayUnpaid > 0) KlubColors.OpenTab else KlubColors.OldDebt)
        m.paidToday -> "✓" to KlubColors.PaidToday
        else -> return
    }
    Text(text, modifier.shadow(12.dp, RoundedCornerShape(12.dp), ambientColor = color, spotColor = color)
        .clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f))))
        .padding(horizontal = 10.dp, vertical = 4.dp),
        color = KlubColors.Ink, style = MaterialTheme.typography.labelLarge.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 15.sp))
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Row(modifier.height(48.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Hledat", color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.height(48.dp).glass(RoundedCornerShape(16.dp)).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(Modifier.fillMaxHeight().clip(RoundedCornerShape(12.dp))
                .then(if (sel) Modifier.background(KlubColors.GoldBrush) else Modifier)
                .combinedClickable(onClick = { onSelect(i) }).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center) {
                Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) KlubColors.Ink else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionsSheet(
    m: MemberStatus, onDismiss: () -> Unit,
    onToggleVisit: () -> Unit, onPayAll: () -> Unit, onPhoto: () -> Unit, onEdit: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(m.nickname ?: m.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ListItem(headlineContent = { Text(if (m.inClub) "Odchod" else "Příchod") },
                leadingContent = { Icon(if (m.inClub) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login, null) },
                colors = colors, modifier = Modifier.combinedClickable(onClick = onToggleVisit))
            if (m.balance > 0) ListItem(headlineContent = { Text("Zaplatit vše") },
                trailingContent = { Text(Credits.format(m.balance), fontWeight = FontWeight.Bold) },
                leadingContent = { Icon(Icons.Default.Payments, null) },
                colors = colors, modifier = Modifier.combinedClickable(onClick = onPayAll))
            ListItem(headlineContent = { Text("Fotka") }, leadingContent = { Icon(Icons.Default.CameraAlt, null) },
                colors = colors, modifier = Modifier.combinedClickable(onClick = onPhoto))
            ListItem(headlineContent = { Text("Upravit") }, leadingContent = { Icon(Icons.Default.Edit, null) },
                colors = colors, modifier = Modifier.combinedClickable(onClick = onEdit))
        }
    }
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
                OutlinedTextField(name, { name = it }, label = { Text("Jméno") }, singleLine = true)
                OutlinedTextField(nick, { nick = it }, label = { Text("Přezdívka") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name, nick) }) { Text("Přidat") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
}
