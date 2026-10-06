package dev.dreamz.weekendfleet.domain

import dev.dreamz.weekendfleet.domain.IdScan.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdScanTest {
    private val accord = "1HGCM82633A004352"

    @Test fun barcodeWithImportPrefixAndFiller() {
        assertEquals(accord, IdScan.vinFromBarcode(accord))
        assertEquals(accord, IdScan.vinFromBarcode("I$accord"))
        assertEquals(accord, IdScan.vinFromBarcode(" ${accord}00 "))
        // Exact 17 with a bad check digit is still taken from a barcode (non-NA VINs).
        assertEquals("WVWZZZ1JZXW000001", IdScan.vinFromBarcode("WVWZZZ1JZXW000001"))
        assertNull(IdScan.vinFromBarcode("12345"))
        assertNull(IdScan.vinFromBarcode("https://example.com/a-long-url-here"))
    }

    @Test fun ocrFixesLookalikesAndJoinsSplits() {
        assertEquals(listOf(Candidate(accord, true)), IdScan.vinCandidates("1HGCM8Z633AOO4352".replace('Z', '2')))
        assertEquals(listOf(Candidate(accord, true)), IdScan.vinCandidates("1HGCM 82633A 004352"))
        assertTrue(Candidate(accord, true) in IdScan.vinCandidates("VIN: $accord MADE IN USA"))
        assertTrue(Candidate(accord, true) in IdScan.vinCandidates("VIN$accord"))
    }

    @Test fun uncheckedOnlyAsWholeRun() {
        // Bad check digit: only accepted as an exact token, never as a window of a longer run.
        assertEquals(listOf(Candidate("WVWZZZ1JZXW000001", false)), IdScan.vinCandidates("WVWZZZ1JZXW000001"))
        assertEquals(emptyList<Candidate>(), IdScan.vinCandidates("XWVWZZZ1JZXW000001"))
        assertEquals(emptyList<Candidate>(), IdScan.vinCandidates("GROSS VEHICLE WEIGHT RATING 4000 LB"))
    }

    private val known = setOf("BRP", "4WN", "OMC")

    @Test fun hinWithCountryPrefixAndKnownMic() {
        val c = IdScan.hinCandidates("US-BRP12345D404", 2026, known::contains)
        assertEquals(listOf(Candidate("BRP12345D404", true)), c)
        assertEquals(listOf(Candidate("BRP12345D404", true)), IdScan.hinCandidates("BRP 12345 D404", 2026, known::contains))
    }

    @Test fun hinMicTakesTheKnownReading() {
        // OCR read the letter O of OMC as a zero, and the serial's O as a zero is fixed too.
        assertEquals(listOf(Candidate("OMC1234OA898".replace("4O", "40"), true)),
            IdScan.hinCandidates("0MC12340A898", 2026, known::contains))
        assertEquals("4WN", IdScan.hinCandidates("4WNI2345D404", 2026, known::contains).single().value.take(3))
        assertEquals("4WN12345D404", IdScan.hinCandidates("4WNI2345D404", 2026, known::contains).single().value)
    }

    @Test fun hinNeedsMicOrDate() {
        assertEquals(listOf(Candidate("XYZ12345D404", false)), IdScan.hinCandidates("XYZ12345D404", 2026, known::contains))
        assertEquals(listOf(Candidate("BRP12345ZZ99", false)), IdScan.hinCandidates("BRP12345ZZ99", 2026, known::contains))
        assertEquals(emptyList<Candidate>(), IdScan.hinCandidates("ABCDEFGHJKLM", 2026, known::contains))
    }

    @Test fun stabilizerNeedsRepeats() {
        val s = ScanStabilizer()
        assertNull(s.offer(listOf(Candidate(accord, true))))
        assertEquals(accord, s.offer(listOf(Candidate(accord, true))))
        s.reset()
        val weak = Candidate("WVWZZZ1JZXW000001", false)
        repeat(3) { assertNull(s.offer(listOf(weak, weak))) }
        assertEquals(weak.value, s.offer(listOf(weak)))
    }
}
