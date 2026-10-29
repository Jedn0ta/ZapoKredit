package cz.klubpos.ui.tour

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cz.klubpos.ui.theme.KlubColors
import kotlin.math.roundToInt

enum class Gesture { NONE, TAP, HOLD, SWIPE_RIGHT, SWIPE_LEFT }

/** Screen the step lives on. MEMBER = order screen of the first member. */
enum class TourScreen { MEMBERS, MEMBER, INVENTORY, SHOPPING, PRODUCTS, SETTINGS }

data class TourStep(
    val screen: TourScreen,
    val target: String?,          // id used in Modifier.tourTarget(); null = centred card, no spotlight
    val title: String,
    val text: String,
    val gesture: Gesture = Gesture.NONE,
)

/** All functions of the app, in the order a new bartender needs them. */
val TOUR_STEPS = listOf(
    TourStep(TourScreen.MEMBERS, null, "Vítej v ZapoKreditu",
        "Pokladna pro klub. Vše se počítá v kreditech, 1 kr = 1 Kč. Průvodce ti ukáže všechny funkce přímo v aplikaci."),
    TourStep(TourScreen.MEMBERS, "member_tile", "Členové",
        "Každý člen má dlaždici. Modrý rámeček = je v klubu. Číslo vpravo nahoře: oranžová = dnešní útrata, červená = dluh z minula, zelená ✓ = dnes zaplaceno.\nKlepnutím otevřeš jeho účet.",
        Gesture.TAP),
    TourStep(TourScreen.MEMBERS, "member_tile", "Rychlé akce",
        "Podrž dlaždici: příchod / odchod, zaplatit vše, fotka, úprava.", Gesture.HOLD),
    TourStep(TourScreen.MEMBERS, "round_button", "Runda",
        "Platí se runda? Klepni sem, označ víc členů a jednu položku zapíšeš všem najednou."),
    TourStep(TourScreen.MEMBERS, "search", "Hledání a filtr",
        "Napiš pár písmen jména nebo přezdívky. Vedle přepneš Vše / V klubu / Dluh."),
    TourStep(TourScreen.MEMBERS, "add_member", "Nový člen", "Přidá člena. Fotku nahraješ přes QR kód z mobilu (podržením dlaždice → Fotka)."),

    TourStep(TourScreen.MEMBER, "product_key", "Zapsat položku",
        "Klepnutí = 1 kus na účet. Číslo v rohu ukazuje, kolik toho má dnes.", Gesture.TAP),
    TourStep(TourScreen.MEMBER, "product_key", "Víc kusů nebo jiná cena",
        "Podrž položku a nastav množství nebo cenu.", Gesture.HOLD),
    TourStep(TourScreen.MEMBER, "favorites", "Oblíbené a To samé",
        "Nahoře jsou nejprodávanější položky. „↻ To samé“ zopakuje poslední objednávku člena jedním klepnutím."),
    TourStep(TourScreen.MEMBER, "custom_amount", "Vlastní částka", "Něco, co není v nabídce? Zadej částku na klávesnici."),
    TourStep(TourScreen.MEMBER, "receipt", "Účtenka – storno",
        "Táhni řádek doleva = storno 1 kusu (jde vrátit tlačítkem Vrátit).", Gesture.SWIPE_LEFT),
    TourStep(TourScreen.MEMBER, "receipt", "Účtenka – přidat a přesunout",
        "Táhni doprava = ještě jeden kus. Podržením řádku ho přesuneš na účet jiného člena.", Gesture.SWIPE_RIGHT),
    TourStep(TourScreen.MEMBER, "pay_button", "Placení",
        "Klepni: vybereš částku, hotově (pokladna spočítá, kolik vrátit) nebo QR kódem do banky.\nPodrž: zaplatí vše hotově hned.",
        Gesture.HOLD),
    TourStep(TourScreen.MEMBER, "history", "Historie", "Všechny pohyby účtu. I starší záznam stornuješ tažením doleva."),

    TourStep(TourScreen.INVENTORY, "stock_row", "Sklad",
        "Červené číslo = dochází. Klepnutím zadáš skutečný stav (inventura).", Gesture.TAP),
    TourStep(TourScreen.INVENTORY, "stock_row", "Naskladnit / do nákupu",
        "Táhni doprava = naskladnit dovezené zboží. Doleva = přidat na nákupní seznam.", Gesture.SWIPE_RIGHT),
    TourStep(TourScreen.SHOPPING, "autofill", "Nákupní seznam",
        "Tímto tlačítkem doplníš vše, co dochází, i s navrženým množstvím. Vedle seznam pošleš např. WhatsAppem."),
    TourStep(TourScreen.SHOPPING, "shopping_row", "Koupeno",
        "Táhni doprava = koupeno a rovnou naskladněno. Doleva = smazat. Klepnutím na množství ho změníš.", Gesture.SWIPE_RIGHT),
    TourStep(TourScreen.PRODUCTS, null, "Položky",
        "Tady spravuješ nabídku: ceny, kategorie (barva tlačítka), minimum skladu a obrázky přes QR."),
    TourStep(TourScreen.SETTINGS, "close_day", "Uzávěrka",
        "Na konci večera: tržba, hotovost, QR platby, kdo dluží. Tlačítkem Ukončit večer zavřeš všem účty."),
    TourStep(TourScreen.SETTINGS, "payment_settings", "Platby a limity",
        "Zadej číslo účtu pro QR platby a limit dluhu – při jeho překročení tě aplikace upozorní."),
    TourStep(TourScreen.SETTINGS, null, "Hotovo",
        "Průvodce kdykoli spustíš znovu v Nastavení → Průvodce aplikací."),
)

