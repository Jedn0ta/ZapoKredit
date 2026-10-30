package cz.klubpos.ui.members

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.FilterChip
import cz.klubpos.domain.displayName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.klubpos.data.db.entity.Member
import cz.klubpos.domain.Credits
import cz.klubpos.domain.MemberStatus
import cz.klubpos.ui.components.CthulhuIcon
import cz.klubpos.ui.components.MemberAvatar
import cz.klubpos.ui.components.PIXEL_AVATARS
import cz.klubpos.ui.components.PixelAvatarImage
import cz.klubpos.ui.components.QrUploadDialog
import cz.klubpos.ui.components.TILE_TINTS
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.components.tileTint
import cz.klubpos.ui.theme.GoldMetal
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.glass
import kotlinx.coroutines.launch

/**
 * Everything about one member, opened by holding their tile:
 * quick actions, name, note, tile colour, VIP, Elder, contact, photo / gamer avatar, delete.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemberSettingsSheet(
    status: MemberStatus, pages: List<String>,
    onDismiss: () -> Unit, onPayAll: () -> Unit, onMoveToPage: (Int) -> Unit,
) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val member by repo.member(status.memberId).collectAsState(initial = null)
    val m0 = member ?: return

    var name by remember(m0.id) { mutableStateOf(m0.name) }
    var nick by remember(m0.id) { mutableStateOf(m0.nickname ?: "") }
    var note by remember(m0.id) { mutableStateOf(m0.note ?: "") }
    var phone by remember(m0.id) { mutableStateOf(m0.phone) }
    var email by remember(m0.id) { mutableStateOf(m0.email) }
    var tint by remember(m0.id) { mutableStateOf(m0.tileColor) }
    var vip by remember(m0.id) { mutableStateOf(m0.vip) }
    var elder by remember(m0.id) { mutableStateOf(m0.elder) }
    var avatar by remember(m0.id) { mutableStateOf(m0.avatar) }
    var jedi by remember(m0.id) { mutableStateOf(m0.jedi) }
    var qr by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pickFile = cz.klubpos.ui.components.rememberImagePicker { bytes -> repo.setMemberPhoto(status.memberId, bytes) }

    fun save(extra: (Member) -> Member = { it }) = scope.launch {
        val cur = repo.memberOnce(m0.id) ?: return@launch
        repo.updateMember(extra(cur.copy(
            name = name.trim().ifBlank { cur.name }, nickname = nick.trim().ifBlank { null }, note = note.trim().ifBlank { null },
            phone = phone.trim(), email = email.trim(), tileColor = tint, vip = vip, elder = elder, avatar = avatar, jedi = jedi,
        )))
    }
    fun go(i: Intent) = runCatching { context.startActivity(i) }

    ModalBottomSheet(onDismissRequest = { save(); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // ---- header + quick actions ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(m0.photoId, avatar, name, tileTint(tint), Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)), textSize = 20)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(nick.ifBlank { name }, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(if (status.balance > 0) "dluh ${Credits.format(status.balance)}" else "bez dluhu",
                        style = MaterialTheme.typography.bodySmall, color = if (status.balance > 0) KlubColors.OldDebt else KlubColors.Muted)
                }
                TextButton({ save(); onDismiss() }) { Text("Hotovo", fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallAction(if (status.inClub) "Odchod" else "Příchod") { scope.launch { if (status.inClub) repo.closeVisit(m0.id) else repo.openVisit(m0.id) }; save(); onDismiss() }
                if (status.balance > 0) SmallAction("Zaplatit vše") { save(); onPayAll() }
                if (phone.isNotBlank()) SmallAction("Zavolat") { go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
                if (phone.isNotBlank()) SmallAction("SMS") { go(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))) }
                if (email.isNotBlank()) SmallAction("E-mail") { go(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))) }
                if (pages.size > 1) pages.forEachIndexed { i, p ->
                    if (i != status.page.coerceIn(0, pages.lastIndex)) SmallAction("→ $p") { save(); onMoveToPage(i) }
                }
            }

            // ---- Údaje ----
            Section("Údaje", open = section == 0, onToggle = { section = if (section == 0) -1 else 0 }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Jméno") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(nick, { nick = it }, label = { Text("Přezdívka") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(phone, { phone = it }, label = { Text("Telefon") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                }
                OutlinedTextField(note, { note = it }, label = { Text("Poznámka") }, modifier = Modifier.fillMaxWidth())
            }

            // ---- Oblíbené a Klasika ----
            Section("Oblíbené a Klasika", open = section == 1, onToggle = { section = if (section == 1) -1 else 1 }) {
                FavoritesEditor(m0.id)
            }

            // ---- Vzhled ----
            Section("Vzhled dlaždice", open = section == 2, onToggle = { section = if (section == 2) -1 else 2 }) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorDot(null, tint == -1) { tint = -1 }
                    TILE_TINTS.forEachIndexed { i, c -> ColorDot(c, tint == i) { tint = i } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(vip, { vip = !vip }, label = { Text("VIP · zlatý rám") })
                    FilterChip(elder, { elder = !elder }, label = { Text("Elder · platina") })
                    FilterChip(jedi > 0, { jedi = if (jedi > 0) 0 else 1 }, label = { Text("Jedi · čepel") })
                }
                if (jedi > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cz.klubpos.ui.theme.SaberColors.forEachIndexed { i, c ->
                        Box(Modifier.size(30.dp).clip(CircleShape).background(c)
                            .border(if (jedi == i + 1) 3.dp else 0.dp, Color.White, CircleShape).clickable { jedi = i + 1 })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallAction("Fotka ze souboru") { pickFile() }
                    SmallAction("Z mobilu (QR)") { qr = true }
                    if (m0.photoId != null) TextButton({ scope.launch { repo.removeMemberPhoto(m0.id) } }) { Text("Odebrat fotku", color = KlubColors.OldDebt) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PIXEL_AVATARS.forEach { a ->
                        val sel = avatar == a.id
                        PixelAvatarImage(a, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                            .then(if (sel) Modifier.border(BorderStroke(3.dp, KlubColors.Gold), RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { avatar = if (sel) null else a.id })
                    }
                }
            }

            TextButton({ confirmDelete = true }) { Text("Smazat člena", color = KlubColors.OldDebt) }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (qr) QrUploadDialog("Fotka – ${nick.ifBlank { name }}", onImage = { repo.setMemberPhoto(m0.id, it) }) { qr = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Smazat ${nick.ifBlank { name }}?") },
        text = {
            Text((if (status.balance > 0) "Pozor, má dluh ${Credits.format(status.balance)}!\n" else "") +
                "Člen zmizí ze seznamu. Jeho historie a účty v uzávěrkách zůstanou zachované.")
        },
        confirmButton = { TextButton({ scope.launch { repo.updateMember(m0.copy(active = false)) }; confirmDelete = false; onDismiss() }) {
            Text("Smazat", color = KlubColors.OldDebt)
        } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Zpět") } },
    )
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Text(label, Modifier.clip(RoundedCornerShape(12.dp)).glass(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun Section(title: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = KlubColors.Hairline)
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(if (open) "▴" else "▾", color = KlubColors.Muted)
        }
        if (open) Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** Own favourites (empty = automatic) and "Klasika" combos of one member. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FavoritesEditor(memberId: Long) {
    val repo = rememberRepo()
    val store = cz.klubpos.ui.components.rememberMemberPrefs()
    val all by store.flow.collectAsState()
    val mp = all[memberId] ?: cz.klubpos.core.MemberPrefs()
    val products by repo.activeProducts().collectAsState(initial = emptyList())
    val top by remember(memberId) { repo.memberFavorites(memberId) }.collectAsState(initial = emptyList())
    var pickFor by remember { mutableStateOf<Int?>(null) }     // -1 = favourites, n = combo index
    fun name(id: Long) = products.firstOrNull { it.id == id }?.displayName ?: "?"

    Text(if (mp.favorites.isEmpty()) "Oblíbené – teď automaticky podle toho, co si dává nejčastěji" else "Oblíbené",
        style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
    val favIds = mp.favorites.ifEmpty { top.map { it.productId } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        favIds.forEach { id ->
            Text("${name(id)}  ×", Modifier.clip(RoundedCornerShape(10.dp)).background(KlubColors.SurfaceHigh)
                .clickable { store.update(memberId) { it.copy(favorites = favIds - id) } }.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        Text("+ přidat", Modifier.clickable { pickFor = -1 }.padding(horizontal = 6.dp, vertical = 6.dp), color = KlubColors.Gold,
            style = MaterialTheme.typography.bodySmall)
        if (mp.favorites.isNotEmpty()) Text("automaticky", Modifier.clickable { store.update(memberId) { it.copy(favorites = emptyList()) } }
            .padding(horizontal = 6.dp, vertical = 6.dp), color = KlubColors.Muted, style = MaterialTheme.typography.bodySmall)
    }

    Text("Klasika – oblíbená kombinace, v objednávce se přidá jedním klepnutím", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
    mp.combos.forEachIndexed { ci, c ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, KlubColors.Gold.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(c.name, { n -> store.update(memberId) { p -> p.copy(combos = p.combos.toMutableList().also { it[ci] = c.copy(name = n) }) } },
                    Modifier.weight(1f), singleLine = true,
                    textStyle = MaterialTheme.typography.titleSmall.copy(color = KlubColors.Gold),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(KlubColors.Gold))
                Text("smazat", Modifier.clickable { store.update(memberId) { p -> p.copy(combos = p.combos.filterIndexed { i, _ -> i != ci }) } }.padding(4.dp),
                    color = KlubColors.OldDebt, style = MaterialTheme.typography.labelSmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                c.items.forEachIndexed { ii, (id, q) ->
                    if (ii > 0) Text("+", Modifier.padding(horizontal = 2.dp, vertical = 4.dp), color = KlubColors.Gold, fontWeight = FontWeight.Bold)
                    fun setItems(f: (MutableList<Pair<Long, Double>>) -> Unit) = store.update(memberId) { p ->
                        p.copy(combos = p.combos.toMutableList().also { it[ci] = c.copy(items = c.items.toMutableList().also(f)) }) }
                    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(KlubColors.SurfaceHigh).padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("−", Modifier.clickable { setItems { l -> if (q > 1) l[ii] = id to q - 1 else l.removeAt(ii) } }.padding(horizontal = 4.dp), color = KlubColors.Muted)
                        Text((if (q != 1.0) "${q.toInt()}× " else "") + name(id), style = MaterialTheme.typography.bodySmall)
                        Text("+", Modifier.clickable { setItems { l -> l[ii] = id to q + 1 } }.padding(horizontal = 4.dp), color = KlubColors.Muted)
                    }
                }
                Text("+ nápoj", Modifier.clickable { pickFor = ci }.padding(horizontal = 6.dp, vertical = 4.dp), color = KlubColors.Gold, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text("+ nová Klasika", Modifier.clickable {
        store.update(memberId) { it.copy(combos = it.combos + cz.klubpos.core.Combo(if (it.combos.isEmpty()) "Klasika" else "Klasika ${it.combos.size + 1}", emptyList())) }
        pickFor = mp.combos.size
    }.padding(vertical = 6.dp), color = KlubColors.Gold)

    pickFor?.let { target ->
        ProductPicker(products, onDismiss = { pickFor = null }) { p ->
            if (target == -1) store.update(memberId) { it.copy(favorites = (favIds + p.id).distinct()) }
            else store.update(memberId) { mp2 -> mp2.copy(combos = mp2.combos.toMutableList().also { l ->
                if (target in l.indices) l[target] = l[target].copy(items = l[target].items + (p.id to 1.0)) }) }
            pickFor = null
        }
    }
}

@Composable
private fun ProductPicker(products: List<cz.klubpos.data.db.entity.Product>, onDismiss: () -> Unit, onPick: (cz.klubpos.data.db.entity.Product) -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Vyber nápoj") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text("Hledat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                    products.filter { q.isBlank() || it.displayName.contains(q, true) }.sortedBy { it.displayName.lowercase() }.forEach { p ->
                        Text(p.displayName, Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 10.dp))
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text("Zrušit") } })
}

@Composable
private fun ColorDot(c: Color?, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape)
        .then(if (c == null) Modifier.glass(CircleShape) else Modifier.background(c.copy(alpha = 0.55f)))
        .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else KlubColors.Hairline, CircleShape)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (c == null) Text("—", color = KlubColors.Muted)
    }
}

@Composable
private fun ToggleRow(value: Boolean, onChange: (Boolean) -> Unit, title: String, subtitle: String, icon: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable { onChange(!value) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        icon(); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
        }
        Switch(value, onChange)
    }
}
