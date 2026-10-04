package dev.maintracker.domain

/**
 * Checks a 17-character VIN before it is sent to the decoder. Validation gates the Decode
 * button only: saving a vehicle accepts any VIN text, including pre-1981 VINs shorter than 17.
 */
object VinValidator {

    sealed interface Result {
        val vin: String

        data class Valid(override val vin: String) : Result

        /** Not decodable; [reason] is shown under the field and no request is made. */
        data class BadFormat(override val vin: String, val reason: String) : Result

        /** A warning only: VINs from outside North America often skip the check digit. */
        data class BadCheckDigit(override val vin: String) : Result
    }

    const val LENGTH = 17

    private val FORMAT = Regex("^[A-HJ-NPR-Z0-9]{17}$")
    private val WEIGHTS = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)

    /** Uppercase with all whitespace removed. */
    fun normalize(raw: String): String = raw.filterNot(Char::isWhitespace).uppercase()

    fun validate(raw: String): Result {
        val vin = normalize(raw)
        if (vin.length != LENGTH) {
            return Result.BadFormat(vin, "A VIN has $LENGTH characters; this one has ${vin.length}.")
        }
        if (!FORMAT.matches(vin)) {
            val reason = if (vin.any { it in "IOQ" }) "VINs never use I, O or Q." else "VINs use only letters and digits."
            return Result.BadFormat(vin, reason)
        }
        return if (vin[8] == checkDigit(vin)) Result.Valid(vin) else Result.BadCheckDigit(vin)
    }

    /** The expected 9th character of a well-formed [vin]: weighted sum mod 11, 10 written as X. */
    fun checkDigit(vin: String): Char {
        val sum = vin.withIndex().sumOf { (i, c) -> value(c) * WEIGHTS[i] }
        val r = sum % 11
        return if (r == 10) 'X' else '0' + r
    }

    private fun value(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'H' -> c - 'A' + 1
        in 'J'..'N' -> c - 'J' + 1
        'P' -> 7
        'R' -> 9
        in 'S'..'Z' -> c - 'S' + 2
        else -> 0
    }
}
