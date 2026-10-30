package cz.klubpos.ui.products

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

/** One printable line of the drink list. */
sealed class PdfLine {
    data class Header(val text: String) : PdfLine()
    data class Item(val volume: String, val name: String, val price: String) : PdfLine()
}

/**
 * Renders the drink list as an A4 PDF with the same columns as on screen and opens the share/print chooser.
 * Columns flow independently; a column that does not fit continues on the next page.
 */
fun shareMenuPdf(context: Context, title: String, columns: List<List<PdfLine>>, scale: Float) {
    val w = 595; val h = 842                       // A4 in points
    val margin = 40f; val gap = 24f
    val font = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, cz.klubpos.R.font.manrope) }.getOrNull()
    val s = scale.coerceIn(0.7f, 1.6f) * (if (columns.size >= 3) 0.85f else 1f)
    fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * s; this.color = color
        typeface = android.graphics.Typeface.create(font ?: android.graphics.Typeface.SERIF, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
    val titleP = paint(26f, true); val headP = paint(11f, true, Color.rgb(120, 90, 20)).apply { letterSpacing = 0.15f }
    val itemP = paint(11f); val volP = paint(10f, color = Color.DKGRAY); val priceP = paint(11f, true)
    val dotP = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.8f }
    val lineH = 17f * s; val headH = 30f * s
    val colW = (w - 2 * margin - gap * (columns.size - 1)) / columns.size

    val doc = PdfDocument()
    val pos = IntArray(columns.size)
    var page = 0
    while (page == 0 || columns.indices.any { pos[it] < columns[it].size }) {
        val pg = doc.startPage(PdfDocument.PageInfo.Builder(w, h, page + 1).create())
        val c = pg.canvas
        var top = margin
        if (page == 0) {
            c.drawText(title, margin, margin + 26f * s, titleP)
            top += 26f * s + 22f
        }
        columns.forEachIndexed { ci, col ->
            val x0 = margin + ci * (colW + gap)
            var y = top
            while (pos[ci] < col.size) {
                val line = col[pos[ci]]
                val need = if (line is PdfLine.Header) headH else lineH
                if (y + need > h - margin) break
                when (line) {
                    is PdfLine.Header -> {
                        y += headH
                        c.drawText(line.text.uppercase(), x0, y - 10f * s, headP)
                        c.drawLine(x0, y - 6f * s, x0 + colW, y - 6f * s, dotP)
                    }
                    is PdfLine.Item -> {
                        y += lineH
                        val volW = 34f * s
                        c.drawText(line.volume, x0, y, volP)
                        val pw = priceP.measureText(line.price)
                        val maxName = colW - volW - pw - 10f
                        var name = line.name
                        while (name.length > 1 && itemP.measureText(name) > maxName) name = name.dropLast(2) + "…"
                        c.drawText(name, x0 + volW, y, itemP)
                        var dx = x0 + volW + itemP.measureText(name) + 4f
                        while (dx < x0 + colW - pw - 4f) { c.drawCircle(dx, y - 2f, 0.6f, dotP); dx += 4f }
                        c.drawText(line.price, x0 + colW - pw, y, priceP)
                    }
                }
                pos[ci]++
            }
        }
        doc.finishPage(pg)
        page++
        if (page > 20) break
    }
    val f = File(context.cacheDir, "exports").apply { mkdirs() }.resolve("Napojovy-listek.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Nápojový lístek – PDF"))
}
