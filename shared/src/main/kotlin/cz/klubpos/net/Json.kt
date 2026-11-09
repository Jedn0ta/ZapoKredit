package cz.klubpos.net

/**
 * Tiny JSON (no dependencies) for the LAN protocol between the bar tablet, the waiter phones and the PC.
 * Values: Map<String, Any?>, List<Any?>, String, Long, Double, Boolean, null.
 */
object Json {
    fun write(v: Any?): String = StringBuilder().also { w(it, v) }.toString()

    private fun w(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> str(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long, is Short -> sb.append(v.toString())
            is Number -> { val d = v.toDouble(); if (d % 1.0 == 0.0 && kotlin.math.abs(d) < 1e15) sb.append(d.toLong()) else sb.append(d) }
            is Map<*, *> -> {
                sb.append('{'); var first = true
                for ((k, x) in v) { if (!first) sb.append(','); first = false; str(sb, k.toString()); sb.append(':'); w(sb, x) }
                sb.append('}')
            }
            is Iterable<*> -> { sb.append('['); var first = true; for (x in v) { if (!first) sb.append(','); first = false; w(sb, x) }; sb.append(']') }
            is Array<*> -> w(sb, v.toList())
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\""); '\\' -> sb.append("\\\\"); '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r"); '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        sb.append('"')
    }

    fun parse(s: String): Any? = P(s).run { val v = value(); ws(); if (i != s.length) err("trailing data"); v }

    private class P(val s: String) {
        var i = 0
        fun err(m: String): Nothing = throw IllegalArgumentException("JSON: $m at $i")
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws(); if (i >= s.length) err("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj(); '[' -> arr(); '"' -> string()
                't' -> lit("true", true); 'f' -> lit("false", false); 'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else err("unexpected '$c'")
            }
        }
        fun lit(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) err("bad literal"); i += w.length; return v }
        fun num(): Any {
            val st = i; if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLong()
        }
        fun string(): String {
            i++; val sb = StringBuilder()
            while (true) {
                if (i >= s.length) err("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[i++]) {
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> err("bad escape $e")
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun obj(): Map<String, Any?> {
            i++; val m = LinkedHashMap<String, Any?>(); ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = string(); ws(); if (s[i++] != ':') err("expected :")
                m[k] = value(); ws()
                when (s[i++]) { ',' -> continue; '}' -> return m; else -> err("expected , or }") }
            }
        }
        fun arr(): List<Any?> {
            i++; val l = ArrayList<Any?>(); ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value(); ws()
                when (s[i++]) { ',' -> continue; ']' -> return l; else -> err("expected , or ]") }
            }
        }
    }
}

/** Convenience accessors on parsed objects. */
@Suppress("UNCHECKED_CAST")
fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()
@Suppress("UNCHECKED_CAST")
fun Any?.arr(): List<Any?> = this as? List<Any?> ?: emptyList()
fun Map<String, Any?>.str(k: String): String? = this[k] as? String
fun Map<String, Any?>.long(k: String): Long? = (this[k] as? Number)?.toLong()
fun Map<String, Any?>.int(k: String): Int? = (this[k] as? Number)?.toInt()
fun Map<String, Any?>.dbl(k: String): Double? = (this[k] as? Number)?.toDouble()
fun Map<String, Any?>.bool(k: String): Boolean = this[k] as? Boolean ?: false
