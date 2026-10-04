package dev.maintracker.domain

import dev.maintracker.domain.HinParser.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HinTest {
    private val year = 2026

    @Test fun normalizeDropsSeparatorsAndCountryPrefix() {
        assertEquals("ABC12345D404", HinParser.normalize("US-ABC12345D404"))
        assertEquals("ABC12345D404", HinParser.normalize(" abc-12345 d404 "))
        // Only a 14-character HIN with two leading letters loses them.
        assertEquals("ABC12345D4045", HinParser.normalize("ABC12345D4045"))
    }

    @Test fun modelYearFromPost1984Format() {
        assertEquals(Result.Valid("ABC12345D404", "ABC", 2004), HinParser.parse("ABC12345D404", year))
        assertEquals(1999, HinParser.modelYear("ABC12345L899", year))
        assertEquals(2027, HinParser.modelYear("ABC12345A127", year)) // next model year is fine
        assertNull(HinParser.modelYear("ABC12345A128", year)) // 2028 is past next year, 1928 before 1984
        assertEquals(1984, HinParser.modelYear("ABC12345H484", year))
        assertNull(HinParser.modelYear("ABC12345A483", year)) // 1983: before the format existed
    }

    @Test fun olderFormatsLeaveYearAlone() {
        assertNull((HinParser.parse("ABC12345M82A", year) as Result.Valid).modelYear)
        assertNull((HinParser.parse("ABC123450783", year) as Result.Valid).modelYear)
    }

    @Test fun badFormat() {
        assertEquals("A HIN has 12 characters; this one has 11.", (HinParser.parse("ABC12345D40", year) as Result.BadFormat).reason)
        assertEquals("HINs use only letters and digits.", (HinParser.parse("ABC1234*D404", year) as Result.BadFormat).reason)
    }

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/hin/$name")).readText()
    private val table by lazy { MicTable.load(fixture("mic_fixture.csv"), fixture("mic_overrides_fixture.csv")) }

    @Test fun loaderReadsQuotedRowsSkipsBadOnesAndMergesOverrides() {
        assertEquals("ZZTEST WIDGET BOATS, INC.", table.lookup("ZZA")?.company)
        assertEquals("QUOTED \"NAME\" MFG CO", table.lookup("zze")?.company)
        assertEquals("ZZTEST LOWER CASE MIC LTD", table.lookup("ZZG")?.company)
        assertNull(table.lookup("ZZH")) // no company
        assertNull(table.lookup("TOO"))
        // Overrides win, and can add a MIC the table lacks.
        assertEquals("Hullworks", table.lookup("ZZB")?.brand)
        assertEquals("ZZBrand Override In Table", table.lookup("ZZC")?.brand)
        assertEquals("Override Only", table.lookup("ZZX")?.brand)
        assertEquals(8, table.size) // 7 good rows (ZZH and TOOLONG rejected) + ZZX from the overrides
    }

    @Test fun lookupMissIsNull() {
        assertNull(table.lookup("QQQ"))
    }

    @Test fun companyMatchIgnoresTradeAndLegalEndings() {
        val makes = listOf("Zztest Widget", "Fakeboat", "Zztest Hull Works", "Quoted Name")
        assertEquals("Zztest Widget", MicMake.make(table.lookup("ZZA")!!, makes))     // BOATS, INC.
        assertEquals("Fakeboat", MicMake.make(table.lookup("ZZD")!!, makes))          // YACHTS & CO
        assertEquals("Quoted Name", MicMake.make(table.lookup("ZZE")!!, makes))       // MFG CO
        // A make that itself ends in a trade word still matches the stripped company.
        assertEquals("Fakeboat Yachts", MicMake.make(table.lookup("ZZD")!!, listOf("Fakeboat Yachts")))
    }

    @Test fun neverStripsToNothing() {
        assertEquals("inc", MicMake.matchKey("INC"))
        assertEquals("marine", MicMake.matchKey("MARINE INC"))
        assertEquals("INC", MicMake.stripLegal("INC"))
    }

    @Test fun noMatchKeepsTradeWordsDropsLegalOnes() {
        assertEquals("Zztest Widget Boats", MicMake.make(table.lookup("ZZA")!!, emptyList()))
        assertEquals("Fakeboat Yachts", MicMake.make(table.lookup("ZZD")!!, emptyList()))
        assertEquals("Zztest Lower Case Mic", MicMake.make(table.lookup("ZZG")!!, emptyList()))
        assertEquals("SEA FOX BOAT COMPANY", MicMake.stripLegal("SEA FOX BOAT COMPANY, INC."))
    }

    @Test fun brandWinsOverEverything() {
        assertEquals("Hullworks", MicMake.make(table.lookup("ZZB")!!, listOf("Zztest Hull Works")))
        // ...but takes the make list's spelling when it is the same name.
        assertEquals("HullWorks", MicMake.make(table.lookup("ZZB")!!, listOf("HullWorks")))
    }

    @Test fun vinMatchingIsUnchanged() {
        // Trade-word stripping is for MIC names only.
        assertTrue(VinFill.displayMake("FAKEBOAT YACHTS", listOf("Fakeboat")) != "Fakeboat")
    }

    @Test fun hinFillIsBlankOnlyAndNeverTouchesModel() {
        val e = table.lookup("ZZA")
        assertEquals(VinFill.Fill(2004, "Zztest Widget Boats"), HinFill.fill("", "", 2004, e, emptyList()))
        assertEquals(VinFill.Fill(null, "Zztest Widget Boats"), HinFill.fill("2003", " ", 2004, e, emptyList()))
        assertEquals(VinFill.Fill(2004, null), HinFill.fill("", "Mine", 2004, e, emptyList()))
        // MIC miss: Make left alone, no error.
        assertEquals(VinFill.Fill(null, null), HinFill.fill("", "", null, null, emptyList()))
    }
}

/** The bundled USCG table as shipped (real data from tools/fetch_uscg_mic.py). */
class BundledMicTableTest {
    private fun asset(path: String) = java.io.File("src/main/assets/$path").readText()

    @Test fun loadsEveryRowAndMatchesListedMakes() {
        val table = MicTable.load(asset("hin/uscg_mic.csv"), asset("hin/mic_brand_overrides.csv"))
        assertTrue("rows: ${table.size}", table.size >= 16_000)
        val setup = dev.maintracker.domain.library.SetupFile.decode(asset("library/setup_questions_v1.json"))
        val powerMakes = setup.commonMakes.getValue(dev.maintracker.data.model.VehicleType.BOAT_POWER)
        assertEquals("Four Winns", MicMake.make(table.lookup("4WN")!!, powerMakes)) // FOUR WINNS INC
        assertEquals("Boston Whaler", MicMake.make(table.lookup("BWC")!!, powerMakes))
    }
}
