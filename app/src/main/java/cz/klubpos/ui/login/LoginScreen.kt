package cz.klubpos.ui.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.data.db.entity.Bartender
import cz.klubpos.ui.components.initials
import cz.klubpos.ui.components.rememberRepo
import cz.klubpos.ui.member.Keypad
import cz.klubpos.ui.theme.KlubColors
import cz.klubpos.ui.theme.Display
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun bartenderColor(i: Int) = KlubColors.Avatar[i.mod(KlubColors.Avatar.size)]

/** Lock screen: pick yourself, type your PIN. Shown whenever bartenders exist and nobody is logged in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen() {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val bartenders by repo.bartenders().collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<Bartender?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (error) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            for (x in listOf(24f, -24f, 16f, -16f, 8f, 0f)) shake.animateTo(x, tween(45))
            pin = ""; error = false
        }
    }
    LaunchedEffect(pin) {
        val b = selected ?: return@LaunchedEffect
        if (pin.length >= 4 && repo.login(b, pin)) { pin = "" }
        else if (pin.length >= 6) error = true
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("ZapoKredit", style = Display.copy(fontSize = 34.sp), color = KlubColors.Gold)
            val b = selected
            if (b == null) {
                Text("Kdo je za barem?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    bartenders.forEach { bt ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { selected = bt; pin = "" }.padding(8.dp)) {
                            Avatar(bt, 84)
                            Spacer(Modifier.height(8.dp))
                            Text(bt.name, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                Avatar(b, 96)
                Text(b.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                // PIN dots
                Row(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(maxOf(4, pin.length)) { i ->
                        Box(Modifier.size(16.dp).clip(CircleShape)
                            .then(if (i < pin.length) Modifier.background(KlubColors.GoldBrush)
                            else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)))
                    }
                }
                Keypad(Modifier.fillMaxWidth(),
                    onDigit = { d -> if (d.length == 1 && pin.length < 6) pin += d },
                    onBack = { pin = pin.dropLast(1) }, onClear = { pin = "" })
                TextButton({ selected = null; pin = "" }) { Text("Jiný barman") }
            }
        }
    }
}

@Composable
fun Avatar(b: Bartender, sizeDp: Int) {
    val c = bartenderColor(b.colorIndex)
    Box(Modifier.size(sizeDp.dp).clip(CircleShape)
        .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center) {
        Text(initials(b.name), fontSize = (sizeDp / 2.6).sp, fontWeight = FontWeight.Bold, color = KlubColors.Ink)
    }
}
