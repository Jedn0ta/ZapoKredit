package cz.klubpos.ui.products

/** One printable line of the drink list. */
sealed class PdfLine {
    data class Header(val text: String) : PdfLine()
    data class Item(val volume: String, val name: String, val price: String) : PdfLine()
}

