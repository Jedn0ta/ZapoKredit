package cz.klubpos.domain

import cz.klubpos.data.db.entity.Product

/** Sections of the Nabídka, in display order. */
enum class Kind(val code: String, val label: String, val emoji: String, val volumes: List<Int>) {
    DRAUGHT("DRAUGHT", "Čepované", "🍺", listOf(500, 400, 300)),
    POURED_ALCO("POURED_ALCO", "Rozlévané – alko", "🍷", listOf(100, 200, 250, 300, 500)),
    POURED_SOFT("POURED_SOFT", "Rozlévané – nealko", "🥤", listOf(200, 300, 400, 500)),
    SPIRIT("SPIRIT", "Lihoviny", "🥃", listOf(20, 40, 50)),
    OTHER("OTHER", "Ostatní", "🍿", emptyList());

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

val Product.kindEnum get() = Kind.of(kind)

/** "0,5 l", "0,2 l", "4 cl" – empty for pieces. */
fun volumeLabel(ml: Int): String = when {
    ml <= 0 -> ""
    ml < 100 -> "${ml / 10} cl".replace(".0", "")
    else -> (if (ml % 100 == 0) "${ml / 1000}," + "${(ml % 1000) / 100}" else "%.2f".format(java.util.Locale("cs", "CZ"), ml / 1000.0)) + " l"
}

/** Name with the offered volume, e.g. "Víno bílé 0,2 l". Draught names already say velké/malé. */
val Product.displayName: String
    get() = if (pourMl > 0 && kegGroup == null) "$name ${volumeLabel(pourMl)}" else name
