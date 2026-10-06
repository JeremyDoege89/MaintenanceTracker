package dev.dreamz.weekendfleet.data.vin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpicParserTest {
    private fun fixture(name: String) =
        checkNotNull(javaClass.getResource("/vpic/$name")) { "missing fixture $name" }.readText()

    @Test fun cleanDecode() {
        val d = VpicParser.parse(fixture("clean_accord.json")) as VpicDecode.Decoded
        assertEquals("HONDA", d.make)
        assertEquals("Accord", d.model)
        assertEquals(2003, d.modelYear)
        assertEquals("PASSENGER CAR", d.vehicleType)
        assertEquals("0", d.errorCode)
        // rawJson is Results[0], intact.
        val raw = Json.parseToJsonElement(d.rawJson).jsonObject
        assertEquals("1HGCM82633A004352", raw["VIN"]?.jsonPrimitive?.content)
    }

    @Test fun partialDecodeKeepsDataDespiteErrorCode() {
        val d = VpicParser.parse(fixture("partial_code6.json")) as VpicDecode.Decoded
        assertEquals(Triple("BMW", "X3", 2011), Triple(d.make, d.model, d.modelYear))
        assertEquals("6", d.errorCode)
    }

    @Test fun garbageIsEmpty() {
        assertEquals(VpicDecode.Empty, VpicParser.parse(fixture("empty_garbage.json")))
    }

    @Test fun blanksNotApplicableAndNullsCountAsMissing() {
        val d = VpicParser.parse(
            """{"Results":[{"Make":"FORD","Model":"Not Applicable","ModelYear":null,"VehicleType":"","ErrorCode":"8"}]}""",
        ) as VpicDecode.Decoded
        assertNull(d.model)
        assertNull(d.modelYear)
        assertNull(d.vehicleType)
        assertEquals(VpicDecode.Empty, VpicParser.parse("""{"Results":[{"Make":"Not Applicable"}]}"""))
        assertEquals(VpicDecode.Empty, VpicParser.parse("""{"Results":[]}"""))
        assertEquals(VpicDecode.Empty, VpicParser.parse("""{"Count":0}"""))
        // A year that isn't a number is ignored, not fatal.
        assertNull((VpicParser.parse("""{"Results":[{"Make":"X","ModelYear":"20XX"}]}""") as VpicDecode.Decoded).modelYear)
    }

    @Test fun unreadableBodyThrows() {
        for (body in listOf("<html>503</html>", "", "[1,2]")) {
            val thrown = runCatching { VpicParser.parse(body) }.exceptionOrNull()
            assertTrue(body, thrown is VpicBodyException)
        }
    }
}
