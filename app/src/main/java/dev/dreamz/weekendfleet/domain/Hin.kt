package dev.dreamz.weekendfleet.domain

/**
 * Hull Identification Numbers (boats, PWC). Decoded entirely on the phone: a HIN is never sent
 * anywhere. The first 3 characters are the USCG Manufacturer Identification Code (MIC).
 */
object HinParser {

    sealed interface Result {
        data class Valid(val hin: String, val mic: String, val modelYear: Int?) : Result
        data class BadFormat(val hin: String, val reason: String) : Result
    }

    const val LENGTH = 12

    private val ALNUM = Regex("^[A-Z0-9]+$")

    /** Uppercase, spaces and hyphens removed, and a 2-letter country prefix dropped ("US-ABC12345D404"). */
    fun normalize(raw: String): String {
        val s = raw.uppercase().filterNot { it.isWhitespace() || it == '-' }
        return if (s.length == LENGTH + 2 && s[0].isLetter() && s[1].isLetter()) s.drop(2) else s
    }

    fun parse(raw: String, currentYear: Int): Result {
        val hin = normalize(raw)
        if (hin.length != LENGTH) return Result.BadFormat(hin, "A HIN has $LENGTH characters; this one has ${hin.length}.")
        if (!ALNUM.matches(hin)) return Result.BadFormat(hin, "HINs use only letters and digits.")
        return Result.Valid(hin, hin.take(3), modelYear(hin, currentYear))
    }

    /**
     * Model year from the format used since August 1984: character 9 a letter A–L (the month) and
     * characters 10–12 digits, with 11–12 the model year. Older formats give null.
     */
    fun modelYear(hin: String, currentYear: Int): Int? {
        if (hin.length != LENGTH || hin[8] !in 'A'..'L' || !hin.substring(9).all(Char::isDigit)) return null
        val yy = hin.substring(10).toInt()
        val nextYear = currentYear + 1
        val year = if (2000 + yy <= nextYear) 2000 + yy else 1900 + yy
        return year.takeIf { it in 1984..nextYear }
    }
}

/** USCG MIC -> manufacturer. [brand] (an override or the table's own column) wins over [company]. */
data class MicEntry(val mic: String, val company: String, val brand: String? = null)

class MicTable(private val entries: Map<String, MicEntry>) {
    val size: Int get() = entries.size

    fun lookup(mic: String): MicEntry? = entries[mic.uppercase()]

    companion object {
        /**
         * [table] is the fetched USCG list (`mic,company[,brand]`); [overrides] is the hand-kept
         * `mic,brand` file, merged on top so re-fetching the table never wipes them.
         */
        fun load(table: String, overrides: String?): MicTable {
            val map = HashMap<String, MicEntry>()
            for (row in csvRows(table).drop(1)) {
                val mic = row.getOrNull(0)?.trim()?.uppercase().orEmpty()
                val company = row.getOrNull(1)?.trim().orEmpty()
                if (mic.length != 3 || company.isEmpty()) continue
                map[mic] = MicEntry(mic, company, row.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() })
            }
            if (overrides != null) for (row in csvRows(overrides).drop(1)) {
                val mic = row.getOrNull(0)?.trim()?.uppercase().orEmpty()
                val brand = row.getOrNull(1)?.trim().orEmpty()
                if (mic.length != 3 || brand.isEmpty()) continue
                map[mic] = map[mic]?.copy(brand = brand) ?: MicEntry(mic, brand, brand)
            }
            return MicTable(map)
        }

        /** RFC 4180: quoted cells may hold commas, quotes ("") and line breaks. */
        internal fun csvRows(text: String): List<List<String>> {
            val rows = ArrayList<List<String>>()
            var row = ArrayList<String>()
            val cell = StringBuilder()
            var quoted = false
            var i = 0
            val s = text.removePrefix("﻿")
            while (i < s.length) {
                val c = s[i]
                when {
                    quoted && c == '"' && s.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                    quoted && c == '"' -> quoted = false
                    quoted -> cell.append(c)
                    c == '"' -> quoted = true
                    c == ',' -> { row += cell.toString(); cell.clear() }
                    c == '\n' || c == '\r' -> {
                        if (c == '\r' && s.getOrNull(i + 1) == '\n') i++
                        row += cell.toString(); cell.clear()
                        if (row.any { it.isNotEmpty() }) rows += row
                        row = ArrayList()
                    }
                    else -> cell.append(c)
                }
                i++
            }
            row += cell.toString()
            if (row.any { it.isNotEmpty() }) rows += row
            return rows
        }
    }
}

/** Turning a MIC entry into the Make field's text. MIC names only; VIN decodes match as before. */
object MicMake {
    /** Legal-entity endings: dropped from the text that goes into Make. */
    private val LEGAL = setOf("inc", "llc", "corp", "co", "ltd")

    /** Ignored when comparing a company name with the make list (legal endings plus trade words). */
    private val MATCH_IGNORED = setOf(
        "inc", "llc", "corp", "corporation", "co", "company", "ltd", "limited", "mfg", "manufacturing",
        "boats", "boat", "marine", "yachts",
    )

    /** The make list's spelling when the brand or company matches one, else the cleaned-up company name. */
    fun make(entry: MicEntry, knownMakes: List<String>): String {
        entry.brand?.let { brand ->
            return knownMakes.firstOrNull { VinFill.matchKey(it) == VinFill.matchKey(brand) } ?: brand
        }
        val key = matchKey(entry.company)
        return knownMakes.firstOrNull { matchKey(it) == key } ?: VinFill.titleCase(stripLegal(entry.company))
    }

    /** Comparison key: trailing [MATCH_IGNORED] words removed (repeatedly, never down to nothing), then letters/digits only. */
    fun matchKey(name: String): String = VinFill.matchKey(stripTrailing(name, MATCH_IGNORED))

    /** "SEA FOX BOAT COMPANY, INC." -> "SEA FOX BOAT COMPANY": legal endings only; Boats / Marine stay. */
    fun stripLegal(name: String): String = stripTrailing(name, LEGAL)

    private fun stripTrailing(name: String, endings: Set<String>): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        // Bare punctuation words ("&", "-") count as endings too, so "X MARINE & CO" loses all three.
        while (words.size > 1 && VinFill.matchKey(words.last()).let { it.isEmpty() || it in endings }) {
            words.removeAt(words.lastIndex)
        }
        return words.joinToString(" ").trimEnd(',', ';', '&', '-', ' ')
    }
}

/** HIN -> blank-only fill. A HIN never carries the model, so Model is always left to the owner. */
object HinFill {
    fun fill(year: String, make: String, modelYear: Int?, entry: MicEntry?, knownMakes: List<String>): VinFill.Fill =
        VinFill.Fill(
            year = modelYear.takeIf { year.isBlank() },
            make = entry?.let { MicMake.make(it, knownMakes) }.takeIf { make.isBlank() },
        )
}
