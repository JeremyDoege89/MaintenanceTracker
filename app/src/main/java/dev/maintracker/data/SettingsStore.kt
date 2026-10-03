package dev.maintracker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.maintracker.domain.DateStyle
import dev.maintracker.domain.DisplayPrefs
import dev.maintracker.domain.DistanceUnit
import dev.maintracker.domain.DueCalculator
import dev.maintracker.domain.ReminderPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val reminders: ReminderPrefs = ReminderPrefs(),
    /** Global due-soon cap in days (Build Guide §6.2: "smaller of 14 days and 10%"). */
    val dueSoonDays: Int = DueCalculator.MAX_LEAD_DAYS,
    /** The in-context notification pre-prompt was answered (either way); never nag again. */
    val notificationPromptAnswered: Boolean = false,
    /** The one-time "reminders may be a little late" explainer has been shown. */
    val exactAlarmExplainerShown: Boolean = false,
    val display: DisplayPrefs = DisplayPrefs(),
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val remindAt = intPreferencesKey("remind_at_minute")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = intPreferencesKey("quiet_start_minute")
        val quietEnd = intPreferencesKey("quiet_end_minute")
        val escalation = booleanPreferencesKey("escalation")
        val dueSoonDays = intPreferencesKey("due_soon_days")
        val promptAnswered = booleanPreferencesKey("notification_prompt_answered")
        val exactExplainer = booleanPreferencesKey("exact_alarm_explainer_shown")
        val distance = stringPreferencesKey("distance_unit")
        val dateStyle = stringPreferencesKey("date_style")
        val currency = stringPreferencesKey("currency")
        val libraryDataVersion = intPreferencesKey("library_data_version")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = flow.first()

    private fun Preferences.toSettings(): AppSettings {
        val d = ReminderPrefs()
        return AppSettings(
            reminders = ReminderPrefs(
                remindAtMinute = this[K.remindAt] ?: d.remindAtMinute,
                quietEnabled = this[K.quietEnabled] ?: d.quietEnabled,
                quietStartMinute = this[K.quietStart] ?: d.quietStartMinute,
                quietEndMinute = this[K.quietEnd] ?: d.quietEndMinute,
                escalation = this[K.escalation] ?: d.escalation,
            ),
            dueSoonDays = this[K.dueSoonDays] ?: DueCalculator.MAX_LEAD_DAYS,
            notificationPromptAnswered = this[K.promptAnswered] ?: false,
            exactAlarmExplainerShown = this[K.exactExplainer] ?: false,
            display = DisplayPrefs(
                distance = this[K.distance]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: DistanceUnit.MILES,
                dateStyle = this[K.dateStyle]?.let { runCatching { DateStyle.valueOf(it) }.getOrNull() } ?: DateStyle.SYSTEM,
                currency = this[K.currency],
            ),
        )
    }

    suspend fun setReminders(r: ReminderPrefs) = context.dataStore.edit {
        it[K.remindAt] = r.remindAtMinute
        it[K.quietEnabled] = r.quietEnabled
        it[K.quietStart] = r.quietStartMinute
        it[K.quietEnd] = r.quietEndMinute
        it[K.escalation] = r.escalation
    }

    suspend fun setDueSoonDays(days: Int) = context.dataStore.edit { it[K.dueSoonDays] = days.coerceIn(1, 60) }
    suspend fun setNotificationPromptAnswered() = context.dataStore.edit { it[K.promptAnswered] = true }
    suspend fun setDisplay(d: DisplayPrefs) = context.dataStore.edit {
        it[K.distance] = d.distance.name
        it[K.dateStyle] = d.dateStyle.name
        if (d.currency == null) it.remove(K.currency) else it[K.currency] = d.currency
    }

    /** dataVersion of the maintenance library last applied to the database (0 = never). */
    suspend fun libraryDataVersion(): Int = context.dataStore.data.first()[K.libraryDataVersion] ?: 0
    suspend fun setLibraryDataVersion(v: Int) = context.dataStore.edit { it[K.libraryDataVersion] = v }

    suspend fun setExactAlarmExplainerShown() = context.dataStore.edit { it[K.exactExplainer] = true }
}
