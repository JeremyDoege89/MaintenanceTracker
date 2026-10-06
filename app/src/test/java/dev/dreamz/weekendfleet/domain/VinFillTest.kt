package dev.dreamz.weekendfleet.domain

import dev.dreamz.weekendfleet.data.model.VehicleType
import dev.dreamz.weekendfleet.domain.VinFill.Fill
import org.junit.Assert.assertEquals
import org.junit.Test

class VinFillTest {
    private val bikeMakes = listOf("BMW", "Harley-Davidson", "Honda", "KTM")

    private fun fill(
        type: VehicleType = VehicleType.MOTORCYCLE, year: String = "", make: String = "", model: String = "",
        decodedMake: String = "HONDA", decodedModel: String? = "Gold Wing", decodedYear: Int? = 2018,
    ) = VinFill.fill(type, year, make, model, decodedMake, decodedModel, decodedYear, bikeMakes)

    @Test fun fillsBlankFieldsOnly() {
        assertEquals(Fill(2018, "Honda", "Gold Wing"), fill())
        assertEquals(Fill(null, null, "Gold Wing"), fill(year = "2017", make = "Hondda"))
        assertEquals(Fill(), fill(year = "2017", make = "Honda", model = "GL1800"))
        // Whitespace counts as blank.
        assertEquals(Fill(2018, "Honda", "Gold Wing"), fill(year = " ", make = "  ", model = "\t"))
    }

    @Test fun blankDecodedValuesFillNothing() {
        assertEquals(Fill(null, "Honda", null), fill(decodedModel = " ", decodedYear = null))
        assertEquals(listOf("Honda"), fill(decodedModel = null, decodedYear = null).filled)
    }

    @Test fun makeMatchIgnoresCaseAndPunctuation() {
        assertEquals("Harley-Davidson", VinFill.displayMake("HARLEY DAVIDSON", bikeMakes))
        assertEquals("Harley-Davidson", VinFill.displayMake("harley-davidson", bikeMakes))
        assertEquals("BMW", VinFill.displayMake("BMW", bikeMakes))
    }

    @Test fun unmatchedMakeBecomesTitleCase() {
        assertEquals("Big Tex", VinFill.displayMake("BIG TEX", bikeMakes))
        assertEquals("GMC", VinFill.displayMake("GMC", emptyList()))
        assertEquals("Mercedes-Benz", VinFill.displayMake("MERCEDES-BENZ", emptyList()))
        assertEquals("Forest River", VinFill.displayMake(" FOREST  RIVER ", emptyList()))
    }

    @Test fun motorhomeLeavesCoachFieldsAlone() {
        val f = fill(type = VehicleType.MOTORHOME, decodedMake = "FORD", decodedModel = "E-450", decodedYear = 2019)
        assertEquals(Fill(chassis = "2019 Ford E-450"), f)
        assertEquals(emptyList<String>(), f.filled)
        assertEquals("Ford", fill(type = VehicleType.MOTORHOME, decodedMake = "FORD", decodedModel = null, decodedYear = null).chassis)
    }
}