@Stable
class TourController {
    var index by mutableIntStateOf(-1); private set
    val targets = mutableStateMapOf<String, Rect>()
    val active get() = index in TOUR_STEPS.indices
    val step get() = TOUR_STEPS.getOrNull(index)

    fun start() { index = 0 }
    fun next() { index = if (index + 1 < TOUR_STEPS.size) index + 1 else -1 }
    fun back() { if (index > 0) index-- }
    fun stop() { index = -1 }
}

val LocalTour = compositionLocalOf { TourController() }

/** Marks an element the tour can point at. The first registered element with a given id wins per frame. */
fun Modifier.tourTarget(id: String): Modifier = composed {
    val tour = LocalTour.current
    onGloballyPositioned { tour.targets[id] = it.boundsInRoot() }
}

/** Dim overlay with a pulsing spotlight, an animated finger showing the gesture and an explanation card. */
@Composable
fun TourOverlay(tour: TourController) {
    val step = tour.step ?: return
    val rect = step.target?.let { tour.targets[it] }
    val density = LocalDensity.current
    val inf = rememberInfiniteTransition(label = "tour")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "pulse")
    val gestureT by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "gesture")

    BoxWithConstraints(Modifier.fillMaxSize()
        // swallow touches so the user can't break the app mid-tour
        .clickable(interactionSource = remember0(), indication = null) {}) {
        val pad = with(density) { 8.dp.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            if (rect != null) {
                val r = Rect(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
                drawRoundRect(Color.Transparent, r.topLeft, r.size, CornerRadius(24f), blendMode = BlendMode.Clear)
                // pulsing ring
                val grow = pulse * with(density) { 14.dp.toPx() }
                drawRoundRect(KlubColors.Amber.copy(alpha = 1f - pulse),
                    Offset(r.left - grow, r.top - grow), androidx.compose.ui.geometry.Size(r.width + 2 * grow, r.height + 2 * grow),
                    CornerRadius(24f + grow), style = Stroke(width = with(density) { 3.dp.toPx() }))
                drawRoundRect(KlubColors.Amber, r.topLeft, r.size, CornerRadius(24f), style = Stroke(width = with(density) { 3.dp.toPx() }))
            }
        }

        // Animated finger
        if (rect != null && step.gesture != Gesture.NONE) {
            val c = rect.center
            val travel = rect.width * 0.35f
            val (dx, scale, alpha) = when (step.gesture) {
                Gesture.TAP -> Triple(0f, if (gestureT < 0.25f) 0.75f else 1f, if (gestureT < 0.6f) 1f else 0f)
                Gesture.HOLD -> Triple(0f, if (gestureT < 0.8f) 0.8f else 1f, 1f)
                Gesture.SWIPE_RIGHT -> Triple(-travel + 2 * travel * gestureT, 0.85f, if (gestureT > 0.85f) 0f else 1f)
                Gesture.SWIPE_LEFT -> Triple(travel - 2 * travel * gestureT, 0.85f, if (gestureT > 0.85f) 0f else 1f)
                Gesture.NONE -> Triple(0f, 1f, 0f)
            }
            val sizePx = with(density) { 44.dp.toPx() }
            Box(Modifier
                .offset { IntOffset((c.x + dx - sizePx / 2).roundToInt(), (c.y - sizePx / 2).roundToInt()) }
                .size(44.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                .clip(CircleShape).background(Color.White.copy(alpha = 0.85f))
                .padding(10.dp).clip(CircleShape).background(KlubColors.Amber))
            if (step.gesture == Gesture.HOLD) {
                // progress ring = "keep holding"
                Canvas(Modifier.offset { IntOffset((c.x - sizePx).roundToInt(), (c.y - sizePx).roundToInt()) }
                    .size(with(density) { (sizePx * 2).toDp() })) {
                    drawArc(Color.White, -90f, 360f * gestureT.coerceAtMost(0.8f) / 0.8f, false,
                        style = Stroke(width = with(density) { 3.dp.toPx() }))
                }
            }
        }

        // Card: below the target if there is room, otherwise above; centred when no target
        val screenH = constraints.maxHeight.toFloat()
        val below = rect == null || rect.bottom < screenH * 0.6f
        val cardModifier = Modifier.align(if (rect == null) Alignment.Center else if (below) Alignment.BottomCenter else Alignment.TopCenter)
            .padding(20.dp).padding(vertical = 24.dp).widthIn(max = 460.dp).fillMaxWidth()
        Column(cardModifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${tour.index + 1} / ${TOUR_STEPS.size}", style = MaterialTheme.typography.labelMedium, color = KlubColors.Amber)
            Text(step.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(step.text, style = MaterialTheme.typography.bodyLarge)
            if (step.target != null && rect == null) Text("(Prvek se načítá…)", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { tour.stop() }) { Text("Ukončit") }
                Spacer(Modifier.weight(1f))
                if (tour.index > 0) TextButton(onClick = { tour.back() }) { Text("Zpět") }
                Button(onClick = { tour.next() }) { Text(if (tour.index == TOUR_STEPS.lastIndex) "Hotovo" else "Další") }
            }
        }
    }
}

@Composable
private fun remember0() = androidx.compose.runtime.remember { MutableInteractionSource() }
