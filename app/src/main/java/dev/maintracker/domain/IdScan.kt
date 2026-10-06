package dev.maintracker.domain

/**
 * Turns what the camera reads (barcodes, recognised text) into VIN / HIN candidates. Pure, so the
 * scanner screen only feeds it frames. A [Candidate] is [Candidate.verified] when it passes a check
 * that random misreads rarely pass: the VIN check digit, or a HIN with a model year and a known
 * USCG maker code. Unverified ones need more sightings before [ScanStabilizer] accepts them.
 */
object IdScan {

    data class Candidate(val value: String, val verified: Boolean)

    /** VINs and HINs never use I, O or Q; OCR's usual confusion is with 1 and 0. */
    private fun fixLookalikes(s: String): String =
        s.uppercase().map { when (it) { 'I' -> '1'; 'O', 'Q' -> '0'; else -> it } }.joinToString("")

    private val VIN_RUN = Regex("[A-HJ-NPR-Z0-9]{17}")
    private val HIN_CHARS = Regex("^[A-Z0-9]+$")

    /**
     * A VIN barcode's raw value (Code 39 on door jambs, Data Matrix on newer labels). Some carry a
     * leading "I" (import) or trailing filler; take the check-digit-valid 17 if there is one, else
     * an exact 17 (barcodes don't misread, and non-North-American VINs skip the check digit).
     */
    fun vinFromBarcode(raw: String): String? {
        val s = VinValidator.normalize(raw)
        val windows = (0..s.length - VinValidator.LENGTH).map { s.substring(it, it + VinValidator.LENGTH) }
            .filter { VIN_RUN.matches(it) }
        return windows.firstOrNull { it[8] == VinValidator.checkDigit(it) }
            ?: s.takeIf { it.length == VinValidator.LENGTH && VIN_RUN.matches(it) }
    }

    /** Format, plus the serial's last 3 characters, which are digits for every manufacturer. */
    private fun looksLikeVin(w: String): Boolean = VIN_RUN.matches(w) && w.takeLast(3).all(Char::isDigit)

    /** VIN candidates in one line of recognised text. */
    fun vinCandidates(line: String): List<Candidate> {
        val out = LinkedHashMap<String, Boolean>()
        for (joined in joinedTokens(line)) {
            val s = fixLookalikes(joined)
            if (s.length < VinValidator.LENGTH) continue
            // A checked VIN may be glued to a label on either side ("VIN1HGCM82633A004352"), so try
            // the run's first and last 17. Not every window: 1 in 11 random ones passes the check.
            for (w in setOf(s.take(VinValidator.LENGTH), s.takeLast(VinValidator.LENGTH))) {
                if (looksLikeVin(w) && w[8] == VinValidator.checkDigit(w)) out[w] = true
            }
            // Unchecked ones only as a whole token run, so neighbouring words can't slide in.
            if (s.length == VinValidator.LENGTH && looksLikeVin(s)) out.putIfAbsent(s, false)
        }
        return out.map { (v, ok) -> Candidate(v, ok) }
    }

    /** HIN candidates in one line of recognised text; [knownMic] is the USCG table lookup. */
    fun hinCandidates(line: String, currentYear: Int, knownMic: (String) -> Boolean): List<Candidate> {
        val out = LinkedHashMap<String, Boolean>()
        for (joined in joinedTokens(line)) {
            val raw = HinParser.normalize(joined)
            if (raw.length != HinParser.LENGTH || !HIN_CHARS.matches(raw)) continue
            // The serial never uses I, O or Q; maker codes can, and a few have digits, so the MIC
            // takes whichever 0/O and 1/I reading the USCG table knows.
            val mic = micReadings(raw.take(3)).firstOrNull(knownMic)
            val hin = (mic ?: raw.take(3)) + fixLookalikes(raw.drop(3))
            val dated = HinParser.modelYear(hin, currentYear) != null
            // Any 12-character word would pass the format alone.
            if (mic == null && !dated) continue
            val verified = mic != null && dated
            out[hin] = (out[hin] ?: false) || verified
        }
        return out.map { (v, ok) -> Candidate(v, ok) }
    }

    /** [mic] as read first, then every 0/O and 1/I swap of it. */
    private fun micReadings(mic: String): List<String> {
        var out = listOf("")
        for (c in mic) {
            val alts = when (c) { '0', 'O' -> listOf(c, if (c == '0') 'O' else '0'); '1', 'I' -> listOf(c, if (c == '1') 'I' else '1'); else -> listOf(c) }
            out = out.flatMap { p -> alts.map { p + it } }
        }
        return out
    }

    /**
     * The line itself plus runs of neighbouring words joined: OCR often splits an ID at a gap
     * ("1HGCM 82633A 004352", "US-ABC 12345 D404").
     */
    private fun joinedTokens(line: String): List<String> {
        val tokens = line.split(' ', '\t', ':').map { it.trim('.', ',', ';', '#', '*') }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        for (i in tokens.indices) {
            val sb = StringBuilder()
            for (j in i until minOf(tokens.size, i + 5)) {
                sb.append(tokens[j])
                if (sb.length > 24) break
                out += sb.toString()
            }
        }
        return out
    }
}

/**
 * Accepts a candidate once it has been seen often enough across frames: a single frame can misread
 * one character, the same misread twice in a row is much rarer.
 */
class ScanStabilizer(private val verifiedHits: Int = 2, private val unverifiedHits: Int = 4) {
    private val seen = HashMap<String, Int>()

    /** Candidates from one frame; returns the accepted value, if any. */
    fun offer(frame: List<IdScan.Candidate>): String? {
        for (c in frame.distinctBy { it.value }) {
            val n = (seen[c.value] ?: 0) + 1
            seen[c.value] = n
            if (n >= if (c.verified) verifiedHits else unverifiedHits) return c.value
        }
        return null
    }

    fun reset() = seen.clear()
}
