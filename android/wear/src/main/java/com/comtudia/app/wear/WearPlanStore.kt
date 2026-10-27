package com.comtudia.app.wear

import android.content.Context
import com.comtudia.app.core.Plan
import com.comtudia.app.core.PlanJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watch-side copy of the plan, written by [WearSyncService] when the phone
 * pushes a new document and read by the UI.
 *
 * A process-wide singleton keeps the activity and the listener service in sync;
 * they share one process, so the flow updates while the app is open.
 */
class WearPlanStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _plan = MutableStateFlow(load())

    val plan: StateFlow<Plan> = _plan.asStateFlow()

    /** Stores a desktop-format plan document. Returns false if it is invalid. */
    fun saveJson(text: String): Boolean = try {
        val decoded = PlanJson.decode(text)
        prefs.edit().putString(KEY_PLAN, text).apply()
        _plan.value = decoded
        true
    } catch (e: Exception) {
        false
    }

    private fun load(): Plan {
        val text = prefs.getString(KEY_PLAN, null) ?: return Plan.default()
        return runCatching { PlanJson.decode(text) }.getOrElse { Plan.default() }
    }

    companion object {
        private const val PREFS_NAME = "comtudia_watch"
        private const val KEY_PLAN = "plan_json"

        @Volatile
        private var instance: WearPlanStore? = null

        fun get(context: Context): WearPlanStore = instance ?: synchronized(this) {
            instance ?: WearPlanStore(context.applicationContext).also { instance = it }
        }
    }
}
