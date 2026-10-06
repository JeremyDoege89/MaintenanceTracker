package dev.dreamz.weekendfleet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {
    private fun pro(state: PurchaseState, acked: Boolean, token: String = "t") =
        OwnedPurchase(listOf(Products.PRO_LIFETIME), token, state, acked)

    @Test fun `no purchases is free`() = assertEquals(ProState.FREE, Entitlements.resolve(emptyList()))

    @Test fun `purchased and acknowledged is pro`() =
        assertEquals(ProState.PRO, Entitlements.resolve(listOf(pro(PurchaseState.PURCHASED, acked = true))))

    @Test fun `purchased but unacknowledged is not pro yet`() =
        assertEquals(ProState.PENDING, Entitlements.resolve(listOf(pro(PurchaseState.PURCHASED, acked = false))))

    @Test fun `pending payment is pending`() =
        assertEquals(ProState.PENDING, Entitlements.resolve(listOf(pro(PurchaseState.PENDING, acked = false))))

    @Test fun `unknown state grants nothing`() =
        assertEquals(ProState.FREE, Entitlements.resolve(listOf(pro(PurchaseState.UNKNOWN, acked = false))))

    @Test fun `other products never grant pro`() {
        val other = OwnedPurchase(listOf("something_else"), "x", PurchaseState.PURCHASED, acknowledged = true)
        assertEquals(ProState.FREE, Entitlements.resolve(listOf(other)))
        assertTrue(Entitlements.needingAcknowledgement(listOf(other)).isEmpty())
    }

    @Test fun `only purchased unacknowledged pro needs acknowledging`() {
        val list = listOf(
            pro(PurchaseState.PURCHASED, acked = false, token = "a"),
            pro(PurchaseState.PURCHASED, acked = true, token = "b"),
            pro(PurchaseState.PENDING, acked = false, token = "c"),
        )
        assertEquals(listOf("a"), Entitlements.needingAcknowledgement(list).map { it.token })
    }

    @Test fun `marking acknowledged grants pro`() {
        val list = Entitlements.markAcknowledged(listOf(pro(PurchaseState.PURCHASED, acked = false, token = "a")), setOf("a"))
        assertEquals(ProState.PRO, Entitlements.resolve(list))
    }

    @Test fun `free tier allows exactly one active vehicle`() {
        assertTrue(Entitlements.canAddVehicle(ProState.FREE, activeVehicles = 0))
        assertFalse(Entitlements.canAddVehicle(ProState.FREE, activeVehicles = 1))
        assertFalse(Entitlements.canAddVehicle(ProState.PENDING, activeVehicles = 1))
        assertTrue(Entitlements.canAddVehicle(ProState.PRO, activeVehicles = 5))
    }
}
