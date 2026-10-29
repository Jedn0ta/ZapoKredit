package cz.klubpos.core.io

import android.util.Xml
import cz.klubpos.data.db.entity.Product
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One row of the product import table. null = "not given, keep current value". */
data class ProductRow(
    val name: String,
    val category: String?,
    val price: Long?,
    val unit: String?,
    val trackStock: Boolean?,
    val stock: Double?,
    val minStock: Double?,
    val reorderQty: Double?,
    val kegGroup: String?,
    val pourMl: Int?,
)

/**
 * Minimal .xlsx (and .csv) reader/writer for the product list – no Apache POI needed.
 * Columns (header row, order doesn't matter, matched by name):
 *   Název | Kategorie | Cena | Jednotka | Sledovat sklad | Stav skladu | Minimum | Nákup | Sud | Objem čepu (ml)
 */
object ProductSheet {
    val HEADERS = listOf("Název", "Kategorie", "Cena", "Jednotka", "Sledovat sklad", "Stav skladu", "Minimum", "Nákup", "Sud", "Objem čepu (ml)")

    // ---------------- Reading ----------------

    fun read(input: InputStream, fileName: String): List<ProductRow> {
        val table = if (fileName.endsWith(".csv", true) || fileName.endsWith(".txt", true)) readCsv(input) else readXlsx(input)
        return toRows(table)
    }

