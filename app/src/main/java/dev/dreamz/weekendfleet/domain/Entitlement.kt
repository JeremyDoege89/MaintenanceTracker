package dev.dreamz.weekendfleet.domain

/** Play product ids (Build Guide §10). Plus ids ship as ghosts — not created in Play Console until M6+. */
object Products {
    const val PRO_LIFETIME = "mt_pro_lifetime"
    const val PLUS_MONTHLY = "mt_plus_monthly"
    const val PLUS_YEARLY = "mt_plus_yearly"
}

enum class ProState { FREE, PENDING, PRO }

enum class PurchaseState { PURCHASED, PENDING, UNKNOWN }

/** A billing-library-free view of one purchase, so the rules below stay unit-testable. */
data class OwnedPurchase(
    val productIds: List<String>,
    val token: String,
    val state: PurchaseState,
    val acknowledged: Boolean,
)

/** What the paywall was opened for; drives its headline. Null request = opened from Settings. */
enum class ProFeature { SECOND_VEHICLE, ATTACHMENTS, EXPORT }

object Entitlements {
    /** Free tier covers one active vehicle completely (Build Guide §2). Archived ones don't count. */
    const val FREE_VEHICLES = 1

    fun canAddVehicle(state: ProState, activeVehicles: Int): Boolean =
        state == ProState.PRO || activeVehicles < FREE_VEHICLES

    private fun OwnedPurchase.isPro() = Products.PRO_LIFETIME in productIds

    /**
     * Pro only after PURCHASED + acknowledged (§10). A purchase that is paid but not yet
     * acknowledged, or still PENDING at the payment provider, shows as "payment pending".
     */
    fun resolve(purchases: List<OwnedPurchase>): ProState {
        val pro = purchases.filter { it.isPro() }
        return when {
            pro.any { it.state == PurchaseState.PURCHASED && it.acknowledged } -> ProState.PRO
            pro.any { it.state == PurchaseState.PENDING || it.state == PurchaseState.PURCHASED } -> ProState.PENDING
            else -> ProState.FREE
        }
    }

    /** Purchases this app must acknowledge itself — never left to Play Console (§10). */
    fun needingAcknowledgement(purchases: List<OwnedPurchase>): List<OwnedPurchase> =
        purchases.filter { it.isPro() && it.state == PurchaseState.PURCHASED && !it.acknowledged }

    /** Same token, now acknowledged: lets [resolve] grant Pro without a second round trip. */
    fun markAcknowledged(purchases: List<OwnedPurchase>, tokens: Set<String>): List<OwnedPurchase> =
        purchases.map { if (it.token in tokens) it.copy(acknowledged = true) else it }
}
