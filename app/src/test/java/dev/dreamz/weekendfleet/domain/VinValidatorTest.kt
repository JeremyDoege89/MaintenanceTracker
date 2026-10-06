package dev.dreamz.weekendfleet.domain

import dev.dreamz.weekendfleet.domain.VinValidator.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VinValidatorTest {
    private val accord = "1HGCM82633A004352"

    @Test fun checkDigitValid() {
        assertEquals(Result.Valid(accord), VinValidator.validate(accord))
        assertEquals('3', VinValidator.checkDigit(accord))
        assertEquals(Result.Valid("11111111111111111"), VinValidator.validate("11111111111111111"))
        assertEquals('X', VinValidator.checkDigit("1M8GDM9AXKP042788"))
        assertEquals(Result.Valid("1M8GDM9AXKP042788"), VinValidator.validate("1M8GDM9AXKP042788"))
    }

    @Test fun checkDigitInvalidIsAWarningOnly() {
        assertEquals(Result.BadCheckDigit("1HGCM82633A004353"), VinValidator.validate("1HGCM82633A004353"))
    }

    @Test fun badFormat() {
        fun reason(vin: String) = (VinValidator.validate(vin) as Result.BadFormat).reason
        assertEquals("A VIN has 17 characters; this one has 16.", reason(accord.dropLast(1)))
        assertEquals("A VIN has 17 characters; this one has 18.", reason(accord + "1"))
        for (c in listOf('I', 'O', 'Q')) {
            assertEquals("VINs never use I, O or Q.", reason(accord.replaceRange(5, 6, c.toString())))
        }
        assertTrue(VinValidator.validate("5UXWX7C5*BA") is Result.BadFormat)
        assertEquals("VINs use only letters and digits.", reason(accord.replaceRange(5, 6, "*")))
        assertTrue(VinValidator.validate("") is Result.BadFormat)
    }

    @Test fun lowercaseWithSpacesNormalizes() {
        assertEquals(Result.Valid(accord), VinValidator.validate(" 1hgcm 82633a\t004352 "))
    }
}