    private fun norm(s: String) = java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]"), "")

    private fun toRows(table: List<List<String>>): List<ProductRow> {
        if (table.isEmpty()) return emptyList()
        val header = table.first().map(::norm)
        fun col(vararg names: String) = names.map(::norm).firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val cName = col("Název", "nazev", "name", "polozka") ?: 0
        val cCat = col("Kategorie", "typ", "category")
        val cPrice = col("Cena", "price", "kredity")
        val cUnit = col("Jednotka", "unit")
        val cTrack = col("Sledovat sklad", "sklad")
        val cStock = col("Stav skladu", "stav", "stock", "mnozstvi")
        val cMin = col("Minimum", "min")
        val cBuy = col("Nákup", "nakup", "obvykle nakupuji")
        val cKeg = col("Sud", "keg")
        val cPour = col("Objem čepu (ml)", "objem", "ml", "objemcepu")

        fun cell(r: List<String>, c: Int?) = c?.let { r.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
        fun num(r: List<String>, c: Int?) = cell(r, c)?.replace(" ", "")?.replace(',', '.')?.removeSuffix("Kč")?.removeSuffix("kr")?.toDoubleOrNull()
        fun bool(r: List<String>, c: Int?) = cell(r, c)?.let { norm(it) in setOf("ano", "a", "yes", "y", "1", "true", "x") }

        return table.drop(1).filter { r -> r.any { it.isNotBlank() } }.map { r ->
            ProductRow(
                name = cell(r, cName) ?: "",
                category = cell(r, cCat),
                price = num(r, cPrice)?.let { Math.round(it) },
                unit = cell(r, cUnit),
                trackStock = bool(r, cTrack),
                stock = num(r, cStock),
                minStock = num(r, cMin),
                reorderQty = num(r, cBuy),
                kegGroup = cell(r, cKeg),
                pourMl = num(r, cPour)?.toInt(),
            )
        }
    }

    private fun readCsv(input: InputStream): List<List<String>> {
        val text = input.bufferedReader(Charsets.UTF_8).readText().removePrefix("﻿")
        val sep = if ((text.lineSequence().firstOrNull()?.count { it == ';' } ?: 0) > 0) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>(); val cur = StringBuilder(); var quoted = false; var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && text.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == sep -> { row += cur.toString(); cur.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cur.toString(); cur.clear(); rows += row; row = mutableListOf()
                }
                else -> cur.append(ch)
            }
            i++
        }
        if (cur.isNotEmpty() || row.isNotEmpty()) { row += cur.toString(); rows += row }
        return rows
    }

    private fun readXlsx(input: InputStream): List<List<String>> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "xl/sharedStrings.xml" || e.name.startsWith("xl/worksheets/sheet")) files[e.name] = zip.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let(::parseShared) ?: emptyList()
        val sheet = files.keys.filter { it.startsWith("xl/worksheets/sheet") }
            .minByOrNull { it.filter(Char::isDigit).toIntOrNull() ?: 99 } ?: error("V souboru není žádný list")
        return parseSheet(files.getValue(sheet), shared)
    }

    private fun parser(bytes: ByteArray) = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(bytes.inputStream(), "UTF-8")
    }

    private fun parseShared(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>(); val p = parser(bytes); val sb = StringBuilder(); var inT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) { "si" -> sb.clear(); "t" -> inT = true }
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) { "t" -> inT = false; "si" -> out += sb.toString() }
            }
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (c in ref) { if (!c.isLetter()) break; n = n * 26 + (c.uppercaseChar() - 'A' + 1) }
        return n - 1
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>(); val p = parser(bytes)
        var row: MutableMap<Int, String>? = null
        var col = 0; var type: String? = null; val value = StringBuilder(); var inV = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = sortedMapOf()
                    "c" -> { col = p.getAttributeValue(null, "r")?.let(::colIndex) ?: (row?.size ?: 0); type = p.getAttributeValue(null, "t"); value.clear() }
                    "v", "t" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inV = false
                    "c" -> {
                        val raw = value.toString()
                        row?.put(col, when (type) {
                            "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "b" -> if (raw == "1") "ano" else "ne"
                            else -> raw.removeSuffix(".0")
                        })
                    }
                    "row" -> row?.let { r -> val max = (r.keys.maxOrNull() ?: -1); rows += (0..max).map { r[it] ?: "" } }
                }
            }
        }
        return rows
    }

    // ---------------- Writing ----------------

    /** Template = header + current products (or examples when empty). Opens in Excel / Google Sheets / LibreOffice. */
    fun writeXlsx(products: List<Product>): ByteArray {
        val rows = mutableListOf(HEADERS)
        val src = products.ifEmpty {
            listOf(
                Product(name = "Pivo 12° velké", category = "Pivo", price = 55, trackStock = false, kegGroup = "Pivo 12°", pourMl = 500),
                Product(name = "Pivo 12° malé", category = "Pivo", price = 35, trackStock = false, kegGroup = "Pivo 12°", pourMl = 300),
                Product(name = "Kofola", category = "Limo", price = 35, stockQty = 24.0, minStock = 12.0, reorderQty = 24.0),
                Product(name = "Rum 4 cl", category = "Alko", price = 70, trackStock = false),
                Product(name = "Utopenec", category = "Jídlo", price = 65, stockQty = 6.0, minStock = 4.0),
            )
        }
        fun n(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
        src.sortedWith(compareBy({ it.category }, { it.name })).forEach { p ->
            rows += listOf(p.name, p.category, p.price.toString(), p.unit, if (p.trackStock) "ano" else "ne",
                if (p.trackStock) n(p.stockQty) else "", if (p.trackStock) n(p.minStock) else "",
                if (p.reorderQty > 0) n(p.reorderQty) else "", p.kegGroup ?: "", if (p.pourMl > 0) p.pourMl.toString() else "")
        }

        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        fun ref(c: Int, r: Int) = "${'A' + c}${r + 1}"
        val numericCols = setOf(2, 5, 6, 7, 9)
        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
            append("<cols>")
            listOf(24, 14, 8, 10, 15, 12, 10, 10, 14, 16).forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            append("</cols><sheetData>")
            rows.forEachIndexed { r, cells ->
                append("""<row r="${r + 1}">""")
                cells.forEachIndexed { c, v ->
                    if (v.isEmpty()) return@forEachIndexed
                    val style = if (r == 0) """ s="1"""" else ""
                    if (r > 0 && c in numericCols && v.toDoubleOrNull() != null) append("""<c r="${ref(c, r)}"$style><v>$v</v></c>""")
                    else append("""<c r="${ref(c, r)}" t="inlineStr"$style><is><t>${esc(v)}</t></is></c>""")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        val files = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Položky" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
            "xl/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFF5C451"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""",
            "xl/worksheets/sheet1.xml" to sheet,
        )
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> files.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray(Charsets.UTF_8)); z.closeEntry() } }
        return bos.toByteArray()
    }
}
