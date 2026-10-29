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
    var qr by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pickFile = cz.klubpos.ui.components.rememberImagePicker { bytes -> repo.setMemberPhoto(status.memberId, bytes) }

    fun save(extra: (Member) -> Member = { it }) = scope.launch {
        val cur = repo.memberOnce(m0.id) ?: return@launch
        repo.updateMember(extra(cur.copy(
            name = name.trim().ifBlank { cur.name }, nickname = nick.trim().ifBlank { null }, note = note.trim().ifBlank { null },
            phone = phone.trim(), email = email.trim(), tileColor = tint, vip = vip, elder = elder, avatar = avatar,
        )))
    }
    fun go(i: Intent) = runCatching { context.startActivity(i) }

    ModalBottomSheet(onDismissRequest = { save(); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ---- header ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(m0.photoId, avatar, name, tileTint(tint), Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)), textSize = 26)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(nick.ifBlank { name }, style = MaterialTheme.typography.headlineSmall)
                    Text(if (status.balance > 0) "dluh ${Credits.format(status.balance)}" else "bez dluhu",
                        color = if (status.balance > 0) KlubColors.OldDebt else KlubColors.Muted)
                }
            }
            // ---- quick actions ----
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ scope.launch { if (status.inClub) repo.closeVisit(m0.id) else repo.openVisit(m0.id) }; save(); onDismiss() }) {
                    Text(if (status.inClub) "Odchod" else "Příchod")
                }
                if (status.balance > 0) OutlinedButton({ save(); onPayAll() }) { Text("Zaplatit vše") }
                if (pages.size > 1) pages.forEachIndexed { i, p ->
                    if (i != status.page.coerceIn(0, pages.lastIndex)) OutlinedButton({ save(); onMoveToPage(i) }) { Text("→ $p") }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ---- names & note ----
            OutlinedTextField(name, { name = it }, label = { Text("Jméno") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nick, { nick = it }, label = { Text("Přezdívka (zobrazí se na dlaždici)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Poznámka") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            // ---- tile colour ----
            Text("Barva dlaždice", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorDot(null, tint == -1) { tint = -1 }
                TILE_TINTS.forEachIndexed { i, c -> ColorDot(c, tint == i) { tint = i } }
            }

            // ---- VIP / Elder ----
            ToggleRow(vip, { vip = it }, "VIP", "zlatý rám, lesk podle náklonu tabletu") {
                Box(Modifier.size(28.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(GoldMetal)))
            }
            ToggleRow(elder, { elder = it }, "Elder", "platinový rám – nejvyšší hodnost, má přednost před VIP") {
                Box(Modifier.size(28.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(cz.klubpos.ui.theme.PlatinumMetal)))
            }

            // ---- contact ----
            Text("Kontakt", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(phone, { phone = it }, label = { Text("Telefon") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                if (phone.isNotBlank()) {
                    IconButton({ go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }) { Icon(Icons.Default.Call, "Zavolat", tint = KlubColors.PaidToday) }
                    IconButton({ go(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))) }) { Icon(Icons.Default.Sms, "SMS", tint = KlubColors.InClub) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                if (email.isNotBlank()) IconButton({ go(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))) }) {
                    Icon(Icons.Default.Email, "E-mail", tint = KlubColors.Gold)
                }
            }

            // ---- picture ----
            Text("Obrázek", style = MaterialTheme.typography.labelLarge, color = KlubColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ pickFile() }) { Text("Vybrat soubor") }
                OutlinedButton({ qr = true }) { Text("Z mobilu (QR)") }
                if (m0.photoId != null) TextButton({ scope.launch { repo.removeMemberPhoto(m0.id) } }) { Text("Odebrat fotku", color = KlubColors.OldDebt) }
            }
            Text("…nebo herní avatar" + if (m0.photoId != null) " (zobrazí se po odebrání fotky)" else "",
                style = MaterialTheme.typography.bodySmall, color = KlubColors.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PIXEL_AVATARS.forEach { a ->
                    val sel = avatar == a.id
                    PixelAvatarImage(a, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                        .then(if (sel) Modifier.border(BorderStroke(3.dp, KlubColors.Gold), RoundedCornerShape(14.dp)) else Modifier)
                        .clickable { avatar = if (sel) null else a.id })
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ confirmDelete = true }) { Text("Smazat člena", color = KlubColors.OldDebt) }
                Spacer(Modifier.weight(1f))
                Button({ save(); onDismiss() }) { Text("Uložit") }
            }
            Spacer(Modifier.height(12.dp))
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
private fun ColorDot(c: Color?, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape)
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
