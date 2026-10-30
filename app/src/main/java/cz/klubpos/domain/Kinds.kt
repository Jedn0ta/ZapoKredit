package cz.klubpos.domain

import cz.klubpos.data.db.entity.Product

/** Sections of the drink menu (nápojový lístek), in display order. */
enum class Kind(val code: String, val label: String, val emoji: String, val volumes: List<Int>, val example: String) {
    DRAUGHT("DRAUGHT", "Točené pivo", "🍺", listOf(200, 300, 400, 500), "Plzeň 12°"),
    BOTTLED_BEER("BOTTLED_BEER", "Lahvové pivo", "🍾", listOf(330, 500), "Svijany Máz"),
    WINE("WINE", "Víno rozlévané", "🍷", listOf(100, 200, 250), "Ryzlink rýnský"),
    SPIRIT("SPIRIT", "Lihoviny", "🥃", listOf(20, 40, 50), "Božkov rum"),
    SOFT_TAP("SOFT_TAP", "Limonády točené / rozlévané", "🥤", listOf(200, 300, 400, 500), "Kofola točená"),
    SOFT_BOTTLE("SOFT_BOTTLE", "Limonády ve skle", "🧃", listOf(250, 330, 500), "Mattoni"),
    HOMEMADE("HOMEMADE", "Domácí limonády", "🍋", listOf(300, 400, 500), "Bezová s citronem"),
    HOT("HOT", "Teplé nápoje", "☕", emptyList(), "Espresso"),
    SMOKING("SMOKING", "Kuřácké potřeby", "", emptyList(), "Zapalovač"),
    CBD("CBD", "CBD", "", emptyList(), "CBD olej 10 %"),
    OTHER("OTHER", "Ostatní", "🍿", emptyList(), "Brambůrky");

    companion object {
        fun of(code: String?) = when (code) {
            "POURED_ALCO" -> WINE
            "POURED_SOFT" -> SOFT_TAP
            else -> entries.firstOrNull { it.code == code } ?: OTHER
        }
    }
}

val Product.kindEnum get() = Kind.of(kind)

/** "0,5 l", "0,25 l", "4 cl" – empty for pieces. */
fun volumeLabel(ml: Int): String = when {
    ml <= 0 -> ""
    ml < 100 -> if (ml % 10 == 0) "${ml / 10} cl" else "$ml ml"
    else -> (if (ml % 100 == 0) "${ml / 1000},${(ml % 1000) / 100}" else "%.2f".format(java.util.Locale("cs", "CZ"), ml / 1000.0)) + " l"
}

/** Drink name on the menu card. */
val Product.menuTitle: String get() = menuName ?: kegGroup ?: name

/** "šnyt 0,2 l", "0,5 l", or "" for pieces. */
val Product.servingLabel: String get() = listOfNotNull(servingName?.takeIf { it.isNotBlank() }, volumeLabel(pourMl).takeIf { it.isNotEmpty() }).joinToString(" ")

/** Full name for keys, cart and ledger, e.g. "Plzeň 12° šnyt 0,2 l". */
val Product.displayName: String get() = listOf(menuTitle, servingLabel).filter { it.isNotBlank() }.joinToString(" ")
