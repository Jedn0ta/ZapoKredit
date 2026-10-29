@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package cz.klubpos.ui.members

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import cz.klubpos.ui.components.reorderableGrid
import cz.klubpos.ui.components.reorderItem
import androidx.compose.foundation.clickable
import cz.klubpos.ui.theme.glass
import cz.klubpos.ui.theme.goldFrame
import cz.klubpos.ui.theme.platinumFrame
import cz.klubpos.ui.theme.saberFrame
import cz.klubpos.ui.theme.SaberColors
import cz.klubpos.ui.components.MemberAvatar
import cz.klubpos.ui.components.CthulhuIcon
import cz.klubpos.ui.components.tileTint
import cz.klubpos.ui.theme.GoldMetal
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
import androidx.compose.material.icons.filled.SwapHoriz
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

private enum class Filter(val label: String) { ALL("Vše"), IN_CLUB("In The House"), DEBT("Dluh") }

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

    val shown = members
        .filter { when (filter) { Filter.ALL -> true; Filter.IN_CLUB -> it.inClub; Filter.DEBT -> it.hasDebt } }
        .filter { query.isBlank() || it.name.contains(query, true) || (it.nickname?.contains(query, true) == true) }

    val cfg by cz.klubpos.ui.components.rememberSettings().flow.collectAsState()
    val pages = cfg.memberPages
    // Cards are used when not searching/filtering; search & filters look through all cards.
    val paged = filter == Filter.ALL && query.isBlank()
    val pager = androidx.compose.foundation.pager.rememberPagerState { pages.size }
    var moveFor by remember { mutableStateOf<MemberStatus?>(null) }
    // Drag a tile onto a card tab to move the member there
    val tabRects = remember { androidx.compose.runtime.mutableStateMapOf<Int, androidx.compose.ui.geometry.Rect>() }
    var dragPointer by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val hoverTab = dragPointer?.let { p -> tabRects.entries.firstOrNull { it.value.contains(p) }?.key }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Minimal header: search + segmented filter
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchField(query, { query = it }, Modifier.weight(1f).tourTarget("search"))
            Segmented(Filter.entries.map { it.label }, filter.ordinal) { filter = Filter.entries[it] }
        }
        // Happy hour (only while a promo runs)
        cz.klubpos.ui.components.HappyHourStrip(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        // Beer kegs on tap – level at a glance
        cz.klubpos.ui.inventory.KegStrip(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp))
        Box(Modifier.weight(1f)) {
            if (paged) {
                Column(Modifier.fillMaxSize()) {
                    if (pages.size > 1) PageTabs(pages, pager.currentPage, hoverTab, onRect = { i, r -> tabRects[i] = r }) { i ->
                        scope.launch { pager.animateScrollToPage(i) }
                    }
                    androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }) { page ->
                        MemberGrid(
                            members = members.filter { it.page.coerceIn(0, pages.lastIndex) == page },
                            tileDp = cfg.memberTileDp, newbieDays = cfg.newbieDays, canReorder = true,
                            tourFirst = page == 0,
                            onOpen = onOpenMember, onActions = { actionsFor = it },
                            onSaveOrder = { ids -> scope.launch { repo.saveMemberOrder(ids) } },
                            onDragPointer = { dragPointer = it },
                            onDropAt = { id, pos ->
                                val target = tabRects.entries.firstOrNull { it.value.contains(pos) }?.key
                                if (target != null && target != page) {
                                    scope.launch { repo.setMemberPage(id, target); pager.animateScrollToPage(target) }; true
                                } else false
                            },
                        )
                    }
                }
            } else {
                MemberGrid(shown, cfg.memberTileDp, cfg.newbieDays, canReorder = false, tourFirst = true,
                    onOpen = onOpenMember, onActions = { actionsFor = it }, onSaveOrder = {})
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp)
                .shadow(20.dp, CircleShape, ambientColor = KlubColors.Gold, spotColor = KlubColors.Gold)
                .clip(CircleShape).background(KlubColors.GoldBrush).tourTarget("add_member")
                .combinedClickable(onClick = { showAdd = true }), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, "Nový člen", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    actionsFor?.let { m ->
        MemberSettingsSheet(m, pages,
            onDismiss = { actionsFor = null },
            onPayAll = { payAllFor = m; actionsFor = null },
            onMoveToPage = { i -> scope.launch { repo.setMemberPage(m.memberId, i); pager.animateScrollToPage(i) }; actionsFor = null },
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
    photoFor?.let { m -> QrUploadDialog("Fotka – ${m.nickname ?: m.name}", onImage = { repo.setMemberPhoto(m.memberId, it) }) { photoFor = null } }
    if (showAdd) AddMemberDialog(onDismiss = { showAdd = false }) { name, nick ->
        val page = if (paged) pager.currentPage else 0
        scope.launch { repo.addMember(name, nick, page) }; showAdd = false
    }
    moveFor?.let { m ->
        AlertDialog(
            onDismissRequest = { moveFor = null },
            title = { Text("Přesunout ${m.nickname ?: m.name} na kartu") },
            text = {
                Column {
                    pages.forEachIndexed { i, name ->
                        val here = m.page.coerceIn(0, pages.lastIndex) == i
                        Text((if (here) "● " else "") + name,
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = !here) {
                                scope.launch { repo.setMemberPage(m.memberId, i); pager.animateScrollToPage(i) }; moveFor = null
                            }.padding(14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (here) KlubColors.Gold else MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            confirmButton = { TextButton({ moveFor = null }) { Text("Zavřít") } },
        )
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
private fun MemberTile(m: MemberStatus, modifier: Modifier = Modifier, selected: Boolean = false, newbie: Boolean = false,
    compact: Boolean = false, horizontal: Boolean = false,
    holdRing: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.fillMaxWidth().aspectRatio(if (horizontal) 1.45f else 0.82f)
            // Elder (platinum) outranks VIP (gold): platinum outside, gold just inside when both
            .then(if (m.elder) Modifier.platinumFrame(shape) else Modifier)
            .then(if (m.vip) Modifier.padding(if (m.elder) 5.dp else 0.dp).goldFrame(if (m.elder) RoundedCornerShape(20.dp) else shape) else Modifier)
            // Jedi blade sits innermost when combined with the metal frames
            .then(if (m.jedi > 0) Modifier.padding(if (m.elder || m.vip) 5.dp else 0.dp)
                .saberFrame(if (m.elder || m.vip) RoundedCornerShape(18.dp) else shape, SaberColors[(m.jedi - 1).coerceIn(0, SaberColors.lastIndex)]) else Modifier)
            .clip(shape)
            .then(when {
                m.vip || m.elder || m.jedi > 0 -> Modifier
                selected -> Modifier.border(BorderStroke(4.dp, KlubColors.Amber), shape)
                else -> Modifier.border(1.dp, KlubColors.Hairline, shape)
            })
            .clickable(onClick = onClick)
    ) {
        if (horizontal) {
            // Tablet: picture fills the tile, big name centred over it
            MemberAvatar(m.photoId, m.avatar, m.name, tileTint(m.tileColor), Modifier.fillMaxSize(), textSize = if (compact) 30 else 44, showInitials = false)
            tileTint(m.tileColor)?.let { t ->
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.copy(alpha = 0.25f), Color.Transparent, t.copy(alpha = 0.3f)))))
            }
            // darken the middle band so the name is always readable
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.65f)))))
            if (!m.inClub) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(m.nickname ?: m.name, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium).copy(
                        fontWeight = FontWeight.Bold,
                        shadow = androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(0f, 2f), 8f)),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
        MemberAvatar(m.photoId, m.avatar, m.name, tileTint(m.tileColor), Modifier.fillMaxSize(), textSize = if (compact) 30 else 44, showInitials = false)
        // Glass tint of the member's colour
        tileTint(m.tileColor)?.let { t ->
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.copy(alpha = 0.28f), Color.Transparent, t.copy(alpha = 0.35f)))))
        }
        // Absent members are dimmed so present ones pop out.
        if (!m.inClub) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        // Name over gradient
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(10.dp, 24.dp, 10.dp, 10.dp)) {
            Column {
                Text(m.nickname ?: m.name, color = Color.White,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        }
        AmountBadge(m, Modifier.align(Alignment.TopEnd).padding(8.dp))
        // In The House = green dot, was here today but left = grey dot
        if (m.inClub || m.visitedToday) {
            val c = if (m.inClub) KlubColors.PaidToday else Color(0xFF8A8794)
            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(if (compact) 14.dp else 18.dp)
                .then(if (m.inClub) Modifier.shadow(10.dp, CircleShape, ambientColor = c, spotColor = c) else Modifier)
                .clip(CircleShape).background(c).border(2.dp, Color.Black.copy(alpha = 0.5f), CircleShape))
        }
        holdRing?.invoke(this)
        Column(Modifier.align(Alignment.TopStart).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (newbie) Text("NOVÁČEK", Modifier.clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(KlubColors.PaidToday, Color(0xFF2BB673)))).padding(horizontal = 7.dp, vertical = 3.dp),
                color = KlubColors.Ink, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold))
        }
        if (selected) Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(28.dp).clip(CircleShape).background(KlubColors.Amber),
            contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp)) }
    }
}

