package dev.dreamz.weekendfleet.domain

import dev.dreamz.weekendfleet.data.model.VehicleType

/**
 * Turns a VIN decode into form values. Only blank fields are filled; anything the user typed
 * stays. Motorhome VINs describe the chassis (e.g. a Ford E-450), not the coach, so nothing goes
 * into the coach's year / make / model; the chassis is shown as text instead.
 */
object VinFill {

    /** Values to put into fields that were blank; null = leave the field alone. */
    data class Fill(
        val year: Int? = null,
        val make: String? = null,
        val model: String? = null,
        /** Motorhomes only, e.g. "2019 Ford E-450". */
        val chassis: String? = null,
    ) {
        val filled: List<String> get() = listOfNotNull(year?.toString(), make, model)
    }

    fun fill(
        type: VehicleType,
        year: String, make: String, model: String,
        decodedMake: String, decodedModel: String?, decodedYear: Int?,
        knownMakes: List<String>,
    ): Fill {
        val decodedModelText = decodedModel?.trim()?.takeIf { it.isNotEmpty() }
        if (type == VehicleType.MOTORHOME) {
            val chassis = listOfNotNull(decodedYear?.toString(), displayMake(decodedMake, emptyList()), decodedModelText)
            return Fill(chassis = chassis.joinToString(" "))
        }
        return Fill(
            year = decodedYear.takeIf { year.isBlank() },
            make = displayMake(decodedMake, knownMakes).takeIf { make.isBlank() },
            model = decodedModelText.takeIf { model.isBlank() },
        )
    }

    /** The listed spelling when the decoded make matches one (ignoring case and punctuation), else title case. */
    fun displayMake(decoded: String, knownMakes: List<String>): String {
        val key = matchKey(decoded)
        return knownMakes.firstOrNull { matchKey(it) == key } ?: titleCase(decoded.trim())
    }

    /** Lowercase letters and digits only: "HARLEY-DAVIDSON" and "Harley Davidson" both give "harleydavidson". */
    fun matchKey(s: String): String = s.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    /** "BIG TEX" -> "Big Tex", "HARLEY-DAVIDSON" -> "Harley-Davidson"; vowel-less words stay upper case (KTM, BMW, GMC). */
    fun titleCase(s: String): String = s.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word ->
        if (word.none { it.lowercaseChar() in "aeiou" }) word.uppercase()
        else word.lowercase().split('-').joinToString("-") { part -> part.replaceFirstChar(Char::uppercaseChar) }
    }
}
