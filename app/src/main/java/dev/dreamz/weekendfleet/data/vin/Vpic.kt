package dev.dreamz.weekendfleet.data.vin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One NHTSA vPIC DecodeVinValues answer, reduced to what the app reads. */
sealed interface VpicDecode {
    /** Make came back. Model and year are often blank (trailers, older bikes, anything pre-1995). */
    data class Decoded(
        val make: String,
        val model: String?,
        val modelYear: Int?,
        val vehicleType: String?,
        /** Comma-separated codes such as "6"; partial decodes carry non-zero codes with good data. */
        val errorCode: String?,
        /** Results[0] verbatim, kept so engine, fuel, trim etc. can be used later without a refetch. */
        val rawJson: String,
    ) : VpicDecode

    /** No make: the VIN could not be decoded. */
    data object Empty : VpicDecode
}

/** HTTP status other than 200. */
class VpicHttpException(val status: Int) : IOException("vPIC HTTP $status")

/** The body was not the JSON vPIC sends. */
class VpicBodyException(message: String, cause: Throwable? = null) : IOException(message, cause)

object VpicParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Throws [VpicBodyException] when [body] isn't a vPIC JSON object. */
    fun parse(body: String): VpicDecode {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: IllegalArgumentException) {
            throw VpicBodyException("vPIC body is not JSON", e)
        } ?: throw VpicBodyException("vPIC body is not a JSON object")
        val first = (root["Results"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return VpicDecode.Empty
        fun field(name: String): String? = (first[name] as? JsonPrimitive)
            ?.takeIf { it.isString }?.content?.trim()
            ?.takeUnless { it.isEmpty() || it.equals("Not Applicable", ignoreCase = true) }
        val make = field("Make") ?: return VpicDecode.Empty
        return VpicDecode.Decoded(
            make = make,
            model = field("Model"),
            modelYear = field("ModelYear")?.toIntOrNull(),
            vehicleType = field("VehicleType"),
            errorCode = field("ErrorCode"),
            rawJson = first.toString(),
        )
    }
}

/** The network side of a decode, so tests can swap it out. Returns the response body. */
fun interface VpicRemote {
    suspend fun fetch(vin: String, modelYear: Int?): String
}

/** Blocking GET; call it on an IO dispatcher. One request per call, no retries (those live in [VinDecoder]). */
class HttpVpicRemote(
    private val baseUrl: String = "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/",
    private val timeoutMs: Int = 10_000,
) : VpicRemote {
    override suspend fun fetch(vin: String, modelYear: Int?): String {
        val query = "?format=json" + (modelYear?.let { "&modelyear=$it" } ?: "")
        val url = URL(baseUrl + URLEncoder.encode(vin, "UTF-8") + query)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            val status = conn.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw VpicHttpException(status)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
