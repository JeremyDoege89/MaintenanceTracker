package dev.dreamz.weekendfleet.data.vin

import dev.dreamz.weekendfleet.data.db.VinDecodeCacheDao
import dev.dreamz.weekendfleet.data.model.VinDecodeCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Cache first, then one vPIC request (retried once on a timeout or 5xx). Caches Decoded results only. */
class VinDecoder(
    private val cache: VinDecodeCacheDao,
    private val remote: VpicRemote,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Failure { OFFLINE, TIMEOUT, SERVER, EMPTY }

    sealed interface Outcome {
        data class Decoded(val decode: VpicDecode.Decoded, val fromCache: Boolean) : Outcome
        data class Failed(val failure: Failure) : Outcome
    }

    /** [vin] must already be normalized (VinValidator). [modelYear] is sent only when the user typed one. */
    suspend fun decode(vin: String, modelYear: Int?): Outcome = withContext(io) {
        cache.get(vin)?.let { return@withContext Outcome.Decoded(it.toDecode(), fromCache = true) }
        when (val r = fetchWithRetry(vin, modelYear)) {
            is Outcome.Failed -> r
            is Outcome.Decoded -> r.also { cache.put(it.decode.toRow(vin)) }
        }
    }

    private suspend fun fetchWithRetry(vin: String, modelYear: Int?): Outcome {
        var attempt = 0
        while (true) {
            attempt++
            val failure = try {
                return when (val d = VpicParser.parse(remote.fetch(vin, modelYear))) {
                    is VpicDecode.Decoded -> Outcome.Decoded(d, fromCache = false)
                    VpicDecode.Empty -> Outcome.Failed(Failure.EMPTY)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnknownHostException) {
                return Outcome.Failed(Failure.OFFLINE)
            } catch (e: ConnectException) {
                return Outcome.Failed(Failure.OFFLINE)
            } catch (e: SocketTimeoutException) {
                Failure.TIMEOUT
            } catch (e: VpicHttpException) {
                if (e.status in 500..599) Failure.SERVER else return Outcome.Failed(Failure.SERVER)
            } catch (e: IOException) {
                // Unreadable body, TLS trouble, connection reset: not worth a retry.
                return Outcome.Failed(Failure.SERVER)
            }
            if (attempt >= 2) return Outcome.Failed(failure)
        }
    }

    private fun VinDecodeCache.toDecode() =
        VpicDecode.Decoded(make, model, modelYear, vehicleType, errorCode, rawJson)

    private fun VpicDecode.Decoded.toRow(vin: String) =
        VinDecodeCache(vin, make, model, modelYear, vehicleType, errorCode, rawJson, fetchedAt = now())
}