/** One number only. Orange = today's tab, red = old debt, green ✓ = paid today. Nothing when clear. */
@Composable
private fun AmountBadge(m: MemberStatus, modifier: Modifier) {
    val (text, color) = when {
        m.balance > 0 -> Credits.format(m.balance) to (if (m.todayUnpaid > 0) KlubColors.OpenTab else KlubColors.OldDebt)
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
    onToggleVisit: () -> Unit, onPayAll: () -> Unit, onPhoto: () -> Unit, onEdit: () -> Unit, onVip: () -> Unit,
    onMove: (() -> Unit)?,
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
            ListItem(headlineContent = { Text(if (m.vip) "Zrušit VIP" else "Nastavit jako VIP") },
                leadingContent = { Text("♛", fontSize = 20.sp, color = KlubColors.Gold) },
                colors = colors, modifier = Modifier.combinedClickable(onClick = onVip))
            if (onMove != null) ListItem(headlineContent = { Text("Přesunout na kartu…") },
                leadingContent = { Icon(Icons.Default.SwapHoriz, null) },
                colors = colors, modifier = Modifier.combinedClickable(onClick = onMove))
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

/** Card tabs above the member grid (also swipeable). */
@Composable
private fun PageTabs(pages: List<String>, current: Int, hover: Int?, onRect: (Int, androidx.compose.ui.geometry.Rect) -> Unit, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)
        .tourTarget("member_pages"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pages.forEachIndexed { i, name ->
            val sel = i == current
            val drop = hover == i && i != current
            Box(Modifier.height(40.dp)
                .onGloballyPositioned { onRect(i, it.boundsInRoot()) }
                .then(if (drop) Modifier.border(2.dp, KlubColors.InClub, RoundedCornerShape(14.dp)) else Modifier)
                .clip(RoundedCornerShape(14.dp))
                .then(if (sel) Modifier.background(KlubColors.GoldBrush) else Modifier.glass(RoundedCornerShape(14.dp)))
                .clickable { onSelect(i) }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text(name, color = if (sel) KlubColors.Ink else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium))
            }
        }
    }
}

