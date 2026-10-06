package dev.dreamz.weekendfleet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.dreamz.weekendfleet.billing.FakeBillingGateway
import kotlinx.coroutines.launch

/**
 * Debug-only hook for the M4 billing acceptance walk-through (Build Guide §10, §11):
 *
 *   adb shell am broadcast -n dev.dreamz.weekendfleet/.DebugBillingReceiver -a next --es outcome PENDING
 *   adb shell am broadcast -n dev.dreamz.weekendfleet/.DebugBillingReceiver -a complete_pending
 *   adb shell am broadcast -n dev.dreamz.weekendfleet/.DebugBillingReceiver -a refund
 *   adb shell am broadcast -n dev.dreamz.weekendfleet/.DebugBillingReceiver -a offline --ez on true
 *   adb shell am broadcast -n dev.dreamz.weekendfleet/.DebugBillingReceiver -a refresh
 *   adb shell am broadcast -n dev.dreamz.weekendfleet/.DebugBillingReceiver -a state
 *
 * Every action logs the resulting state under tag DebugBilling.
 */
class DebugBillingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MaintTrackerApp
        val fake = app.billing as? FakeBillingGateway
        val pending = goAsync()
        app.appScope.launch {
            try {
                when (intent.action) {
                    "next" -> fake?.nextOutcome = FakeBillingGateway.Outcome.valueOf(intent.getStringExtra("outcome")!!)
                    "complete_pending" -> fake?.completePending()
                    "refund" -> fake?.refund()
                    "offline" -> fake?.offline = intent.getBooleanExtra("on", true)
                    "refresh" -> Log.i(TAG, "refresh reached Play: ${app.pro.refresh()}")
                }
                Log.i(TAG, "pro=${app.pro.status.value} fake=${fake?.describe() ?: "(real Play)"}")
            } catch (e: Exception) {
                Log.e(TAG, "failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DebugBilling"
    }
}
