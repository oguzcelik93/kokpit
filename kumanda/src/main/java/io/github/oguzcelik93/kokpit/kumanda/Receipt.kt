package io.github.oguzcelik93.kokpit.kumanda

import java.time.LocalDate

/** What a fuel receipt says, as far as the text could be read. Values in the panel's form ("45,30"). */
data class ReceiptGuess(val liters: String?, val price: String?, val date: String?)

/**
 * `2.160,36` / `2160,36` / `2160.36` / `45,300` → number. With only dots, a dot followed by
 * exactly three digits is a thousands dot unless [threeDecimals] (litres may have three).
 */
fun parseAmount(text: String, threeDecimals: Boolean = false): Double? {
    val t = text.trim().trimStart('*').trim()
    if (t.isEmpty() || t.any { !it.isDigit() && it != '.' && it != ',' }) return null
    val lastDot = t.lastIndexOf('.')
    val lastComma = t.lastIndexOf(',')
    val normal = when {
        lastDot >= 0 && lastComma >= 0 -> {
            val decimal = if (lastDot > lastComma) '.' else ','
            val thousands = if (decimal == '.') ',' else '.'
            t.replace(thousands.toString(), "").replace(decimal, '.')
        }
        lastComma >= 0 -> if (t.count { it == ',' } == 1) t.replace(',', '.') else return null
        lastDot >= 0 -> {
            val decimals = t.length - lastDot - 1
            if (t.count { it == '.' } > 1 || (decimals == 3 && !threeDecimals)) t.replace(".", "") else t
        }
        else -> t
    }
    return normal.toDoubleOrNull()
}

private fun fmt(v: Double): String = "%.2f".format(java.util.Locale.US, v).replace('.', ',')

/** Upper case without Turkish dotted/dotless trouble: İ, I, ı, i all become I. */
private fun fold(text: String): String = text.uppercase(java.util.Locale.ROOT)
    .replace('İ', 'I').replace('Ş', 'S').replace('Ğ', 'G').replace('Ü', 'U').replace('Ö', 'O').replace('Ç', 'C')

private val AMOUNT = Regex("""\*?\s*(\d{1,3}(?:\.\d{3})+,\d{2}|\d{1,3}(?:,\d{3})+\.\d{2}|\d+[.,]\d{2})(?!\d)""")
private val LITERS = Regex("""(\d{1,3}[.,]\d{1,3})\s*(?:LT|LITRE|LIT|L)\b""")
private val LITERS_AFTER = Regex("""(?:MIKTAR|LITRE|HACIM)\s*[:=]?\s*(\d{1,3}[.,]\d{1,3})""")
private val UNIT_PRICE = Regex("""(\d{1,3}[.,]\d{1,3})\s*(?:LT|LITRE|L)?\s*[X×*]\s*(\d{1,4}[.,]\d{1,4})""")
private val DATE = Regex("""(?<!\d)(\d{2})[./-](\d{2})[./-](\d{4})(?!\d)""")

/**
 * Reads litres, total and date from a Turkish fuel receipt's text (as OCR gives it). Litres:
 * "45,30 LT", "MİKTAR 45.30"; total: the "TOPLAM" line (not KDV), else "TUTAR", else litres ×
 * unit price; date: the first real dd.mm.yyyy. A total that would make an absurd litre price
 * is dropped, so a wrong guess never silently lands in the log. [today] bounds the date.
 */
fun parseReceipt(text: String, today: LocalDate): ReceiptGuess {
    val lines = text.lines().map(::fold)
    val all = lines.joinToString("\n")

    val liters = (LITERS.findAll(all).map { it.groupValues[1] } + LITERS_AFTER.findAll(all).map { it.groupValues[1] })
        .mapNotNull { parseAmount(it, threeDecimals = true) }
        .firstOrNull { it in 1.0..200.0 }

    fun amountOn(i: Int): Double? {
        val here = AMOUNT.findAll(lines[i]).lastOrNull()?.groupValues?.get(1)
        val next = lines.getOrNull(i + 1)?.let { AMOUNT.findAll(it).lastOrNull()?.groupValues?.get(1) }
        return (here ?: next)?.let { parseAmount(it) }
    }
    fun total(word: String): Double? = lines.indices
        .filter { word in lines[it] && "KDV" !in lines[it] && "ARA" !in lines[it] }
        .firstNotNullOfOrNull(::amountOn)

    var price = total("TOPLAM") ?: total("TUTAR")
    if (price == null) {
        UNIT_PRICE.find(all)?.let { m ->
            val l = parseAmount(m.groupValues[1], threeDecimals = true)
            val unit = parseAmount(m.groupValues[2], threeDecimals = true)
            if (l != null && unit != null) price = l * unit
        }
    }
    // A litre costs somewhere between a few and a few hundred lira.
    if (price != null && liters != null && (price!! / liters) !in 5.0..500.0) price = null
    if (price != null && price!! !in 1.0..100_000.0) price = null

    val date = DATE.findAll(all).mapNotNull { m ->
        runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
    }.firstOrNull { !it.isAfter(today) && !it.isBefore(today.minusDays(60)) }

    return ReceiptGuess(liters?.let(::fmt), price?.let(::fmt), date?.toString())
}
