package dev.dreamz.weekendfleet.data.vin

import dev.dreamz.weekendfleet.data.db.VinDecodeCacheDao
import dev.dreamz.weekendfleet.data.model.VinDecodeCache
import dev.dreamz.weekendfleet.data.vin.VinDecoder.Failure
import dev.dreamz.weekendfleet.data.vin.VinDecoder.Outcome
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class VinDecoderTest {
    private val accord = "1HGCM82633A004352"
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/vpic/$name")).readText()

    private class FakeCache : VinDecodeCacheDao {
        val rows = mutableMapOf<String, VinDecodeCache>()
        override suspend fun get(vin: String) = rows[vin]
        override suspend fun put(row: VinDecodeCache) { rows[row.vin] = row }
        override suspend fun clear() = rows.clear()
    }

    /** Answers each call with the next scripted body, or throws the next scripted exception. */
    private class ScriptedRemote(vararg steps: Any) : VpicRemote {
        private val steps = ArrayDeque(steps.toList())
        val calls = mutableListOf<Pair<String, Int?>>()
        override suspend fun fetch(vin: String, modelYear: Int?): String {
            calls += vin to modelYear
            return when (val s = steps.removeFirst()) {
                is Throwable -> throw s
                else -> s as String
            }
        }
    }

    private fun TestScope.decoder(cache: FakeCache, remote: VpicRemote) =
        VinDecoder(cache, remote, io = StandardTestDispatcher(testScheduler), now = { 42L })

    @Test fun decodedIsCachedAndACacheHitMakesNoNetworkCall() = runTest {
        val cache = FakeCache()
        val remote = ScriptedRemote(fixture("clean_accord.json"))
        val first = decoder(cache, remote).decode(accord, 2003) as Outcome.Decoded
        assertEquals(false, first.fromCache)
        assertEquals(listOf(accord to 2003), remote.calls)
        assertEquals(42L, cache.rows.getValue(accord).fetchedAt)

        val noNetwork = ScriptedRemote() // any call would throw NoSuchElementException
        val second = decoder(cache, noNetwork).decode(accord, null) as Outcome.Decoded
        assertTrue(second.fromCache)
        assertEquals(first.decode, second.decode)
        assertTrue(noNetwork.calls.isEmpty())
    }

    @Test fun timeoutRetriesOnceThenFails() = runTest {
        val remote = ScriptedRemote(SocketTimeoutException(), SocketTimeoutException())
        assertEquals(Outcome.Failed(Failure.TIMEOUT), decoder(FakeCache(), remote).decode(accord, null))
        assertEquals(2, remote.calls.size)
    }

    @Test fun timeoutThenSuccessDecodes() = runTest {
        val remote = ScriptedRemote(SocketTimeoutException(), fixture("clean_accord.json"))
        assertTrue(decoder(FakeCache(), remote).decode(accord, null) is Outcome.Decoded)
    }

    @Test fun emptyIsNeitherRetriedNorCached() = runTest {
        val cache = FakeCache()
        val remote = ScriptedRemote(fixture("empty_garbage.json"))
        assertEquals(Outcome.Failed(Failure.EMPTY), decoder(cache, remote).decode("00000000000000000", null))
        assertEquals(1, remote.calls.size)
        assertTrue(cache.rows.isEmpty())
    }

    @Test fun offlineIsNotRetried() = runTest {
        for (e in listOf(UnknownHostException(), ConnectException())) {
            val remote = ScriptedRemote(e)
            assertEquals(Outcome.Failed(Failure.OFFLINE), decoder(FakeCache(), remote).decode(accord, null))
            assertEquals(1, remote.calls.size)
        }
    }

    @Test fun serverErrorsRetryOnlyOn5xx() = runTest {
        val r5 = ScriptedRemote(VpicHttpException(503), VpicHttpException(502))
        assertEquals(Outcome.Failed(Failure.SERVER), decoder(FakeCache(), r5).decode(accord, null))
        assertEquals(2, r5.calls.size)

        val r4 = ScriptedRemote(VpicHttpException(404))
        assertEquals(Outcome.Failed(Failure.SERVER), decoder(FakeCache(), r4).decode(accord, null))
        assertEquals(1, r4.calls.size)

        val junk = ScriptedRemote("<html>oops</html>")
        assertEquals(Outcome.Failed(Failure.SERVER), decoder(FakeCache(), junk).decode(accord, null))
        assertEquals(1, junk.calls.size)
    }
}
