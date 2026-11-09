package cz.klubpos.ui.products

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.ByteArrayOutputStream
import java.io.File

/** PC: drink list as A4 PDF (same layout as on the tablet), saved and opened for printing. */
fun shareMenuPdf(title: String, columns: List<List<PdfLine>>, scale: Float) {
    val doc = PDDocument()
    fun load(res: String, fallback: String): PDFont = runCatching {
        PDType0Font.load(doc, PdfLine::class.java.getResourceAsStream(res)!!)
    }.getOrElse { PDType0Font.load(doc, File(fallback)) }
    val regular = load("/font/manrope.ttf", "C:/Windows/Fonts/arial.ttf")
    val bold = runCatching { PDType0Font.load(doc, File("C:/Windows/Fonts/arialbd.ttf")) }.getOrDefault(regular)
    val w = PDRectangle.A4.width; val h = PDRectangle.A4.height
    val margin = 40f; val gap = 24f
    val s = scale.coerceIn(0.7f, 1.6f) * (if (columns.size >= 3) 0.85f else 1f)
    val lineH = 17f * s; val headH = 30f * s
    val colW = (w - 2 * margin - gap * (columns.size - 1)) / columns.size
    fun width(f: PDFont, size: Float, t: String) = runCatching { f.getStringWidth(t) / 1000f * size }.getOrDefault(t.length * size * 0.5f)
    val pos = IntArray(columns.size)
    var page = 0
    while (page == 0 || columns.indices.any { pos[it] < columns[it].size }) {
        val p = PDPage(PDRectangle.A4); doc.addPage(p)
        PDPageContentStream(doc, p).use { c ->
            fun text(f: PDFont, size: Float, x: Float, yTop: Float, t: String) {
                c.beginText(); c.setFont(f, size); c.newLineAtOffset(x, h - yTop); c.showText(t); c.endText()
            }
            var top = margin
            if (page == 0) { text(bold, 26f * s, margin, margin + 26f * s, title); top += 26f * s + 22f }
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
                            c.setNonStrokingColor(0.47f, 0.35f, 0.08f)
                            text(bold, 11f * s, x0, y - 10f * s, line.text.uppercase())
                            c.setNonStrokingColor(0f, 0f, 0f)
                            c.setStrokingColor(0.8f, 0.8f, 0.8f); c.setLineWidth(0.8f)
                            c.moveTo(x0, h - (y - 6f * s)); c.lineTo(x0 + colW, h - (y - 6f * s)); c.stroke()
                        }
                        is PdfLine.Item -> {
                            y += lineH
                            val volW = 34f * s
                            c.setNonStrokingColor(0.35f, 0.35f, 0.35f); text(regular, 10f * s, x0, y, line.volume); c.setNonStrokingColor(0f, 0f, 0f)
                            val pw = width(bold, 11f * s, line.price)
                            var name = line.name
                            while (name.length > 1 && width(regular, 11f * s, name) > colW - volW - pw - 10f) name = name.dropLast(2) + "…"
                            text(regular, 11f * s, x0 + volW, y, name)
                            text(bold, 11f * s, x0 + colW - pw, y, line.price)
                        }
                    }
                    pos[ci]++
                }
            }
        }
        page++
        if (page > 20) break
    }
    val out = ByteArrayOutputStream(); doc.save(out); doc.close()
    cz.klubpos.platform.shareFile(out.toByteArray(), "Napojovy-listek.pdf", "application/pdf", "Nápojový lístek – PDF")
}