/** Grid of member tiles with optional drag & drop ordering. */
@Composable
private fun MemberGrid(
    members: List<MemberStatus>, tileDp: Int, newbieDays: Int, canReorder: Boolean, tourFirst: Boolean,
    onOpen: (Long) -> Unit, onActions: (MemberStatus) -> Unit, onSaveOrder: (List<Long>) -> Unit,
    onDragPointer: (androidx.compose.ui.geometry.Offset?) -> Unit = {},
    onDropAt: ((Long, androidx.compose.ui.geometry.Offset) -> Boolean)? = null,
) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val reorder = cz.klubpos.ui.components.rememberGridReorderState(gridState)
    val local = remember { androidx.compose.runtime.mutableStateListOf<MemberStatus>() }
    LaunchedEffect(members) { if (reorder.draggingKey == null) { local.clear(); local.addAll(members) } }
    LaunchedEffect(reorder) { androidx.compose.runtime.snapshotFlow { reorder.pointer }.collect { onDragPointer(it) } }
    val gap = (tileDp / 11).coerceIn(8, 14).dp
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    // Tablet (wide): horizontal tiles – picture left, big name in the middle
    val horizontal = maxWidth >= 600.dp
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize().reorderableGrid(reorder,
            canDrag = { canReorder },
            onMove = { from, to ->
                val a = local.indexOfFirst { it.memberId == from }; val b = local.indexOfFirst { it.memberId == to }
                if (a >= 0 && b >= 0) local.add(b, local.removeAt(a))
            },
            onDrop = { onSaveOrder(local.map { it.memberId }) },
            onLongPress = { key -> local.firstOrNull { it.memberId == key }?.let(onActions) },
            onDropAt = onDropAt?.let { f -> { key, pos -> f(key as Long, pos) } },
            holdMillis = 800),
        columns = GridCells.Adaptive(if (horizontal) (tileDp * 1.45f).dp else tileDp.dp),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        itemsIndexed(local, key = { _, m -> m.memberId }) { i, m ->
            MemberTile(m, newbie = m.isNewbie(newbieDays), compact = tileDp < 120, horizontal = horizontal,
                holdRing = { cz.klubpos.ui.components.HoldRing(reorder, m.memberId, Modifier.matchParentSize()) },
                modifier = (if (i == 0 && tourFirst) Modifier.tourTarget("member_tile") else Modifier).reorderItem(reorder, m.memberId),
                onClick = { onOpen(m.memberId) })
        }
    }
    }
}
